package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.flow.SharedFlow
import me.rerere.rikkahub.data.agent.capability.PhoneControlCore

/**
 * Production entry for device tasks: route → queue → AgentRuntime.
 */
class AgentManager(
    private val runtime: AgentRuntime,
    private val queue: AgentTaskQueue,
    private val core: PhoneControlCore,
    private val eventBus: AgentRuntimeEventBus,
    private val stateManager: AgentStateManager,
) {
    val events: SharedFlow<AgentRuntimeEvent> = eventBus.events
    val taskState = runtime.taskState
    /** External Runtime state outlet (dual-written with TaskState). */
    val agentState = stateManager.state
    val isDeviceBusy: Boolean
        get() = queue.isBusy || core.isRuntimeExclusive()

    fun route(text: String, phoneControlEnabled: Boolean): TaskRouteDecision =
        TaskRouter.classify(text, phoneControlEnabled)

    /**
     * Run a RULE-mode device task. Emits [AgentRuntimeEvent] for live UI updates.
     */
    suspend fun submitDeviceTask(
        goal: String,
        conversationId: String? = null,
        mode: ExecutionMode = ExecutionMode.RULE,
    ): AgentRunResult = queue.submitAndAwait(
        goal = goal,
        mode = mode,
        conversationId = conversationId,
    )

    fun cancel(reason: String = "cancelled") {
        queue.cancel(reason)
    }
}
