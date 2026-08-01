package me.rerere.rikkahub.data.agent

import kotlinx.serialization.Serializable

/**
 * Priority for [ActionScheduler] queue ordering (lower ordinal = higher priority).
 */
enum class ActionPriority(val rank: Int) {
    /** Stop / cancel task — always first. */
    CRITICAL(0),
    HIGH(1),
    NORMAL(2),
    LOW(3),
}

/**
 * Scheduler-facing execution result (async, timed, cancellable).
 */
@Serializable
data class ActionResult(
    val success: Boolean,
    val costTime: Long = 0L,
    val error: String? = null,
    val message: String = "",
    val observationSummary: String? = null,
    val cancelled: Boolean = false,
    val timedOut: Boolean = false,
    val actionId: String = "",
) {
    fun toExecuteResult(): ActionExecuteResult = ActionExecuteResult(
        ok = success,
        message = when {
            error != null -> error
            message.isNotBlank() -> message
            else -> if (success) "ok" else "failed"
        },
        observationSummary = observationSummary,
    )

    companion object {
        fun fromExecute(
            result: ActionExecuteResult,
            costTime: Long,
            actionId: String = "",
        ): ActionResult = ActionResult(
            success = result.ok,
            costTime = costTime,
            error = if (result.ok) null else result.message,
            message = result.message,
            observationSummary = result.observationSummary,
            actionId = actionId,
        )
    }
}

/**
 * Queued work item for [ActionScheduler].
 */
data class ScheduledAction(
    val id: String,
    val action: AgentAction,
    val priority: ActionPriority = ActionPriority.NORMAL,
    val timeoutMs: Long = ActionSchedulerDefaults.timeoutFor(action),
    val enqueuedAt: Long = System.currentTimeMillis(),
)

object ActionSchedulerDefaults {
    const val CLICK_TIMEOUT_MS = 3_000L
    const val TYPE_TIMEOUT_MS = 3_000L
    const val OPEN_APP_TIMEOUT_MS = 5_000L
    const val WAIT_TIMEOUT_MS = 5_000L
    const val PERCEIVE_TIMEOUT_MS = 8_000L
    const val DEFAULT_TIMEOUT_MS = 5_000L
    /** Min gap between consecutive gesture-like actions. */
    const val THROTTLE_MS = 280L

    fun timeoutFor(action: AgentAction): Long {
        action.params["timeout_ms"]?.toLongOrNull()?.let { return it.coerceIn(100L, 60_000L) }
        action.params["timeout"]?.toLongOrNull()?.let {
            // support seconds: timeout=3 → 3000ms when value is small
            return if (it <= 120L) (it * 1000L).coerceIn(100L, 60_000L) else it.coerceIn(100L, 60_000L)
        }
        return when (action.action) {
            AgentAction.CLICK_NODE, AgentAction.CLICK_XY -> CLICK_TIMEOUT_MS
            AgentAction.TYPE_TEXT -> TYPE_TIMEOUT_MS
            AgentAction.OPEN_APP -> OPEN_APP_TIMEOUT_MS
            AgentAction.WAIT_FOR_TEXT, AgentAction.WAIT_FOR_PAGE -> WAIT_TIMEOUT_MS
            AgentAction.SEE_SCREEN, AgentAction.DUMP_UI -> PERCEIVE_TIMEOUT_MS
            AgentAction.SWIPE, AgentAction.GLOBAL -> CLICK_TIMEOUT_MS
            else -> DEFAULT_TIMEOUT_MS
        }
    }

    fun priorityFor(action: AgentAction): ActionPriority {
        action.params["priority"]?.lowercase()?.let { p ->
            return when (p) {
                "critical", "stop" -> ActionPriority.CRITICAL
                "high" -> ActionPriority.HIGH
                "low" -> ActionPriority.LOW
                else -> ActionPriority.NORMAL
            }
        }
        return when (action.action) {
            AgentAction.FAIL -> ActionPriority.HIGH
            AgentAction.DONE -> ActionPriority.HIGH
            AgentAction.SEE_SCREEN, AgentAction.DUMP_UI -> ActionPriority.LOW
            else -> ActionPriority.NORMAL
        }
    }

    fun needsThrottle(action: AgentAction): Boolean = when (action.action) {
        AgentAction.CLICK_NODE, AgentAction.CLICK_XY, AgentAction.TYPE_TEXT, AgentAction.SWIPE -> true
        else -> false
    }
}
