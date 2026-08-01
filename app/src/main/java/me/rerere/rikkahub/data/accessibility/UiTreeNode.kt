package me.rerere.rikkahub.data.accessibility

import kotlinx.serialization.Serializable

/**
 * Full hierarchical accessibility node for Agent reasoning.
 * Children preserve on-screen tree structure; [parentNodeId] links upward without cycles in JSON.
 */
@Serializable
data class UiTreeNode(
    val nodeId: String,
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val packageName: String = "",
    val viewId: String = "",
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val focused: Boolean = false,
    val selected: Boolean = false,
    val bounds: UiBounds = UiBounds.EMPTY,
    val parentNodeId: String? = null,
    val children: List<UiTreeNode> = emptyList(),
) {
    val centerX: Int get() = bounds.centerX
    val centerY: Int get() = bounds.centerY

    /** Depth-first flatten (self then descendants). */
    fun flatten(): List<UiTreeNode> {
        val out = ArrayList<UiTreeNode>()
        fun walk(n: UiTreeNode) {
            out += n
            n.children.forEach(::walk)
        }
        walk(this)
        return out
    }

    fun findById(id: String): UiTreeNode? {
        if (nodeId == id) return this
        children.forEach { child ->
            child.findById(id)?.let { return it }
        }
        return null
    }

    fun findByViewId(viewId: String): UiTreeNode? {
        if (viewId.isBlank()) return null
        if (this.viewId == viewId || this.viewId.endsWith("/$viewId") ||
            this.viewId.substringAfterLast('/') == viewId
        ) {
            return this
        }
        children.forEach { child ->
            child.findByViewId(viewId)?.let { return it }
        }
        return null
    }
}
