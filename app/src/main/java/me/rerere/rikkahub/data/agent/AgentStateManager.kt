package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe store for [AgentState] — Runtime's external state outlet.
 *
 * ## Sync contract (Stage 8.2)
 * - **Direction**: one-way only — [TaskState] → [AgentState] (never reverse).
 * - **Entry points**: [init] on task start; [syncFromTask] after Runtime commits
 *   (phase / foreground / observation / action); optional [updateObservation] /
 *   [updateAction] for fine-grained merges.
 * - **Events**: successful sync may emit [AgentRuntimeEvent.StateUpdated] (throttled).
 *
 * Callers that mutate Runtime progress must update TaskState first, then call
 * [syncFromTask]. UI / TaskBall / Chat should consume [state] or StateUpdated,
 * not mutate this manager as a second source of truth.
 */
class AgentStateManager(
    private val runtimeEventBus: AgentRuntimeEventBus? = null,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<AgentState?>(null)
    val state: StateFlow<AgentState?> = _state.asStateFlow()

    private val lastEmitAt = AtomicLong(0L)

    @Volatile
    var conversationId: String? = null

    fun snapshot(): AgentState? = _state.value

    suspend fun init(task: TaskState, observation: CompactObservation? = null) = mutex.withLock {
        val next = AgentState.fromTask(task, observation = observation)
        _state.value = next
        emitStateUpdated(next, throttle = false)
    }

    /**
     * One-way sync: mirror the latest [TaskState] into [AgentState].
     *
     * - Copies identity / phase / failCount / package+page / updatedAt from [task].
     * - Merges optional [observation] / [lastAction] / [lastActionResult] (falls back to previous AgentState).
     * - Does **not** write anything back into TaskState or AgentRuntime.
     */
    suspend fun syncFromTask(
        task: TaskState,
        observation: CompactObservation? = null,
        lastAction: AgentAction? = null,
        lastActionResult: ActionExecuteResult? = null,
        emitEvent: Boolean = true,
    ) = mutex.withLock {
        val prev = _state.value
        val mergedObs = observation ?: prev?.currentObservation
        val next = AgentState.fromTask(
            task = task,
            observation = mergedObs,
            lastAction = lastAction ?: prev?.lastAction,
            lastActionResult = lastActionResult ?: prev?.lastActionResult,
        ).copy(
            currentPackage = task.packageName.ifBlank {
                mergedObs?.packageName?.ifBlank { prev?.currentPackage }.orEmpty()
            },
            currentActivity = task.currentPage.ifBlank {
                mergedObs?.activityName?.ifBlank { prev?.currentActivity }.orEmpty()
            },
            treeHash = mergedObs?.treeHash?.ifBlank { prev?.treeHash }.orEmpty()
                .ifBlank { prev?.treeHash.orEmpty() },
        )
        _state.value = next
        if (emitEvent) emitStateUpdated(next, throttle = true)
    }

    suspend fun updateObservation(observation: CompactObservation) = mutex.withLock {
        val cur = _state.value ?: return@withLock
        val next = cur.copy(
            currentPackage = observation.packageName.ifBlank { cur.currentPackage },
            currentActivity = observation.activityName.ifBlank { cur.currentActivity },
            currentObservation = observation,
            treeHash = observation.treeHash.ifBlank { cur.treeHash },
            updatedAt = System.currentTimeMillis(),
        )
        _state.value = next
        emitStateUpdated(next, throttle = true)
    }

    suspend fun updateAction(
        action: AgentAction,
        result: ActionExecuteResult,
    ) = mutex.withLock {
        val cur = _state.value ?: return@withLock
        val next = cur.copy(
            lastAction = action,
            lastActionResult = result,
            updatedAt = System.currentTimeMillis(),
        )
        _state.value = next
        emitStateUpdated(next, throttle = false)
    }

    suspend fun clear() = mutex.withLock {
        _state.value = null
    }

    private fun emitStateUpdated(state: AgentState, throttle: Boolean) {
        val bus = runtimeEventBus ?: return
        val now = System.currentTimeMillis()
        if (throttle) {
            val last = lastEmitAt.get()
            if (now - last < EMIT_THROTTLE_MS) return
            if (!lastEmitAt.compareAndSet(last, now)) return
        } else {
            lastEmitAt.set(now)
        }
        bus.tryEmit(
            AgentRuntimeEvent.StateUpdated(
                taskId = state.taskId,
                phase = state.phase,
                currentApp = state.currentPackage,
                currentActivity = state.currentActivity,
                lastAction = state.lastAction,
                lastResult = state.lastActionResult,
                conversationId = conversationId,
            )
        )
    }

    companion object {
        private const val EMIT_THROTTLE_MS = 200L
    }
}
