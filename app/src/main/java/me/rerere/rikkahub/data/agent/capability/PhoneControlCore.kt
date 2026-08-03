package me.rerere.rikkahub.data.agent.capability

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import me.rerere.rikkahub.data.accessibility.WaitCondition
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.service.SolaceAccessibilityService
import me.rerere.rikkahub.utils.isSolaceAccessibilityEnabledInSystemSettings
import me.rerere.rikkahub.utils.openAccessibilitySettings
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single implementation of phone control primitives.
 *
 * Both [me.rerere.rikkahub.data.ai.tools.local.DeviceControlTools] (LLM tools)
 * and [PhoneControlCapability] (AgentRuntime) must go through this class.
 */
class PhoneControlCore(
    private val context: Context,
) {
    /**
     * Runtime 独占令牌：>0 表示占用中。
     * 只有持有相同 token 的 [endRuntimeExclusive] 才能解除，避免旧任务 finally 清掉新任务的占用。
     */
    private val exclusiveToken = AtomicInteger(0)
    private val tokenSeq = AtomicInteger(0)

    /** @return 占用令牌，结束时必须原样传给 [endRuntimeExclusive] */
    fun beginRuntimeExclusive(): Int {
        val token = tokenSeq.incrementAndGet().coerceAtLeast(1)
        exclusiveToken.set(token)
        return token
    }

    fun endRuntimeExclusive(token: Int) {
        if (token <= 0) return
        exclusiveToken.compareAndSet(token, 0)
    }

    fun isRuntimeExclusive(): Boolean = exclusiveToken.get() != 0

    fun serviceOrNull(openSettingsIfMissing: Boolean = false): SolaceAccessibilityService? {
        val service = SolaceAccessibilityService.instance
        if (service == null && openSettingsIfMissing) {
            context.openAccessibilitySettings()
        }
        return service
    }

    fun isAccessibilityAvailable(): Boolean {
        if (SolaceAccessibilityService.instance != null) return true
        return context.isSolaceAccessibilityEnabledInSystemSettings()
    }

    /** Tool path: reject while Runtime owns the device. */
    fun guardToolsAllowed(): CoreResult? {
        if (isRuntimeExclusive()) {
            return CoreResult(false, ERROR_DEVICE_BUSY)
        }
        return null
    }

    fun dumpUiSnapshot(maxNodes: Int = 80): SolaceAccessibilityService.UiSnapshot? {
        return serviceOrNull()?.dumpUiSnapshot(maxNodes.coerceIn(8, 200))
    }

    fun captureUISnapshot(maxNodes: Int = 120): UISnapshot {
        return serviceOrNull()?.captureUISnapshot(maxNodes = maxNodes.coerceIn(8, 200))
            ?: UISnapshot(
                page = "",
                packageName = "",
                timestamp = System.currentTimeMillis(),
            )
    }

    fun waitForUi(
        expectedPackage: String? = null,
        timeoutMs: Long = 2000L,
    ): SolaceAccessibilityService.UiSnapshot? {
        return serviceOrNull()?.waitForUi(
            expectedPackage = expectedPackage,
            timeoutMs = timeoutMs,
        )
    }

    suspend fun captureScreenshotPng(maxWidth: Int = 720) =
        serviceOrNull()?.captureScreenshotPng(maxWidth = maxWidth)

    fun clickByText(text: String, partial: Boolean = true): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        return service.clickByText(text, partial).toCore()
    }

    fun clickByViewId(viewId: String): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        return service.clickByViewId(viewId).toCore()
    }

    fun clickAt(x: Int, y: Int): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        return service.clickAt(x, y).toCore()
    }

    /**
     * Prefer text → view_id → x/y. Short view_id may resolve via a light dump.
     */
    fun click(
        text: String? = null,
        viewId: String? = null,
        x: Int? = null,
        y: Int? = null,
        exact: Boolean = false,
    ): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        return when {
            !text.isNullOrBlank() -> service.clickByText(text, partial = !exact).toCore()
            !viewId.isNullOrBlank() -> {
                if (viewId.contains(':')) {
                    service.clickByViewId(viewId).toCore()
                } else {
                    val match = service.dumpUi(120).firstOrNull { n ->
                        n.viewId == viewId || n.viewId.endsWith("/$viewId")
                    }
                    if (match != null) service.clickAt(match.centerX, match.centerY).toCore()
                    else service.clickByViewId(viewId).toCore()
                }
            }
            x != null && y != null -> service.clickAt(x, y).toCore()
            else -> CoreResult(false, "Provide text, view_id, or x+y")
        }
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300L): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        return service.swipe(x1, y1, x2, y2, durationMs).toCore()
    }

    fun typeText(text: String, append: Boolean = false): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        return service.typeText(text, append).toCore()
    }

    fun globalAction(name: String): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        return service.globalAction(name).toCore()
    }

    fun openApp(
        packageName: String? = null,
        appName: String? = null,
        waitMs: Long = 2000L,
    ): OpenAppResult {
        val pm = context.packageManager
        val targetPkg = when {
            !packageName.isNullOrBlank() -> packageName
            !appName.isNullOrBlank() -> resolvePackageByLabel(pm, appName)
            else -> null
        }
        if (targetPkg.isNullOrBlank()) {
            return OpenAppResult(ok = false, message = "Provide package or name")
        }
        if (targetPkg in BLOCKED_PACKAGES || targetPkg.startsWith("com.android.settings")) {
            return OpenAppResult(
                ok = false,
                message = "Opening system settings / phone apps is blocked for safety",
            )
        }
        val launch = pm.getLaunchIntentForPackage(targetPkg)
            ?: return OpenAppResult(ok = false, message = "No launcher activity for $targetPkg")
        return try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            val snapshot = serviceOrNull()?.waitForUi(
                expectedPackage = targetPkg,
                timeoutMs = waitMs.coerceIn(500L, 5000L),
            )
            OpenAppResult(
                ok = true,
                packageName = targetPkg,
                message = "Launched $targetPkg",
                snapshot = snapshot,
            )
        } catch (e: Exception) {
            OpenAppResult(ok = false, message = e.message ?: "Launch failed")
        }
    }

    suspend fun waitForText(text: String, timeoutMs: Long = 5_000L): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        val wait = service.waitFor(WaitCondition.TextAppears(text), timeoutMs = timeoutMs)
        return CoreResult(wait.ok, wait.toString())
    }

    suspend fun waitForPage(
        packageName: String? = null,
        activityName: String? = null,
        timeoutMs: Long = 5_000L,
    ): CoreResult {
        val service = serviceOrNull() ?: return CoreResult(false, ERROR_NO_A11Y)
        val wait = service.waitFor(
            WaitCondition.PageChanged(
                packageName = packageName,
                activityName = activityName,
            ),
            timeoutMs = timeoutMs,
        )
        return CoreResult(wait.ok, wait.toString())
    }

    data class CoreResult(
        val ok: Boolean,
        val message: String = "",
    )

    data class OpenAppResult(
        val ok: Boolean,
        val message: String = "",
        val packageName: String? = null,
        val snapshot: SolaceAccessibilityService.UiSnapshot? = null,
    )

    companion object {
        const val ERROR_NO_A11Y = "NO_ACCESSIBILITY"
        const val ERROR_DEVICE_BUSY = "DEVICE_BUSY_RUNTIME"

        private val BLOCKED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
        )

        fun resolvePackageByLabel(pm: PackageManager, label: String): String? {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            val exact = apps.firstOrNull {
                it.loadLabel(pm).toString().equals(label, ignoreCase = true)
            }
            if (exact != null) return exact.activityInfo.packageName
            return apps.firstOrNull {
                it.loadLabel(pm).toString().contains(label, ignoreCase = true)
            }?.activityInfo?.packageName
        }
    }
}

private fun SolaceAccessibilityService.ActionResult.toCore() =
    PhoneControlCore.CoreResult(ok = ok, message = message)
