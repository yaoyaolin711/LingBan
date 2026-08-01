package me.rerere.rikkahub.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.rikkahub.data.accessibility.AccessibilityEventManager
import me.rerere.rikkahub.data.accessibility.AccessibilityKeepAlive
import me.rerere.rikkahub.data.accessibility.UIObservation
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.accessibility.UiTreeNode
import me.rerere.rikkahub.data.accessibility.UiTreeParser
import me.rerere.rikkahub.data.accessibility.WaitCondition
import me.rerere.rikkahub.data.accessibility.WaitResult
import me.rerere.rikkahub.data.agent.trace.AgentTrace
import me.rerere.rikkahub.data.agent.trace.AgentTracer
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.isSolaceAccessibilityEnabledInSystemSettings
import org.koin.core.context.GlobalContext
import androidx.core.app.NotificationManagerCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "SolaceA11y"
private const val MAX_DUMP_NODES = 120
private const val MAX_DUMP_DEPTH = 12

/**
 * 本机 UI 操控无障碍服务: 导出给 AI 工具读树 / 点击 / 滑动 / 输入 / 全局按键 / 截屏.
 *
 * Event path: onAccessibilityEvent → [AccessibilityEventManager] → [me.rerere.rikkahub.data.accessibility.AgentEventBus]
 */
class SolaceAccessibilityService : AccessibilityService() {

