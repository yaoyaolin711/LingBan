package me.rerere.rikkahub.data.agent

/**
 * Runtime / task lifecycle events for TaskBall and Chat streaming updates.
 *
 * Named separately from accessibility [me.rerere.rikkahub.data.accessibility.AgentEvent].
 */
sealed interface AgentRuntimeEvent {
    val taskId: String
    val conversationId: String?
    val timestamp: Long

    data class TaskQueued(
        override val taskId: String,
        val goal: String,
        val mode: ExecutionMode,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    data class TaskStarted(
        override val taskId: String,
        val goal: String,
        val mode: ExecutionMode,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    data class PhaseChanged(
        override val taskId: String,
        val phase: AgentPhase,
        val statusText: String,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    data class ActionStarted(
        override val taskId: String,
        val action: AgentAction,
        val step: Int,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    data class ActionFinished(
        override val taskId: String,
        val action: AgentAction,
        val ok: Boolean,
        val message: String,
        val step: Int,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    data class Progress(
        override val taskId: String,
        val statusText: String,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    data class TaskSucceeded(
        override val taskId: String,
        val goal: String,
        val summary: String,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    data class TaskFailed(
        override val taskId: String,
        val goal: String,
        val error: String,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    data class TaskCancelled(
        override val taskId: String,
        val goal: String,
        val reason: String,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent

    /** Dual-write [me.rerere.rikkahub.data.agent.AgentState] snapshot.
     *
     * **UI primary process event** for TaskBall / Chat progress bubbles.
     * Prefer this over [Progress], [PhaseChanged], [ActionStarted], [ActionFinished]
     * for display; those remain for logging / debug.
     */
    data class StateUpdated(
        override val taskId: String,
        val phase: AgentPhase,
        val currentApp: String = "",
        val currentActivity: String = "",
        val lastAction: AgentAction? = null,
        val lastResult: ActionExecuteResult? = null,
        override val conversationId: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : AgentRuntimeEvent
}
