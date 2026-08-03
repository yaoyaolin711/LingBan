package me.rerere.rikkahub.data.agent

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import me.rerere.rikkahub.data.accessibility.AgentEvent
import me.rerere.rikkahub.data.accessibility.AgentEventBus
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.accessibility.UnifiedObservation
import me.rerere.rikkahub.data.agent.memory.MemoryManager
import me.rerere.rikkahub.data.agent.trace.AgentTrace
import me.rerere.rikkahub.data.agent.trace.AgentTracer
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Task lifecycle: Perceive → Plan → Act → Verify.
 *
 * Performance:
 * - All observation goes through [ObservationCollector] (L0/L1/L2)
 * - Skips full UI tree when local rules can plan (e.g. 打开微信)
 * - Verify reuses collectAfterAction — no direct Tiered/perceiveLight
 * - Planning via [TaskPlanner]/[LightweightTaskPlanner] off Main
 */
class AgentRuntime(
    private val planner: AgentPlanner,
    private val executor: AgentActionExecutor,
    private val verifier: ActionVerifier,
    private val eventBus: AgentEventBus,
    private val appScope: CoroutineScope,
    private val scheduler: ActionScheduler = ActionScheduler(
        executor = executor,
        parentScope = appScope,
        workerDispatcher = Dispatchers.Default,
    ),
    private val memory: MemoryManager? = null,
    private val tracer: AgentTracer? = AgentTracer.instance,
    private val runtimeEventBus: AgentRuntimeEventBus? = null,
    private val stateManager: AgentStateManager? = null,
    private val observationCollector: ObservationCollector? = null,
) {
    /** Set by AgentTaskQueue so phase/action events can update Chat/TaskBall. */
    @Volatile
    var eventConversationId: String? = null
        set(value) {
            field = value
            stateManager?.conversationId = value
        }
    companion object {
        private const val TAG = "AgentRuntime"
        const val DEFAULT_MAX_STEPS = 32
        private const val RETRY_DELAY_MS = 350L
    }

    private val mutex = Mutex()
    private val taskPlanner: TaskPlanner? = planner as? TaskPlanner
    private val lightPlanner: LightweightTaskPlanner? = planner as? LightweightTaskPlanner

    /** Set by [cancel]; cleared in [startTask]. Prevents tick from overwriting cancel with SUCCESS. */
    private val cancelRequested = AtomicBoolean(false)

    private val _taskState = MutableStateFlow<TaskState?>(null)
    val taskState: StateFlow<TaskState?> = _taskState.asStateFlow()

    /** Remaining actions from last multi-step plan — avoids re-plan / re-scan. */
    private val pendingActions = ArrayDeque<AgentAction>()

    /**
     * PlanStep metadata aligned with [pendingActions] (same order / size when plan
     * was consumed from [ActionPlan.steps]). Kept separate so pending queue type stays
     * [AgentAction] for Stage9.1 compatibility.
     */
    private val pendingPlanSteps = ArrayDeque<PlanStep>()

    /** Step currently being executed (status: PENDING → RUNNING → DONE/FAILED). */
    private var activePlanStep: PlanStep? = null

    /**
     * In-tick UnifiedObservation cache for Planner when a full tree was available.
     *
     * TODO(Stage8.2+): migrate planning to ObservationResult.snapshot →
     * UnifiedObservation (and/or AgentState.currentObservation); then remove this field.
     * Do not delete yet — still used on the perceive → plan path.
     */
    private var cachedObservation: UnifiedObservation? = null
    private var eventJob: Job? = null
    private var runJob: Job? = null

    init {
        eventJob = appScope.launch {
            eventBus.events.collect { event ->
                if (event.eventType == AgentEvent.PAGE_CHANGED) {
                    _taskState.update { current ->
                        current?.copy(
                            currentPage = event.activityName.ifBlank { current.currentPage },
                            packageName = event.packageName.ifBlank { current.packageName },
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    val synced = _taskState.value
                    if (synced != null) {
                        stateManager?.syncFromTask(
                            task = synced,
                            observation = CompactObservation(
                                level = ObservationLevel.L0,
                                packageName = synced.packageName,
                                activityName = synced.currentPage,
                            ),
                            emitEvent = true,
                        )
                    }
                }
            }
        }
    }

    suspend fun startTask(goal: String, maxFails: Int = 5): TaskState = mutex.withLock {
        cancelRequested.set(false)
        clearPendingPlan()
        cachedObservation = null
        observationCollector?.clearLightCache()
        scheduler.reset()
        var state = TaskState(
            taskId = UUID.randomUUID().toString(),
            goal = goal.trim(),
            state = AgentPhase.IDLE,
            maxFails = maxFails.coerceIn(1, 20),
        )
        _taskState.value = state
        // Short-term only — never SQLite on start hot path.
        memory?.writeTaskState(state, durable = false)
        tracer?.begin(state.taskId, state.goal)
        val bootstrap = observationCollector?.collect(
            ObservationCollector.Request(maxLevel = ObservationLevel.L0),
        )?.compact
        if (bootstrap != null) {
            state = state.copy(
                packageName = bootstrap.packageName.ifBlank { state.packageName },
                currentPage = bootstrap.activityName.ifBlank { state.currentPage },
                updatedAt = System.currentTimeMillis(),
            )
            _taskState.value = state
        }
        stateManager?.init(state, observation = bootstrap)
        Log.i(TAG, "startTask id=${state.taskId} goal=${state.goal}")
        state
    }

    suspend fun tick(): TaskState = mutex.withLock {
        var state = _taskState.value
            ?: return TaskState(
                taskId = "",
                goal = "",
                state = AgentPhase.FAILED,
                lastError = "No active task. Call startTask() first.",
            )
        if (state.isTerminal) return state
        if (cancelRequested.get() || scheduler.isStopped()) {
            return finishTrace(state.withPhase(AgentPhase.FAILED, "cancelled"))
        }
        if (state.goal.isBlank()) {
            state = state.withPhase(AgentPhase.FAILED, "Empty goal")
            return finishTrace(state)
        }

        val action: AgentAction
        var beforeSnap: UISnapshot
        var observation: UnifiedObservation? = null
        var lastCollectCompact: CompactObservation? = null

        if (pendingActions.isNotEmpty()) {
            // Fast path: no re-plan; L0 foreground only (no dump).
            action = pendingActions.removeFirst()
            activePlanStep = if (pendingPlanSteps.isNotEmpty()) {
                pendingPlanSteps.removeFirst()
            } else {
                PlanStep(
                    index = state.currentStep,
                    action = action,
                    status = StepStatus.PENDING,
                )
            }
            val pendingObs = observationCollector?.collect(
                ObservationCollector.Request(
                    maxLevel = ObservationLevel.L0,
                    previousPackage = state.packageName,
                    previousActivity = state.currentPage,
                    afterAction = true,
                )
            )
            lastCollectCompact = pendingObs?.compact
            beforeSnap = pendingObs?.snapshot ?: emptySnap(
                state.copy(
                    packageName = pendingObs?.compact?.packageName?.ifBlank { state.packageName }
                        ?: state.packageName,
                    currentPage = pendingObs?.compact?.activityName?.ifBlank { state.currentPage }
                        ?: state.currentPage,
                )
            )
            state = state.copy(
                currentPage = beforeSnap.page.ifBlank {
                    pendingObs?.compact?.activityName?.ifBlank { state.currentPage } ?: state.currentPage
                },
                packageName = beforeSnap.packageName.ifBlank {
                    pendingObs?.compact?.packageName?.ifBlank { state.packageName } ?: state.packageName
                },
                updatedAt = System.currentTimeMillis(),
            )
            _taskState.value = state
        } else {
            val skipFullTree = LocalRuleEngine.canPlanWithoutFullTree(state.goal)
            val complex = LocalRuleEngine.isComplexGoal(state.goal)

            // --- PERCEIVE via ObservationCollector only ---
            state = state.withPhase(AgentPhase.PERCEIVING)
            _taskState.value = state
            emitPhase(state, "正在感知界面…")

            val perceiveResult = runCatching {
                val req = ObservationCollector.Request(
                    maxLevel = when {
                        skipFullTree -> ObservationLevel.L0
                        complex -> ObservationLevel.L1
                        else -> ObservationLevel.L1
                    },
                    previousPackage = state.packageName,
                    previousActivity = state.currentPage,
                    afterAction = false,
                    complexTask = complex,
                )
                if (tracer != null && observationCollector != null) {
                    tracer.measureSuspend(AgentTrace.PERCEPTION) {
                        observationCollector.collect(req)
                    }
                } else {
                    observationCollector?.collect(req)
                }
            }.getOrElse {
                Log.w(TAG, "perceive failed", it)
                null
            }

            if (observationCollector != null && perceiveResult == null && !skipFullTree) {
                return finishTrace(
                    state.withPhase(AgentPhase.FAILED, "Perceive failed")
                )
            }

            lastCollectCompact = perceiveResult?.compact
            beforeSnap = when {
                skipFullTree || perceiveResult == null -> emptySnap(
                    state.copy(
                        packageName = perceiveResult?.compact?.packageName?.ifBlank { state.packageName }
                            ?: state.packageName,
                        currentPage = perceiveResult?.compact?.activityName?.ifBlank { state.currentPage }
                            ?: state.currentPage,
                    )
                )
                perceiveResult.snapshot != null -> perceiveResult.snapshot
                else -> emptySnap(
                    state.copy(
                        packageName = perceiveResult.compact.packageName.ifBlank { state.packageName },
                        currentPage = perceiveResult.compact.activityName.ifBlank { state.currentPage },
                    )
                )
            }
            observation = beforeSnap.root?.let { UnifiedObservation.fromSnapshot(beforeSnap) }
                ?: cachedObservation

            if (observation != null) cachedObservation = observation

            val compact = perceiveResult?.compact
            state = state.copy(
                currentPage = beforeSnap.page.ifBlank {
                    compact?.activityName?.ifBlank { state.currentPage } ?: state.currentPage
                },
                packageName = beforeSnap.packageName.ifBlank {
                    compact?.packageName?.ifBlank { state.packageName } ?: state.packageName
                },
                lastObservationSummary = when {
                    compact == null -> if (skipFullTree) "skip_full_tree" else "nodes=${beforeSnap.nodeCount}"
                    compact.fromCache -> "cache:${compact.treeHash}"
                    else -> "collector:${compact.level}"
                },
                updatedAt = System.currentTimeMillis(),
            )
            _taskState.value = state

            // --- PLAN ---
            state = state.withPhase(AgentPhase.PLANNING)
            _taskState.value = state
            emitPhase(state, "正在规划操作…")
            val plan = runCatching {
                when {
                    taskPlanner != null -> taskPlanner.plan(
                        TaskContext.of(
                            goal = state.goal,
                            state = state,
                            observation = observation,
                            allowLlm = complex,
                        )
                    )
                    observation != null -> planner.plan(state.goal, observation, state)
                    else -> planner.plan(state.goal, beforeSnap, state)
                }
            }.getOrElse {
                Log.w(TAG, "plan failed", it)
                return finishTraceIfNeeded(bumpFail(state, "Plan failed: ${it.message}"))
            }

            // Prefer ActionPlan.steps; fall back to actions→PlanStep.
            val steps = plan.preferredSteps()

            if (plan.done || steps.any { it.action.action == AgentAction.DONE }) {
                return finishTrace(
                    appendHistory(
                        state = state,
                        action = AgentAction(AgentAction.DONE, target = "planner"),
                        result = ActionExecuteResult(true, plan.reasoning.ifBlank { "done" }),
                        verification = ActionVerification(VerificationStatus.SUCCESS, "done"),
                    ).withPhase(AgentPhase.SUCCESS)
                )
            }

            if (steps.any { it.action.action == AgentAction.FAIL }) {
                val failStep = steps.first { it.action.action == AgentAction.FAIL }
                activePlanStep = failStep.copy(status = StepStatus.FAILED)
                val fail = failStep.action
                return finishTrace(
                    appendHistory(
                        state = state,
                        action = fail,
                        result = ActionExecuteResult(false, fail.target.ifBlank { plan.reasoning }),
                        verification = ActionVerification(VerificationStatus.FAILED, fail.target),
                    ).withPhase(AgentPhase.FAILED, fail.target.ifBlank { plan.reasoning })
                )
            }

            if (steps.isEmpty()) {
                return finishTraceIfNeeded(
                    bumpFail(state, plan.reasoning.ifBlank { "Empty ActionPlan" })
                )
            }

            val head = steps.first()
            activePlanStep = head
            action = head.action
            clearPendingPlan(clearActive = false)
            steps.drop(1).forEach { step ->
                pendingActions.addLast(step.action)
                pendingPlanSteps.addLast(step)
            }
        }

        // --- ACT + VERIFY (observation only via collectAfterAction) ---
        // PlanStep lifecycle: PENDING → RUNNING (before act)
        activePlanStep = (activePlanStep ?: PlanStep(
            index = state.currentStep,
            action = action,
            status = StepStatus.PENDING,
        )).copy(status = StepStatus.RUNNING)
        Log.d(
            TAG,
            "PlanStep#${activePlanStep!!.index} RUNNING action=${action.action}",
        )

        val maxRetries = verifier.maxRetriesFor(action)
        var attempt = 0
        var lastResult = ActionExecuteResult(false, "not executed")
        var lastVerification = ActionVerification(VerificationStatus.FAILED, "not verified")
        var afterSnap: UISnapshot = beforeSnap
        val pkgBeforeAct = state.packageName
        val pageBeforeAct = state.currentPage
        val complexGoal = LocalRuleEngine.isComplexGoal(state.goal)
        var postObs: CompactObservation? = lastCollectCompact

        while (true) {
            attempt++
            state = state.withPhase(AgentPhase.EXECUTING)
            _taskState.value = state
            emitPhase(state, statusForAction(action))
            runtimeEventBus?.tryEmit(
                AgentRuntimeEvent.ActionStarted(
                    taskId = state.taskId,
                    action = action,
                    step = state.currentStep + 1,
                    conversationId = eventConversationId,
                )
            )
            lastResult = runCatching {
                scheduler.submit(action).toExecuteResult()
            }.getOrElse {
                ActionExecuteResult(false, "Execute error: ${it.message}")
            }
            runtimeEventBus?.tryEmit(
                AgentRuntimeEvent.ActionFinished(
                    taskId = state.taskId,
                    action = action,
                    ok = lastResult.ok,
                    message = lastResult.message,
                    step = state.currentStep + 1,
                    conversationId = eventConversationId,
                )
            )
            if (scheduler.isStopped()) {
                activePlanStep = activePlanStep?.copy(status = StepStatus.FAILED)
                return finishTrace(
                    appendHistory(
                        state,
                        action,
                        lastResult,
                        ActionVerification(VerificationStatus.FAILED, "cancelled"),
                    ).withPhase(AgentPhase.FAILED, "cancelled")
                )
            }
            if (lastResult.observationSummary != null) {
                state = state.copy(lastObservationSummary = lastResult.observationSummary)
            }

            state = state.withPhase(AgentPhase.VERIFYING)
            _taskState.value = state
            emitPhase(state, "正在验证结果…")

            // Single post-action observation — no Tiered.observe / perceiveLight here.
            val afterResult = observationCollector?.collectAfterAction(
                previousPackage = pkgBeforeAct,
                previousActivity = pageBeforeAct,
                complexTask = complexGoal,
            )
            postObs = afterResult?.compact ?: postObs
            afterSnap = afterResult?.snapshot
                ?: emptySnap(
                    state.copy(
                        packageName = afterResult?.compact?.packageName?.ifBlank { state.packageName }
                            ?: state.packageName,
                        currentPage = afterResult?.compact?.activityName?.ifBlank { state.currentPage }
                            ?: state.currentPage,
                    )
                )
            state = state.copy(
                currentPage = afterSnap.page.ifBlank {
                    afterResult?.compact?.activityName?.ifBlank { state.currentPage } ?: state.currentPage
                },
                packageName = afterSnap.packageName.ifBlank {
                    afterResult?.compact?.packageName?.ifBlank { state.packageName } ?: state.packageName
                },
                lastObservationSummary = afterResult?.compact?.let { c ->
                    buildString {
                        append("obs:")
                        append(c.level)
                        if (c.fromCache) append(":cache")
                        if (c.packageName.isNotBlank()) append(" pkg=").append(c.packageName)
                        if (c.treeHash.isNotBlank()) append(" hash=").append(c.treeHash.take(8))
                    }
                } ?: state.lastObservationSummary,
                updatedAt = System.currentTimeMillis(),
            )
            _taskState.value = state

            lastVerification = try {
                val ctx = VerifyContext(
                    goal = state.goal,
                    action = action,
                    executeResult = lastResult,
                    before = beforeSnap,
                    after = afterSnap,
                    attempt = attempt,
                    maxRetries = maxRetries,
                )
                if (tracer != null) {
                    tracer.measureSuspend(AgentTrace.VERIFY) { verifier.verify(ctx) }
                } else {
                    verifier.verify(ctx)
                }
            } catch (e: Exception) {
                ActionVerification(
                    status = VerificationStatus.FAILED,
                    message = "Verifier error: ${e.message}",
                    attempt = attempt,
                )
            }

            when (lastVerification.status) {
                VerificationStatus.SUCCESS -> break
                VerificationStatus.FAILED -> break
                VerificationStatus.RETRY -> {
                    if (attempt > maxRetries) {
                        lastVerification = lastVerification.copy(
                            status = VerificationStatus.FAILED,
                            message = "Retries exhausted: ${lastVerification.message}",
                        )
                        break
                    }
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        state = appendHistory(state, action, lastResult, lastVerification, attempts = attempt)

        // PlanStep lifecycle: RUNNING → DONE | FAILED (same verdict as Verify)
        if (lastVerification.status == VerificationStatus.SUCCESS) {
            activePlanStep = activePlanStep?.copy(status = StepStatus.DONE)
            Log.d(TAG, "PlanStep#${activePlanStep?.index} DONE action=${action.action}")
            lightPlanner?.historyStore?.record(
                goal = state.goal,
                pageKey = TaskContext.pageKey(state.packageName, state.currentPage),
                action = action,
            )
            lightPlanner?.planCache?.recordSuccessPlan(
                goal = state.goal,
                currentState = TaskContext.pageKey(state.packageName, state.currentPage),
                actions = listOf(action),
            )
            val oneShot = pendingActions.isEmpty() &&
                LocalRuleEngine.isTerminalOneShotGoal(state.goal) &&
                (action.action == AgentAction.OPEN_APP || action.action == AgentAction.GLOBAL)
            state = if (oneShot) {
                state.withPhase(AgentPhase.SUCCESS)
            } else {
                state.copy(
                    state = AgentPhase.IDLE,
                    lastError = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            cachedObservation = null
        } else {
            activePlanStep = activePlanStep?.copy(status = StepStatus.FAILED)
            Log.d(
                TAG,
                "PlanStep#${activePlanStep?.index} FAILED action=${action.action} " +
                    "msg=${lastVerification.message}",
            )
            clearPendingPlan(clearActive = false)
            state = bumpFail(
                state,
                lastVerification.message.ifBlank { lastResult.message },
            )
        }

        commitState(
            state,
            durable = state.isTerminal,
            observation = postObs,
            lastAction = action,
            lastActionResult = lastResult,
        )
        if (state.isTerminal) tracer?.finish()
        Log.i(TAG, "tick → ${state.state} step=${state.currentStep} pending=${pendingActions.size}")
        return state
    }

    private fun finishTrace(state: TaskState): TaskState {
        commitState(state, durable = true)
        tracer?.finish()
        return state
    }

    private fun finishTraceIfNeeded(state: TaskState): TaskState {
        commitState(state, durable = state.isTerminal)
        if (state.isTerminal) tracer?.finish()
        return state
    }

    suspend fun runUntilDone(
        goal: String,
        maxSteps: Int = DEFAULT_MAX_STEPS,
        maxFails: Int = 5,
    ): TaskState {
        startTask(goal, maxFails)
        var steps = 0
        var state = _taskState.value!!
        while (!state.isTerminal && steps < maxSteps) {
            coroutineContext.ensureActive()
            if (cancelRequested.get() || scheduler.isStopped()) {
                state = finishTrace(state.withPhase(AgentPhase.FAILED, "cancelled"))
                break
            }
            // Re-read after cancel() may have marked the task terminal mid-loop.
            _taskState.value?.takeIf { it.isTerminal }?.let { return it }
            state = tick()
            steps++
        }
        if (!state.isTerminal) {
            state = finishTrace(state.withPhase(AgentPhase.FAILED, "Max steps reached ($maxSteps)"))
        }
        return state
    }

    fun runUntilDoneAsync(
        goal: String,
        maxSteps: Int = DEFAULT_MAX_STEPS,
        onFinished: ((TaskState) -> Unit)? = null,
    ): Job {
        runJob?.cancel()
        val job = appScope.launch {
            val result = withContext(Dispatchers.Default) {
                runUntilDone(goal, maxSteps)
            }
            onFinished?.invoke(result)
        }
        runJob = job
        return job
    }

    fun cancel(reason: String = "cancelled") {
        cancelRequested.set(true)
        scheduler.cancelAll(reason)
        runJob?.cancel()
        runJob = null
        // Prefer locking so pendingActions clear does not race tick; if tick holds the lock,
        // cancelRequested + FAILED state are enough for tick to exit without SUCCESS overwrite.
        if (mutex.tryLock()) {
            try {
                activePlanStep = activePlanStep?.copy(status = StepStatus.FAILED)
                clearPendingPlan(clearActive = false)
                markCancelledLocked(reason)
            } finally {
                mutex.unlock()
            }
        } else {
            markCancelledLocked(reason)
        }
    }

    private fun markCancelledLocked(reason: String) {
        _taskState.update { current ->
            val next = current?.takeUnless { it.isTerminal }
                ?.withPhase(AgentPhase.FAILED, reason)
                ?: current
            if (next != null) {
                memory?.writeTaskState(next, durable = true)
                appScope.launch(Dispatchers.Default) {
                    stateManager?.syncFromTask(next, emitEvent = true)
                }
            } else {
                appScope.launch(Dispatchers.Default) { stateManager?.clear() }
            }
            tracer?.finish()
            next
        }
    }

    /** Publish task state: short-term always; SQLite only when [durable] (async). Dual-write AgentState. */
    private fun commitState(
        state: TaskState,
        durable: Boolean = false,
        observation: CompactObservation? = null,
        lastAction: AgentAction? = null,
        lastActionResult: ActionExecuteResult? = null,
    ): TaskState {
        val existing = _taskState.value
        // Cancel already won: never let a late SUCCESS from tick overwrite FAILED.
        if (existing != null &&
            existing.taskId == state.taskId &&
            existing.isTerminal &&
            existing.state == AgentPhase.FAILED &&
            (state.state == AgentPhase.SUCCESS || !state.isTerminal || cancelRequested.get())
        ) {
            if (state.state == AgentPhase.SUCCESS || !state.isTerminal) {
                return existing
            }
        }
        val toCommit = if (cancelRequested.get() && state.state == AgentPhase.SUCCESS) {
            state.withPhase(AgentPhase.FAILED, "cancelled")
        } else {
            state
        }
        _taskState.value = toCommit
        memory?.writeTaskState(toCommit, durable = durable)
        // Sync AgentState off the hot path without blocking tick callers that already hold mutex.
        appScope.launch(Dispatchers.Default) {
            stateManager?.syncFromTask(
                task = toCommit,
                observation = observation,
                lastAction = lastAction,
                lastActionResult = lastActionResult,
                emitEvent = true,
            )
            if (toCommit.isTerminal) {
                // keep last snapshot for UI briefly; clear on next startTask
            }
        }
        return toCommit
    }

    private fun clearPendingPlan(clearActive: Boolean = true) {
        pendingActions.clear()
        pendingPlanSteps.clear()
        if (clearActive) activePlanStep = null
    }

    private fun emptySnap(state: TaskState) = UISnapshot(
        page = state.currentPage,
        packageName = state.packageName,
        timestamp = System.currentTimeMillis(),
    )

    private fun appendHistory(
        state: TaskState,
        action: AgentAction,
        result: ActionExecuteResult,
        verification: ActionVerification,
        attempts: Int = verification.attempt,
    ): TaskState {
        val step = state.currentStep + 1
        return state.copy(
            currentStep = step,
            history = state.history + ActionRecord(
                step = step,
                action = action,
                ok = result.ok && verification.status == VerificationStatus.SUCCESS,
                message = result.message,
                verification = verification.status,
                verifyMessage = verification.message,
                attempts = attempts,
            ),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun bumpFail(state: TaskState, error: String): TaskState {
        val fails = state.failCount + 1
        return if (fails >= state.maxFails) {
            state.copy(
                failCount = fails,
                state = AgentPhase.FAILED,
                lastError = error,
                updatedAt = System.currentTimeMillis(),
            )
        } else {
            state.copy(
                failCount = fails,
                state = AgentPhase.IDLE,
                lastError = error,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun emitPhase(state: TaskState, statusText: String) {
        runtimeEventBus?.tryEmit(
            AgentRuntimeEvent.PhaseChanged(
                taskId = state.taskId,
                phase = state.state,
                statusText = statusText,
                conversationId = eventConversationId,
            )
        )
        runtimeEventBus?.tryEmit(
            AgentRuntimeEvent.Progress(
                taskId = state.taskId,
                statusText = statusText,
                conversationId = eventConversationId,
            )
        )
    }

    private fun statusForAction(action: AgentAction): String = when (action.action) {
        AgentAction.OPEN_APP -> "正在打开应用…"
        AgentAction.CLICK_NODE, AgentAction.CLICK_XY -> "正在点击…"
        AgentAction.TYPE_TEXT -> "正在输入…"
        AgentAction.SWIPE -> "正在滑动…"
        AgentAction.GLOBAL -> "正在执行系统操作…"
        AgentAction.SEE_SCREEN, AgentAction.DUMP_UI -> "正在查看屏幕…"
        else -> "正在执行：${action.action}"
    }
}