    /**
     * Compact flat node kept for existing phone-control tools (click-by-id / compact JSON).
     * Prefer [UISnapshot] / [UIObservation] for new Agent perception paths.
     */
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
        val nodeId: String = "",
        val enabled: Boolean = true,
        val scrollable: Boolean = false,
        val checkable: Boolean = false,
        val checked: Boolean = false,
        val focused: Boolean = false,
        val selected: Boolean = false,
        val parentNodeId: String? = null,
    )

    /**
     * Legacy flat snapshot used by DeviceControlTools.
     * Full hierarchical capture: [captureUISnapshot] / [observeUi].
     */
    data class UiSnapshot(
        val packageName: String,
        val windowTitle: String,
        val screenWidth: Int,
        val screenHeight: Int,
        val nodes: List<UiNode>,
        val page: String = packageName,
        val timestamp: Long = System.currentTimeMillis(),
        val tree: UiTreeNode? = null,
        val truncated: Boolean = false,
    )

    data class ScreenshotCapture(
        val jpegBytes: ByteArray,
        val width: Int,
        val height: Int,
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

    private val screenshotExecutor: Executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val eventManager: AccessibilityEventManager?
        get() = AccessibilityEventManager.instance
            ?: runCatching { GlobalContext.get().get<AccessibilityEventManager>() }.getOrNull()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        eventManager?.attachTextProbe { query, partial ->
            dumpUi(80).any { n ->
                if (partial) {
                    n.text.contains(query, ignoreCase = true) ||
                        n.contentDescription.contains(query, ignoreCase = true)
                } else {
                    n.text.equals(query, ignoreCase = true) ||
                        n.contentDescription.equals(query, ignoreCase = true)
                }
            }
        }
        // Cancel "please re-enable" notification once system has bound us again.
        NotificationManagerCompat.from(this).cancel(4101)
        Log.i(TAG, "service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        eventManager?.detachTextProbe()
        if (instance === this) instance = null
        Log.i(TAG, "service unbound")
        // If OEM force-stop stripped us from ENABLED_ACCESSIBILITY_SERVICES, ask user to re-enable.
        // (Cannot turn the system toggle back on from the app.)
        val stillEnabled = isSolaceAccessibilityEnabledInSystemSettings()
        if (!stillEnabled) {
            runCatching {
                val settings = GlobalContext.getOrNull()
                    ?.get<SettingsStore>()
                    ?.settingsFlow
                    ?.value
                if (settings != null) {
                    AccessibilityKeepAlive.notifyIfAccessibilityStripped(applicationContext, settings)
                } else {
                    AccessibilityKeepAlive.notifyReenable(applicationContext)
                }
            }.onFailure { Log.w(TAG, "notify a11y stripped failed", it) }
        }
        // Request rebind when the toggle is still on (temporary process death).
        return true
    }

    override fun onDestroy() {
        eventManager?.detachTextProbe()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        eventManager?.onAccessibilityEvent(event)
    }

    override fun onInterrupt() = Unit

    /**
     * Suspend until a wait condition is met (text appears / page change / event type).
     * Delegates to [AccessibilityEventManager.waitFor].
     */
    suspend fun waitFor(
        condition: WaitCondition,
        timeoutMs: Long = 5_000L,
    ): WaitResult {
        val mgr = eventManager
            ?: return WaitResult.Timeout(waitedMs = 0L, condition = "NO_EVENT_MANAGER")
        return mgr.waitFor(condition, timeoutMs)
    }

    fun dumpUi(maxNodes: Int = MAX_DUMP_NODES): List<UiNode> {
        return dumpUiSnapshot(maxNodes).nodes
    }

    /**
     * Full hierarchical UI snapshot for Agent perception (page / timestamp / tree).
     */
    fun captureUISnapshot(
        maxNodes: Int = MAX_DUMP_NODES,
        maxDepth: Int = MAX_DUMP_DEPTH,
    ): UISnapshot {
        val root = rootInActiveWindow
        val metrics = resources.displayMetrics
        val timestamp = System.currentTimeMillis()
        if (root == null) {
            val mgr = eventManager
            return UISnapshot(
                page = resolvePage(""),
                packageName = mgr?.currentPackageName.orEmpty(),
                timestamp = timestamp,
                windowTitle = "",
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                root = null,
                nodeCount = 0,
                truncated = false,
                maxDepth = maxDepth,
            )
        }
        return try {
            val mgr = eventManager
            val packageName = root.packageName?.toString().orEmpty()
                .ifBlank { mgr?.currentPackageName.orEmpty() }
            val windowTitle = root.paneTitle?.toString()
                ?: windows?.firstOrNull { it.isActive }?.title?.toString().orEmpty()
            val parsed = AgentTracer.instance?.measure(AgentTrace.UI_TREE) {
                UiTreeParser.parse(
                    root = root,
                    maxDepth = maxDepth.coerceIn(1, 32),
                    maxNodes = maxNodes.coerceIn(1, UiTreeParser.DEFAULT_MAX_NODES),
                )
            } ?: UiTreeParser.parse(
                root = root,
                maxDepth = maxDepth.coerceIn(1, 32),
                maxNodes = maxNodes.coerceIn(1, UiTreeParser.DEFAULT_MAX_NODES),
            )
            UISnapshot(
                page = resolvePage(packageName),
                packageName = packageName,
                timestamp = timestamp,
                windowTitle = windowTitle,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                root = parsed.root,
                nodeCount = parsed.nodeCount,
                truncated = parsed.truncated,
                maxDepth = parsed.maxDepthReached,
            )
        } finally {
            root.recycle()
        }
    }

    /** Unified Agent observation (`source=accessibility`, flat [elements] + optional tree). */
    fun observeUi(
        maxNodes: Int = MAX_DUMP_NODES,
        maxDepth: Int = MAX_DUMP_DEPTH,
    ): UIObservation = captureUISnapshot(maxNodes, maxDepth).toObservation()

    fun dumpUiSnapshot(maxNodes: Int = MAX_DUMP_NODES): UiSnapshot {
        val full = captureUISnapshot(maxNodes = maxNodes, maxDepth = MAX_DUMP_DEPTH)
        val flat = toCompactNodes(full.flattenNodes(), maxNodes.coerceIn(1, MAX_DUMP_NODES))
        return UiSnapshot(
            packageName = full.packageName,
            windowTitle = full.windowTitle,
            screenWidth = full.screenWidth,
            screenHeight = full.screenHeight,
            nodes = flat,
            page = full.page,
            timestamp = full.timestamp,
            tree = full.root,
            truncated = full.truncated,
        )
    }

    fun waitForUi(
        expectedPackage: String? = null,
        timeoutMs: Long = 2500L,
        pollMs: Long = 200L,
    ): UiSnapshot {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(300L, 8000L)
        var last = dumpUiSnapshot(40)
        while (System.currentTimeMillis() < deadline) {
            val pkgOk = expectedPackage.isNullOrBlank() ||
                last.packageName.equals(expectedPackage, ignoreCase = true)
            if (pkgOk && last.nodes.isNotEmpty()) return last
            try {
                Thread.sleep(pollMs.coerceIn(50L, 500L))
            } catch (_: InterruptedException) {
                break
            }
            last = dumpUiSnapshot(40)
        }
        return dumpUiSnapshot(80)
    }

    private fun resolvePage(packageName: String): String {
        val mgr = eventManager
        val activity = mgr?.currentActivityName.orEmpty()
        val eventPkg = mgr?.currentPackageName.orEmpty()
        if (activity.isNotBlank()) {
            if (packageName.isBlank() || activity.startsWith(packageName) ||
                eventPkg.equals(packageName, ignoreCase = true)
            ) {
                return activity
            }
        }
        return packageName.ifBlank { eventPkg }
    }

    /**
     * Prefer interactive / labeled nodes for compact tool payloads (token budget).
     * Full hierarchy remains on [UiSnapshot.tree] / [UISnapshot.root].
     */
    private fun toCompactNodes(flat: List<UiTreeNode>, maxNodes: Int): List<UiNode> {
        val useful = flat.filter { n ->
            !n.bounds.isEmpty && (
                n.text.isNotBlank() ||
                    n.contentDescription.isNotBlank() ||
                    n.clickable ||
                    n.editable ||
                    n.scrollable ||
                    n.checkable ||
                    n.focused ||
                    n.viewId.isNotBlank()
                )
        }
        val selected = if (useful.isNotEmpty()) useful else flat.filter { !it.bounds.isEmpty }
        return selected.take(maxNodes).mapIndexed { index, n ->
            UiNode(
                index = index,
                className = n.className.substringAfterLast('.'),
                text = n.text.take(80),
                contentDescription = n.contentDescription.take(80),
                viewId = n.viewId.substringAfterLast('/'),
                clickable = n.clickable,
                editable = n.editable,
                bounds = n.bounds.toCompactString(),
                centerX = n.centerX,
                centerY = n.centerY,
                nodeId = n.nodeId,
                enabled = n.enabled,
                scrollable = n.scrollable,
                checkable = n.checkable,
                checked = n.checked,
                focused = n.focused,
                selected = n.selected,
                parentNodeId = n.parentNodeId,
            )
        }
    }

    suspend fun captureScreenshotPng(
        maxWidth: Int = 720,
        quality: Int = 85,
    ): Result<ScreenshotCapture> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Result.failure(
                IllegalStateException("Screenshot requires Android 11+ (API 30)")
            )
        }
        return runCatching {
            val hardware = takeScreenshotBitmap()
            try {
                val scaled = scaleBitmap(hardware, maxWidth.coerceIn(360, 1280))
                try {
                    val stream = ByteArrayOutputStream()
                    // quality is ignored for PNG but kept for API symmetry
                    if (!scaled.compress(Bitmap.CompressFormat.PNG, quality.coerceIn(40, 100), stream)) {
                        error("PNG compress failed")
                    }
                    ScreenshotCapture(
                        jpegBytes = stream.toByteArray(),
                        width = scaled.width,
                        height = scaled.height,
                    )
                } finally {
                    if (scaled !== hardware) scaled.recycle()
                }
            } finally {
                hardware.recycle()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshotBitmap(): Bitmap =
        suspendCancellableCoroutine { cont ->
            val callback = object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    runCatching {
                        val wrapped = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace,
                        ) ?: error("wrapHardwareBuffer returned null")
                        val copy = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                            ?: error("Failed to copy hardware bitmap")
                        wrapped.recycle()
                        screenshot.hardwareBuffer.close()
                        copy
                    }.fold(
                        onSuccess = { if (cont.isActive) cont.resume(it) },
                        onFailure = { if (cont.isActive) cont.resumeWith(Result.failure(it)) },
                    )
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) {
                        cont.resumeWith(
                            Result.failure(IllegalStateException("takeScreenshot failed: code=$errorCode"))
                        )
                    }
                }
            }
            takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, callback)
        }

    private fun scaleBitmap(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val ratio = maxWidth.toFloat() / source.width
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, maxWidth, h, true)
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

    fun clickAt(x: Int, y: Int): ActionResult = dispatchTap(x.toFloat(), y.toFloat())

    fun longClickAt(x: Int, y: Int, durationMs: Long = 600L): ActionResult =
        dispatchTap(x.toFloat(), y.toFloat(), durationMs)

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
            val value = if (append) focused.text?.toString().orEmpty() + text else text
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
            else -> return ActionResult(
                false,
                "Unknown action '$name'. Use back/home/recents/notifications/quick_settings/power"
            )
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
            mainHandler,
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
}
