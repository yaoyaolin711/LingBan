package me.rerere.rikkahub.data.accessibility

import kotlinx.serialization.Serializable

/**
 * Perception escalation levels (cost increases downward).
 *
 * L1 Accessibility incremental → L2 OCR → L3 Vision
 */
enum class PerceptionLevel {
    L1_A11Y,
    L2_OCR,
    L3_VISION,
}

/**
 * Delta-only UI snapshot — changed nodes since last baseline, not a full tree.
 */
@Serializable
data class IncrementalUISnapshot(
    val packageName: String = "",
    val page: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    /** Nodes touched by recent accessibility events / light diffs. */
    val changedNodes: List<ChangedNode> = emptyList(),
    val removedHints: List<String> = emptyList(),
    /** Fingerprint of current page surface for [ObservationCache]. */
    val treeHash: String = "",
    val previousTreeHash: String = "",
    val eventType: String = "",
    val changeCount: Int = changedNodes.size,
) {
    val isEmpty: Boolean get() = changedNodes.isEmpty()

    fun actionableCount(): Int = changedNodes.count { it.actionable }

    fun labeledCount(): Int = changedNodes.count { it.text.isNotBlank() || it.contentDescription.isNotBlank() }
}

@Serializable
data class ChangedNode(
    val nodeId: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val viewId: String = "",
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val actionable: Boolean = false,
    val bounds: UiBounds = UiBounds.EMPTY,
    val x: Int = 0,
    val y: Int = 0,
    val sourceEvent: String = "",
) {
    companion object {
        fun fromAffected(event: AgentEvent, index: Int): ChangedNode? {
            val n = event.affectedNode ?: return null
            val clickableHint = event.eventType == AgentEvent.VIEW_CLICKED
            return ChangedNode(
                nodeId = "delta_$index",
                text = n.text,
                contentDescription = n.contentDescription,
                className = n.className,
                viewId = n.viewId,
                clickable = clickableHint,
                actionable = clickableHint || n.text.isNotBlank(),
                sourceEvent = event.eventType,
            )
        }

        fun fromUiElement(el: UIElement, sourceEvent: String = ""): ChangedNode = ChangedNode(
            nodeId = el.id,
            text = el.text,
            contentDescription = el.contentDescription,
            className = el.className,
            viewId = el.viewId,
            clickable = el.clickable,
            editable = el.editable,
            actionable = el.clickable || el.editable || el.checkable,
            bounds = el.bounds,
            x = el.x,
            y = el.y,
            sourceEvent = sourceEvent,
        )
    }
}

/**
 * Request controlling how expensive perception may get.
 */
data class PerceptionRequest(
    /** Hard cap — never escalate beyond this. */
    val maxLevel: PerceptionLevel = PerceptionLevel.L1_A11Y,
    /**
     * After Act/Verify: force L1 only — never screenshot + OCR.
     */
    val afterAction: Boolean = false,
    /** Escalate to OCR when actionable/labeled nodes below this. */
    val minUsefulNodes: Int = 2,
    val forceRefresh: Boolean = false,
)

data class PerceptionResult(
    val level: PerceptionLevel,
    val fromCache: Boolean,
    val incremental: IncrementalUISnapshot,
    val snapshot: UISnapshot?,
    val observation: UnifiedObservation,
    val treeHash: String,
    val reason: String = "",
)
