package me.rerere.rikkahub.data.accessibility

/**
 * Wait predicates for Agent closed-loop execution (click → wait → dump).
 */
sealed class WaitCondition {
    /** Wait until a node with the given text / contentDescription appears on screen. */
    data class TextAppears(
        val text: String,
        val partial: Boolean = true,
        val packageName: String? = null,
    ) : WaitCondition()

    /** Wait until the foreground page changes (optionally to a specific package / activity). */
    data class PageChanged(
        val packageName: String? = null,
        val activityName: String? = null,
    ) : WaitCondition()

    /** Wait for the next AgentEvent of a given [eventType] (e.g. [AgentEvent.VIEW_CLICKED]). */
    data class EventOfType(
        val eventType: String,
        val packageName: String? = null,
    ) : WaitCondition()

    /** Arbitrary predicate over emitted [AgentEvent]s. */
    data class Predicate(
        val description: String = "custom",
        val match: (AgentEvent) -> Boolean,
    ) : WaitCondition()
}

sealed class WaitResult {
    data class Success(
        val event: AgentEvent?,
        val matchedBy: String,
        val waitedMs: Long,
    ) : WaitResult()

    data class Timeout(
        val waitedMs: Long,
        val condition: String,
    ) : WaitResult()

    val ok: Boolean get() = this is Success
}
