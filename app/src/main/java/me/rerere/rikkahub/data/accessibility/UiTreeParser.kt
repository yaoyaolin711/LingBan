package me.rerere.rikkahub.data.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.IdentityHashMap

/**
 * Recursively parses [AccessibilityNodeInfo] into a hierarchical [UiTreeNode] tree.
 *
 * Guarantees:
 * - Parent/child hierarchy preserved via [UiTreeNode.children] + [UiTreeNode.parentNodeId]
 * - Cycle-safe via identity-visited set
 * - Bounded by [maxDepth] and [maxNodes]
 */
object UiTreeParser {

    const val DEFAULT_MAX_DEPTH = 12
    const val DEFAULT_MAX_NODES = 200

    data class ParseResult(
        val root: UiTreeNode?,
        val nodeCount: Int,
        val truncated: Boolean,
        val maxDepthReached: Int,
    )

    fun parse(
        root: AccessibilityNodeInfo?,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): ParseResult {
        if (root == null) {
            return ParseResult(root = null, nodeCount = 0, truncated = false, maxDepthReached = 0)
        }
        val depthLimit = maxDepth.coerceIn(1, 32)
        val nodeLimit = maxNodes.coerceIn(1, 500)
        val visited = IdentityHashMap<AccessibilityNodeInfo, Boolean>()
        val counter = IntArray(1) // node id sequence
        val depthReached = IntArray(1)
        val truncated = booleanArrayOf(false)

        val tree = walk(
            node = root,
            depth = 0,
            parentNodeId = null,
            maxDepth = depthLimit,
            maxNodes = nodeLimit,
            visited = visited,
            counter = counter,
            depthReached = depthReached,
            truncated = truncated,
            recycleNode = false, // caller owns root
        )

        return ParseResult(
            root = tree,
            nodeCount = counter[0],
            truncated = truncated[0],
            maxDepthReached = depthReached[0],
        )
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        parentNodeId: String?,
        maxDepth: Int,
        maxNodes: Int,
        visited: IdentityHashMap<AccessibilityNodeInfo, Boolean>,
        counter: IntArray,
        depthReached: IntArray,
        truncated: BooleanArray,
        recycleNode: Boolean,
    ): UiTreeNode? {
        try {
            if (counter[0] >= maxNodes) {
                truncated[0] = true
                return null
            }
            if (visited.put(node, true) != null) {
                // Already visited — cycle guard
                truncated[0] = true
                return null
            }
            if (depth > maxDepth) {
                truncated[0] = true
                return null
            }

            depthReached[0] = maxOf(depthReached[0], depth)
            val nodeId = "n${counter[0]}"
            counter[0] += 1

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val bounds = UiBounds.fromRect(rect)

            val children = ArrayList<UiTreeNode>()
            if (depth < maxDepth) {
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    if (counter[0] >= maxNodes) {
                        truncated[0] = true
                        break
                    }
                    val child = node.getChild(i) ?: continue
                    val childNode = walk(
                        node = child,
                        depth = depth + 1,
                        parentNodeId = nodeId,
                        maxDepth = maxDepth,
                        maxNodes = maxNodes,
                        visited = visited,
                        counter = counter,
                        depthReached = depthReached,
                        truncated = truncated,
                        recycleNode = true,
                    )
                    if (childNode != null) children += childNode
                }
            } else if (node.childCount > 0) {
                truncated[0] = true
            }

            return UiTreeNode(
                nodeId = nodeId,
                text = node.text?.toString().orEmpty().take(200),
                contentDescription = node.contentDescription?.toString().orEmpty().take(200),
                className = node.className?.toString().orEmpty(),
                packageName = node.packageName?.toString().orEmpty(),
                viewId = node.viewIdResourceName.orEmpty(),
                clickable = node.isClickable,
                enabled = node.isEnabled,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                checkable = node.isCheckable,
                checked = node.isChecked,
                focused = node.isFocused,
                selected = node.isSelected,
                bounds = bounds,
                parentNodeId = parentNodeId,
                children = children,
            )
        } finally {
            if (recycleNode) {
                runCatching { node.recycle() }
            }
        }
    }
}
