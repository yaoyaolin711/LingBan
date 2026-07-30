package me.rerere.rikkahub.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "SolaceA11y"
private const val MAX_DUMP_NODES = 120
private const val MAX_DUMP_DEPTH = 12

/**
 * 本机 UI 操控无障碍服务: 导出给 AI 工具读树 / 点击 / 滑动 / 输入 / 全局按键.
 */
class SolaceAccessibilityService : AccessibilityService() {

    data class UiNode(
        val index: Int,
        val className: String,
        val text: String,
        val contentDescription: String,
        val viewId: String,
        val clickable: Boolean,
        val editable: Boolean,
        val bounds: String,
        val centerX: Int,
        val centerY: Int,
    )

    data class ActionResult(
        val ok: Boolean,
        val message: String,
    )

    companion object {
        @Volatile
        var instance: SolaceAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        Log.i(TAG, "service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun dumpUi(maxNodes: Int = MAX_DUMP_NODES): List<UiNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = ArrayList<UiNode>(maxNodes.coerceAtMost(MAX_DUMP_NODES))
        try {
            collectNodes(root, depth = 0, out = out, maxNodes = maxNodes.coerceIn(1, MAX_DUMP_NODES))
        } finally {
            root.recycle()
        }
        return out
    }

    fun clickByText(text: String, partial: Boolean = true): ActionResult {
        val node = findNode { n ->
            val t = n.text?.toString().orEmpty()
            val d = n.contentDescription?.toString().orEmpty()
            if (partial) {
                t.contains(text, ignoreCase = true) || d.contains(text, ignoreCase = true)
            } else {
                t.equals(text, ignoreCase = true) || d.equals(text, ignoreCase = true)
            }
        } ?: return ActionResult(false, "No node matched text='$text'")
        return clickNode(node)
    }

    fun clickByViewId(viewId: String): ActionResult {
        val root = rootInActiveWindow ?: return ActionResult(false, "No active window")
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            val target = nodes?.firstOrNull()
                ?: return ActionResult(false, "No node with viewId='$viewId'")
            clickNode(target)
        } finally {
            root.recycle()
        }
    }

    fun clickAt(x: Int, y: Int): ActionResult {
        return dispatchTap(x.toFloat(), y.toFloat())
    }

    fun longClickAt(x: Int, y: Int, durationMs: Long = 600L): ActionResult {
        return dispatchTap(x.toFloat(), y.toFloat(), durationMs)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300L): ActionResult {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50L, 2000L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGestureSync(gesture)
        return if (ok) ActionResult(true, "Swiped ($x1,$y1)->($x2,$y2)")
        else ActionResult(false, "Swipe gesture failed")
    }

    fun typeText(text: String, append: Boolean = false): ActionResult {
        val focused = findFocusedEditable()
            ?: return ActionResult(false, "No focused editable field. Click an input first.")
        return try {
            val args = Bundle()
            val value = if (append) {
                focused.text?.toString().orEmpty() + text
            } else {
                text
            }
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) ActionResult(true, "Typed ${text.length} chars")
            else ActionResult(false, "ACTION_SET_TEXT failed")
        } finally {
            focused.recycle()
        }
    }

    fun globalAction(name: String): ActionResult {
        val action = when (name.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power" -> GLOBAL_ACTION_POWER_DIALOG
            else -> return ActionResult(false, "Unknown action '$name'. Use back/home/recents/notifications/quick_settings/power")
        }
        val ok = performGlobalAction(action)
        return if (ok) ActionResult(true, "Performed global action: $name")
        else ActionResult(false, "Global action failed: $name")
    }

    private fun clickNode(node: AccessibilityNodeInfo): ActionResult {
        return try {
            var cur: AccessibilityNodeInfo? = node
            while (cur != null) {
                if (cur.isClickable) {
                    val ok = cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return if (ok) {
                        val label = cur.text?.toString()
                            ?: cur.contentDescription?.toString()
                            ?: cur.viewIdResourceName
                            ?: "node"
                        ActionResult(true, "Clicked: $label")
                    } else {
                        // fallback to gesture at center
                        val rect = Rect()
                        cur.getBoundsInScreen(rect)
                        dispatchTap(rect.exactCenterX(), rect.exactCenterY())
                    }
                }
                val parent = cur.parent
                if (cur !== node) cur.recycle()
                cur = parent
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            dispatchTap(rect.exactCenterX(), rect.exactCenterY())
        } finally {
            node.recycle()
        }
    }

    private fun dispatchTap(x: Float, y: Float, durationMs: Long = 50L): ActionResult {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(1L, 2000L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGestureSync(gesture)
        return if (ok) ActionResult(true, "Tapped at (${x.toInt()},${y.toInt()})")
        else ActionResult(false, "Tap gesture failed at (${x.toInt()},${y.toInt()})")
    }

    private fun dispatchGestureSync(gesture: GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        val result = booleanArrayOf(false)
        val sent = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    result[0] = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    result[0] = false
                    latch.countDown()
                }
            },
            null,
        )
        if (!sent) return false
        return runCatching {
            latch.await(2, TimeUnit.SECONDS) && result[0]
        }.getOrDefault(false)
    }

    private fun findFocusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return try {
            findNode(root) { it.isFocused && (it.isEditable || it.isFocusable) }
                ?: findNode(root) { it.isEditable }
        } finally {
            root.recycle()
        }
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return try {
            findNode(root, predicate)
        } finally {
            root.recycle()
        }
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, predicate)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: MutableList<UiNode>,
        maxNodes: Int,
    ) {
        if (out.size >= maxNodes || depth > MAX_DUMP_DEPTH) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val useful = text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isEditable ||
            viewId.isNotBlank()
        if (useful && rect.width() > 0 && rect.height() > 0) {
            out += UiNode(
                index = out.size,
                className = node.className?.toString()?.substringAfterLast('.') ?: "",
                text = text.take(80),
                contentDescription = desc.take(80),
                viewId = viewId.substringAfterLast('/'),
                clickable = node.isClickable,
                editable = node.isEditable,
                bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                centerX = rect.centerX(),
                centerY = rect.centerY(),
            )
        }
        for (i in 0 until node.childCount) {
            if (out.size >= maxNodes) return
            val child = node.getChild(i) ?: continue
            try {
                collectNodes(child, depth + 1, out, maxNodes)
            } finally {
                child.recycle()
            }
        }
    }
}
