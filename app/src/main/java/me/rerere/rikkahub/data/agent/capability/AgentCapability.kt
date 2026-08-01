package me.rerere.rikkahub.data.agent.capability

import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.service.SolaceAccessibilityService

/**
 * Runtime-callable device capabilities. Implementations must not bypass [PhoneControlCore].
 */
interface AgentCapability {
    fun perceive(maxNodes: Int = 120): UISnapshot
    fun perceiveLight(): UISnapshot = perceive(maxNodes = 48)

    fun dumpUi(maxNodes: Int = 80): SolaceAccessibilityService.UiSnapshot?
    fun seeScreenLight(waitMs: Long = 0L): PhoneControlCore.CoreResult

    fun click(
        text: String? = null,
        viewId: String? = null,
        x: Int? = null,
        y: Int? = null,
        exact: Boolean = false,
    ): PhoneControlCore.CoreResult

    fun type(text: String, append: Boolean = false): PhoneControlCore.CoreResult
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300L): PhoneControlCore.CoreResult
    fun global(action: String): PhoneControlCore.CoreResult
    fun openApp(packageName: String, waitMs: Long = 2500L): PhoneControlCore.OpenAppResult
    suspend fun waitForText(text: String, timeoutMs: Long = 5_000L): PhoneControlCore.CoreResult
    suspend fun waitForPage(
        packageName: String? = null,
        activityName: String? = null,
        timeoutMs: Long = 5_000L,
    ): PhoneControlCore.CoreResult
}

class PhoneControlCapability(
    private val core: PhoneControlCore,
) : AgentCapability {
    override fun perceive(maxNodes: Int): UISnapshot = core.captureUISnapshot(maxNodes)

    override fun dumpUi(maxNodes: Int): SolaceAccessibilityService.UiSnapshot? =
        core.dumpUiSnapshot(maxNodes)

    override fun seeScreenLight(waitMs: Long): PhoneControlCore.CoreResult {
        if (waitMs > 0) {
            core.waitForUi(timeoutMs = waitMs)
        }
        val snap = core.captureUISnapshot(maxNodes = 48)
        return PhoneControlCore.CoreResult(
            ok = true,
            message = "light snapshot nodes=${snap.nodeCount} page=${snap.page}",
        )
    }

    override fun click(
        text: String?,
        viewId: String?,
        x: Int?,
        y: Int?,
        exact: Boolean,
    ): PhoneControlCore.CoreResult = core.click(text, viewId, x, y, exact)

    override fun type(text: String, append: Boolean): PhoneControlCore.CoreResult =
        core.typeText(text, append)

    override fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Long,
    ): PhoneControlCore.CoreResult = core.swipe(x1, y1, x2, y2, durationMs)

    override fun global(action: String): PhoneControlCore.CoreResult = core.globalAction(action)

    override fun openApp(packageName: String, waitMs: Long): PhoneControlCore.OpenAppResult =
        core.openApp(packageName = packageName, waitMs = waitMs)

    override suspend fun waitForText(text: String, timeoutMs: Long): PhoneControlCore.CoreResult =
        core.waitForText(text, timeoutMs)

    override suspend fun waitForPage(
        packageName: String?,
        activityName: String?,
        timeoutMs: Long,
    ): PhoneControlCore.CoreResult = core.waitForPage(packageName, activityName, timeoutMs)
}
