package me.rerere.rikkahub.data.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Central listener that converts Android [AccessibilityEvent]s into [AgentEvent]s
 * and publishes them on [AgentEventBus]. Also provides [waitFor] for Agent loops.
 */
class AccessibilityEventManager(
    private val eventBus: AgentEventBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var currentPackageName: String = ""
        private set

    @Volatile
    var currentActivityName: String = ""
        private set

    /** Wall-clock of the last accepted accessibility event used for package/activity. */
    private val lastEventAtMs = AtomicLong(0L)

    val lastAccessibilityEventTime: Long
        get() = lastEventAtMs.get()

    /**
     * Optional UI probe used by [WaitCondition.TextAppears] to verify text on screen.
     * Wired from [me.rerere.rikkahub.service.SolaceAccessibilityService] when connected.
     */
    @Volatile
    var textProbe: ((query: String, partial: Boolean) -> Boolean)? = null

    private val lastContentEmitAt = AtomicLong(0L)
    private val contentThrottleMs = 200L

    companion object {
        private const val TAG = "A11yEventMgr"

        /** Default TTL for L0 foreground cache before allowing service fallback. */
        const val FOREGROUND_CACHE_TTL_MS: Long = 3_000L

        @Volatile
        var instance: AccessibilityEventManager? = null
            private set
    }

    /** True when package/activity cache is non-blank and within [ttlMs]. */
    fun isForegroundCacheFresh(ttlMs: Long = FOREGROUND_CACHE_TTL_MS): Boolean {
        if (currentPackageName.isBlank() && currentActivityName.isBlank()) return false
        val last = lastEventAtMs.get()
        if (last <= 0L) return false
        return System.currentTimeMillis() - last <= ttlMs
    }

    init {
        instance = this
    }

    fun attachTextProbe(probe: (query: String, partial: Boolean) -> Boolean) {
        textProbe = probe
    }

    fun detachTextProbe() {
        textProbe = null
    }

    /**
     * Entry from [android.accessibilityservice.AccessibilityService.onAccessibilityEvent].
     * Must be cheap and non-blocking.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val converted = convert(event) ?: return

        // Track page context for snapshots / wait conditions.
        var touchedForeground = false
        if (converted.packageName.isNotBlank()) {
            currentPackageName = converted.packageName
            touchedForeground = true
        }
        if (converted.eventType == AgentEvent.PAGE_CHANGED &&
            converted.activityName.isNotBlank()
        ) {
            currentActivityName = converted.activityName
            touchedForeground = true
        }
        if (touchedForeground) {
            lastEventAtMs.set(converted.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis())
        }

        if (converted.eventType == AgentEvent.CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            val last = lastContentEmitAt.get()
            if (now - last < contentThrottleMs) return
            lastContentEmitAt.set(now)
        }

        val ok = eventBus.tryEmit(converted)
        if (!ok) {
            Log.d(TAG, "drop event ${converted.eventType} (bus full)")
        }
        // Level-1 incremental perception (cheap; no full tree / OCR).
        TieredPerceptionEngine.instance?.onAgentEvent(converted)
    }

    /**
     * Convert Android event → AgentEvent. Returns null for unsupported / noisy types.
     */
    fun convert(event: AccessibilityEvent): AgentEvent? {
        val eventType = mapEventType(event.eventType) ?: return null
        val packageName = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()

        val activityName = when (eventType) {
            AgentEvent.PAGE_CHANGED -> {
                if (looksLikeActivityClass(className)) className
                else currentActivityName
            }
            else -> currentActivityName
        }

        val affected = extractAffectedNode(event, className)

        return AgentEvent(
            eventType = eventType,
            packageName = packageName,
            activityName = activityName,
            timestamp = System.currentTimeMillis(),
            affectedNode = affected,
            rawEventType = event.eventType,
        )
    }

    /**
     * Suspend until [condition] is satisfied or [timeoutMs] elapses.
     *
     * For [WaitCondition.TextAppears], checks the live UI probe first and again
     * whenever PAGE/CONTENT events arrive.
     */
    suspend fun waitFor(
        condition: WaitCondition,
        timeoutMs: Long = 5_000L,
        pollMs: Long = 250L,
    ): WaitResult {
        val start = System.currentTimeMillis()
        val timeout = timeoutMs.coerceIn(100L, 60_000L)
        val label = conditionLabel(condition)

        // Immediate satisfaction (e.g. text already visible).
        if (isSatisfiedNow(condition)) {
            return WaitResult.Success(
                event = null,
                matchedBy = "already_met:$label",
                waitedMs = 0L,
            )
        }

        val result = withTimeoutOrNull(timeout) {
            when (condition) {
                is WaitCondition.TextAppears -> waitForText(condition, pollMs)
                is WaitCondition.PageChanged -> {
                    eventBus.events.first { matchesPageChanged(it, condition, requireChange = true) }
                }
                is WaitCondition.EventOfType -> {
                    eventBus.events.first { matchesEventOfType(it, condition) }
                }
                is WaitCondition.Predicate -> {
                    eventBus.events.first { condition.match(it) }
                }
            }
        }

        val waited = System.currentTimeMillis() - start
        return if (result != null) {
            WaitResult.Success(event = result, matchedBy = label, waitedMs = waited)
        } else {
            // Final probe for text (race: appeared after last poll, before timeout return).
            if (condition is WaitCondition.TextAppears && isSatisfiedNow(condition)) {
                WaitResult.Success(event = null, matchedBy = "probe_after_timeout:$label", waitedMs = waited)
            } else {
                WaitResult.Timeout(waitedMs = waited, condition = label)
            }
        }
    }

    /**
     * Fire-and-forget wait on the manager scope (useful from non-suspend tool callbacks).
     * Invokes [onResult] on a background dispatcher.
     */
    fun waitForAsync(
        condition: WaitCondition,
        timeoutMs: Long = 5_000L,
        onResult: (WaitResult) -> Unit,
    ): Job = scope.launch {
        onResult(waitFor(condition, timeoutMs))
    }

    // region internal

    private suspend fun waitForText(
        condition: WaitCondition.TextAppears,
        pollMs: Long,
    ): AgentEvent {
        // Event wakeups + light polling (CONTENT_CHANGED may be throttled / dropped).
        val deferred = kotlinx.coroutines.CompletableDeferred<AgentEvent>()
        var lastEvent: AgentEvent? = null

        fun tryComplete(event: AgentEvent?) {
            if (deferred.isCompleted) return
            if (!textVisible(condition)) return
            deferred.complete(
                event ?: AgentEvent(
                    eventType = AgentEvent.CONTENT_CHANGED,
                    packageName = currentPackageName,
                    activityName = currentActivityName,
                    timestamp = System.currentTimeMillis(),
                    affectedNode = AffectedNode(text = condition.text),
                )
            )
        }

        val eventJob = scope.launch {
            eventBus.events.collect { ev ->
                if (ev.eventType == AgentEvent.PAGE_CHANGED ||
                    ev.eventType == AgentEvent.CONTENT_CHANGED ||
                    ev.eventType == AgentEvent.TEXT_CHANGED
                ) {
                    lastEvent = ev
                    tryComplete(ev)
                }
            }
        }
        val pollJob = scope.launch {
            while (!deferred.isCompleted) {
                tryComplete(lastEvent)
                delay(pollMs.coerceIn(50L, 1_000L))
            }
        }
        try {
            return deferred.await()
        } finally {
            eventJob.cancel()
            pollJob.cancel()
        }
    }

    private fun isSatisfiedNow(condition: WaitCondition): Boolean = when (condition) {
        is WaitCondition.TextAppears -> textVisible(condition)
        is WaitCondition.PageChanged -> {
            // "Page changed" means a future transition; never already met unless targeting
            // a specific page that is already foreground.
            val pkgOk = condition.packageName.isNullOrBlank() ||
                currentPackageName.equals(condition.packageName, ignoreCase = true)
            val actOk = condition.activityName.isNullOrBlank() ||
                currentActivityName.equals(condition.activityName, ignoreCase = true)
            !condition.packageName.isNullOrBlank() && !condition.activityName.isNullOrBlank() &&
                pkgOk && actOk
        }
        is WaitCondition.EventOfType -> false
        is WaitCondition.Predicate -> false
    }

    private fun textVisible(condition: WaitCondition.TextAppears): Boolean {
        if (condition.packageName != null &&
            currentPackageName.isNotBlank() &&
            !currentPackageName.equals(condition.packageName, ignoreCase = true)
        ) {
            return false
        }
        return textProbe?.invoke(condition.text, condition.partial) == true
    }

    private fun matchesPageChanged(
        event: AgentEvent,
        condition: WaitCondition.PageChanged,
        requireChange: Boolean,
    ): Boolean {
        if (event.eventType != AgentEvent.PAGE_CHANGED) return false
        if (!requireChange) return true
        val pkgOk = condition.packageName.isNullOrBlank() ||
            event.packageName.equals(condition.packageName, ignoreCase = true)
        val actOk = condition.activityName.isNullOrBlank() ||
            event.activityName.equals(condition.activityName, ignoreCase = true) ||
            event.activityName.endsWith(condition.activityName!!, ignoreCase = true)
        return pkgOk && actOk
    }

    private fun matchesEventOfType(
        event: AgentEvent,
        condition: WaitCondition.EventOfType,
    ): Boolean {
        if (event.eventType != condition.eventType) return false
        return condition.packageName.isNullOrBlank() ||
            event.packageName.equals(condition.packageName, ignoreCase = true)
    }

    private fun conditionLabel(condition: WaitCondition): String = when (condition) {
        is WaitCondition.TextAppears -> "text=${condition.text}"
        is WaitCondition.PageChanged ->
            "page=${condition.packageName.orEmpty()}/${condition.activityName.orEmpty()}"
        is WaitCondition.EventOfType -> "type=${condition.eventType}"
        is WaitCondition.Predicate -> condition.description
    }

    private fun mapEventType(type: Int): String? = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> AgentEvent.PAGE_CHANGED
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> AgentEvent.CONTENT_CHANGED
        AccessibilityEvent.TYPE_VIEW_CLICKED -> AgentEvent.VIEW_CLICKED
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> AgentEvent.TEXT_CHANGED
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> AgentEvent.VIEW_SCROLLED
        else -> null
    }

    private fun extractAffectedNode(
        event: AccessibilityEvent,
        fallbackClassName: String,
    ): AffectedNode? {
        val textFromEvent = event.text
            ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            ?.joinToString(" ")
            .orEmpty()
        var text = textFromEvent
        var contentDescription = event.contentDescription?.toString().orEmpty()
        var resolvedViewId = ""
        var resolvedClass = fallbackClassName

        val source = runCatching { event.source }.getOrNull()
        if (source != null) {
            try {
                if (text.isBlank()) text = source.text?.toString().orEmpty()
                if (contentDescription.isBlank()) {
                    contentDescription = source.contentDescription?.toString().orEmpty()
                }
                resolvedViewId = source.viewIdResourceName.orEmpty()
                if (resolvedClass.isBlank()) {
                    resolvedClass = source.className?.toString().orEmpty()
                }
            } finally {
                runCatching { source.recycle() }
            }
        }

        if (text.isBlank() && contentDescription.isBlank() &&
            resolvedViewId.isBlank() && resolvedClass.isBlank()
        ) {
            return null
        }
        return AffectedNode(
            text = text.take(200),
            contentDescription = contentDescription.take(200),
            className = resolvedClass,
            viewId = resolvedViewId,
        )
    }

    private fun looksLikeActivityClass(className: String): Boolean {
        if (!className.contains('.')) return false
        if (className.startsWith("android.widget.") ||
            className.startsWith("android.view.") ||
            className.startsWith("androidx.") ||
            className.startsWith("com.android.internal.")
        ) {
            return false
        }
        val simple = className.substringAfterLast('.')
        if (simple.endsWith("Layout") || simple.endsWith("View") || simple.startsWith("View")) {
            return false
        }
        return true
    }

    // endregion
}
