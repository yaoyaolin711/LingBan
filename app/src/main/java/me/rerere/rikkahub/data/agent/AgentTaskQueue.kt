package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.agent.capability.PhoneControlCore
import java.util.UUID

/**
 * Lightweight device-task queue.
 *
 * - Max pending slot: 1
 * - New task overwrites pending and cancels the running Runtime task
 */
class AgentTaskQueue(
    private val runtime: AgentRuntime,
    private val core: PhoneControlCore,
    private val eventBus: AgentRuntimeEventBus,
    private val appScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var runningJob: Job? = null
    private var activeTaskId: String? = null
    private var activeGoal: String = ""
    private var activeConversationId: String? = null

    val isBusy: Boolean
        get() = runningJob?.isActive == true

    /**
     * Submit a device task. Cancels any previous Runtime work and starts the new goal.
     */
    suspend fun submitAndAwait(
        goal: String,
        mode: ExecutionMode = ExecutionMode.RULE,
        conversationId: String? = null,
        maxSteps: Int = AgentRuntime.DEFAULT_MAX_STEPS,
    ): AgentRunResult {
        val taskId = UUID.randomUUID().toString()
        eventBus.tryEmit(
            AgentRuntimeEvent.TaskQueued(
                taskId = taskId,
                goal = goal,
                mode = mode,
                conversationId = conversationId,
            )
        )

        // Stop any previous device job and wait until it fully exits before starting a new one.
        // Joining outside the mutex avoids deadlocking the job's finally block.
        cancelAndJoin(reason = "superseded")

        val deferred = CompletableDeferred<AgentRunResult>()
        var leftoverJob: Job? = null
        mutex.withLock {
            // Another concurrent submit may have started between join and here.
            if (runningJob?.isActive == true) {
                leftoverJob = runningJob
                cancelInternal(reason = "superseded", emitEvent = true)
            }
        }
        runCatching { leftoverJob?.join() }

        mutex.withLock {
            activeTaskId = taskId
            activeGoal = goal
            activeConversationId = conversationId

            // Phase 1: only RULE is executed.
            val effectiveMode = ExecutionMode.RULE

            val job = appScope.launch {
                val exclusiveToken = core.beginRuntimeExclusive()
                try {
                    runtime.eventConversationId = conversationId
                    eventBus.tryEmit(
                        AgentRuntimeEvent.TaskStarted(
                            taskId = taskId,
                            goal = goal,
                            mode = effectiveMode,
                            conversationId = conversationId,
                        )
                    )
                    eventBus.tryEmit(
                        AgentRuntimeEvent.Progress(
                            taskId = taskId,
                            statusText = "正在执行：$goal",
                            conversationId = conversationId,
                        )
                    )
                    val state = withContext(Dispatchers.Default) {
                        runtime.runUntilDone(goal = goal, maxSteps = maxSteps)
                    }
                    val result = AgentRunResult.from(state, taskId, conversationId)
                    if (result.success) {
                        eventBus.tryEmit(
                            AgentRuntimeEvent.TaskSucceeded(
                                taskId = result.taskId,
                                goal = result.goal,
                                summary = result.summary,
                                conversationId = conversationId,
                            )
                        )
                    } else {
                        eventBus.tryEmit(
                            AgentRuntimeEvent.TaskFailed(
                                taskId = result.taskId,
                                goal = result.goal,
                                error = result.summary,
                                conversationId = conversationId,
                            )
                        )
                    }
                    deferred.complete(result)
                } catch (e: CancellationException) {
                    val result = AgentRunResult(
                        taskId = taskId,
                        goal = goal,
                        success = false,
                        summary = "cancelled",
                        phase = AgentPhase.FAILED,
                        conversationId = conversationId,
                    )
                    // TaskCancelled is emitted by cancelInternal; avoid duplicate Failed noise.
                    deferred.complete(result)
                    throw e
                } catch (e: Exception) {
                    val result = AgentRunResult(
                        taskId = taskId,
                        goal = goal,
                        success = false,
                        summary = e.message ?: "cancelled",
                        phase = AgentPhase.FAILED,
                        conversationId = conversationId,
                    )
                    eventBus.tryEmit(
                        AgentRuntimeEvent.TaskFailed(
                            taskId = taskId,
                            goal = goal,
                            error = result.summary,
                            conversationId = conversationId,
                        )
                    )
                    deferred.complete(result)
                } finally {
                    // 仅释放本任务的占用令牌，避免清掉后续新任务的独占
                    core.endRuntimeExclusive(exclusiveToken)
                    if (activeTaskId == taskId) {
                        activeTaskId = null
                        activeGoal = ""
                        activeConversationId = null
                    }
                }
            }
            runningJob = job
        }
        return deferred.await()
    }

    fun cancel(reason: String = "cancelled") {
        appScope.launch {
            cancelAndJoin(reason)
        }
    }

    /**
     * Cancel the active device task and wait until its Job fully stops.
     * Callers that start a new chat turn must use this (not fire-and-forget [cancel]).
     */
    suspend fun cancelAndJoin(reason: String = "cancelled") {
        val jobToJoin = mutex.withLock {
            val job = runningJob
            if (job != null || activeTaskId != null) {
                cancelInternal(reason = reason, emitEvent = true)
            }
            job
        }
        runCatching { jobToJoin?.join() }
    }

    private fun cancelInternal(reason: String, emitEvent: Boolean) {
        val prevId = activeTaskId
        val prevGoal = activeGoal
        val prevConversation = activeConversationId
        runtime.cancel(reason)
        runningJob?.cancel()
        runningJob = null
        // 占用由 Job finally 用 token 释放，这里不再无条件 end，防止清掉新任务占用
        if (emitEvent && prevId != null) {
            eventBus.tryEmit(
                AgentRuntimeEvent.TaskCancelled(
                    taskId = prevId,
                    goal = prevGoal,
                    reason = reason,
                    conversationId = prevConversation,
                )
            )
        }
        activeTaskId = null
        activeGoal = ""
        activeConversationId = null
    }
}

data class AgentRunResult(
    val taskId: String,
    val goal: String,
    val success: Boolean,
    val summary: String,
    val phase: AgentPhase,
    val conversationId: String? = null,
) {
    companion object {
        fun from(state: TaskState, taskId: String, conversationId: String?): AgentRunResult {
            val success = state.state == AgentPhase.SUCCESS
            val summary = when {
                success -> buildSuccessSummary(state)
                else -> state.lastError?.ifBlank { null } ?: "任务失败"
            }
            return AgentRunResult(
                taskId = taskId.ifBlank { state.taskId },
                goal = state.goal,
                success = success,
                summary = summary,
                phase = state.state,
                conversationId = conversationId,
            )
        }

        private fun buildSuccessSummary(state: TaskState): String {
            val last = state.history.lastOrNull()
            val actionHint = last?.let { "${it.action.action} ${it.action.target}".trim() }
            return buildString {
                append("已完成：").append(state.goal)
                if (!actionHint.isNullOrBlank()) {
                    append("（").append(actionHint).append("）")
                }
                if (state.packageName.isNotBlank()) {
                    append("\n当前应用：").append(state.packageName)
                }
                if (state.currentPage.isNotBlank()) {
                    append("\n页面：").append(state.currentPage)
                }
            }
        }
    }
}
