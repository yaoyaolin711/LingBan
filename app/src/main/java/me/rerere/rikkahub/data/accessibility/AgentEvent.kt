package me.rerere.rikkahub.data.accessibility

import kotlinx.serialization.Serializable

/**
 * Agent-facing accessibility event (normalized from [android.view.accessibility.AccessibilityEvent]).
 */
@Serializable
data class AgentEvent(
    val eventType: String,
    val packageName: String = "",
    val activityName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val affectedNode: AffectedNode? = null,
    /** Android AccessibilityEvent type bitmask/value for debugging. */
    val rawEventType: Int = 0,
) {
    companion object {
        const val PAGE_CHANGED = "PAGE_CHANGED"
        const val CONTENT_CHANGED = "CONTENT_CHANGED"
        const val VIEW_CLICKED = "VIEW_CLICKED"
        const val TEXT_CHANGED = "TEXT_CHANGED"
        const val VIEW_SCROLLED = "VIEW_SCROLLED"
        const val UNKNOWN = "UNKNOWN"
    }
}

@Serializable
data class AffectedNode(
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val viewId: String = "",
) {
    fun matchesText(query: String, partial: Boolean = true): Boolean {
        if (query.isBlank()) return false
        return if (partial) {
            text.contains(query, ignoreCase = true) ||
                contentDescription.contains(query, ignoreCase = true)
        } else {
            text.equals(query, ignoreCase = true) ||
                contentDescription.equals(query, ignoreCase = true)
        }
    }
}
