package me.rerere.rikkahub.data.accessibility

import kotlinx.serialization.Serializable

/**
 * Point-in-time capture of the foreground UI for Agent perception.
 *
 * [page] is preferably the foreground Activity class name (e.g. com.xxx.MainActivity),
 * falling back to package name when Activity cannot be resolved.
 */
@Serializable
data class UISnapshot(
    val page: String,
    val packageName: String,
    val timestamp: Long,
    val windowTitle: String = "",
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    /** Hierarchical tree root; null when no active window. */
    val root: UiTreeNode? = null,
    val nodeCount: Int = 0,
    val truncated: Boolean = false,
    val maxDepth: Int = 0,
) {
    /** Root children when present, otherwise empty — matches `{ nodes: [] }` agent shape. */
    val nodes: List<UiTreeNode>
        get() = root?.let { listOf(it) }.orEmpty()

    fun flattenNodes(): List<UiTreeNode> = root?.flatten().orEmpty()

    fun toObservation(
        source: String = UIObservation.SOURCE_ACCESSIBILITY,
    ): UIObservation = UIObservation.fromSnapshot(this, source)
}
