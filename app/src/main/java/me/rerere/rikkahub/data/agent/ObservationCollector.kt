package me.rerere.rikkahub.data.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.accessibility.AccessibilityEventManager
import me.rerere.rikkahub.data.accessibility.IncrementalUISnapshot
import me.rerere.rikkahub.data.accessibility.ObservationCache
import me.rerere.rikkahub.data.accessibility.PerceptionLevel
import me.rerere.rikkahub.data.accessibility.PerceptionRequest
import me.rerere.rikkahub.data.accessibility.TieredPerceptionEngine
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.accessibility.UnifiedObservation
import me.rerere.rikkahub.service.SolaceAccessibilityService

/** L0 foreground read — normally backed by [AccessibilityEventManager] cache. */
interface ForegroundCacheSource {
    fun packageName(): String
    fun activityName(): String
    fun isFresh(ttlMs: Long): Boolean
}

fun ForegroundCacheSource(
    packageName: () -> String,
    activityName: () -> String,
    isFresh: (Long) -> Boolean,
): ForegroundCacheSource = object : ForegroundCacheSource {
    override fun packageName(): String = packageName()
    override fun activityName(): String = activityName()
    override fun isFresh(ttlMs: Long): Boolean = isFresh(ttlMs)
}

fun AccessibilityEventManager.asForegroundCacheSource(): ForegroundCacheSource =
    ForegroundCacheSource(
        packageName = { currentPackageName },
        activityName = { currentActivityName },
        isFresh = { ttl -> isForegroundCacheFresh(ttl) },
    )

/**
 * Result of a single observation pass through [ObservationCollector].
 * [snapshot] is present for L1/L2 (and cache hits); L0 usually leaves it null.
 */
data class ObservationResult(
    val compact: CompactObservation,
    val snapshot: UISnapshot? = null,
)

/**
 * Tiered phone-state observation — **sole** observation entry for AgentRuntime.
 *
 * L0: event-manager cache (package/activity)
 * L1/L2: [TieredPerceptionEngine] + shared [ObservationCache] (no second cache)
 */
class ObservationCollector(
    private val foregroundSource: () -> ForegroundCacheSource? = {
        AccessibilityEventManager.instance?.asForegroundCacheSource()
    },
    private val lightSnapshot: () -> UISnapshot = {
        SolaceAccessibilityService.instance?.captureUISnapshot(maxNodes = 48)
            ?: UISnapshot(page = "", packageName = "", timestamp = System.currentTimeMillis())
    },
    private val fullSnapshot: () -> UISnapshot = {
        SolaceAccessibilityService.instance?.captureUISnapshot(maxNodes = 120)
            ?: UISnapshot(page = "", packageName = "", timestamp = System.currentTimeMillis())
    },
    private val cacheTtlMs: Long = AccessibilityEventManager.FOREGROUND_CACHE_TTL_MS,
    /**
     * Shared cache. Prefer [TieredPerceptionEngine.observationCache] when available
     * so Runtime / Tiered share one ObservationCache.
     */
    private val observationCacheProvider: () -> ObservationCache? = {
        TieredPerceptionEngine.instance?.observationCache
    },
) {
    companion object {
        private const val TAG = "ObsCollector"
    }

    data class Request(
        val maxLevel: ObservationLevel = ObservationLevel.L0,
        val previousPackage: String = "",
        val previousActivity: String = "",
        val afterAction: Boolean = false,
        val complexTask: Boolean = false,
    )

    /** Fallback only when TieredPerception is not wired (unit tests). */
    private val localFallbackCache by lazy { ObservationCache(maxSize = 16) }

    private fun sharedCache(): ObservationCache =
        observationCacheProvider() ?: localFallbackCache

    suspend fun collect(request: Request = Request()): ObservationResult =
        withContext(Dispatchers.Default) {
            val l0 = collectL0()
            val pageChanged = detectPageChange(request, l0.compact)
            val target = resolveLevel(request, pageChanged)
            when (target) {
                ObservationLevel.L0 -> l0
                ObservationLevel.L1 -> collectL1(l0.compact, afterAction = request.afterAction)
                ObservationLevel.L2 -> collectL2(l0.compact, afterAction = request.afterAction)
            }
        }

    /** After-action: L0 by default; escalate to L1 only if package/activity changed. */
    suspend fun collectAfterAction(
        previousPackage: String,
        previousActivity: String,
        complexTask: Boolean = false,
    ): ObservationResult = collect(
        Request(
            maxLevel = if (complexTask) ObservationLevel.L1 else ObservationLevel.L0,
            previousPackage = previousPackage,
            previousActivity = previousActivity,
            afterAction = true,
            complexTask = complexTask,
        )
    )

    private fun detectPageChange(request: Request, l0: CompactObservation): Boolean {
        if (request.previousPackage.isBlank() || l0.packageName.isBlank()) return false
        if (!l0.packageName.equals(request.previousPackage, ignoreCase = true)) return true
        if (l0.activityName.isBlank() || request.previousActivity.isBlank()) return false
        return !l0.activityName.equals(request.previousActivity, ignoreCase = true)
    }

    private fun resolveLevel(request: Request, pageChanged: Boolean): ObservationLevel {
        if (pageChanged) {
            return if (request.complexTask && request.maxLevel >= ObservationLevel.L2) {
                ObservationLevel.L2
            } else {
                ObservationLevel.L1
            }
        }
        return request.maxLevel
    }

    private fun collectL0(): ObservationResult {
        val src = foregroundSource()
        val fresh = src?.isFresh(cacheTtlMs) == true
        var pkg = src?.packageName().orEmpty()
        var activity = src?.activityName().orEmpty()
        var usedFallback = false

        if (!fresh || (pkg.isBlank() && activity.isBlank())) {
            val rootPkg = runCatching {
                SolaceAccessibilityService.instance?.rootInActiveWindow
                    ?.packageName?.toString().orEmpty()
            }.getOrDefault("")
            if (rootPkg.isNotBlank()) {
                pkg = rootPkg
                usedFallback = true
                Log.d(TAG, "L0 fallback rootInActiveWindow pkg=$pkg")
            }
        }

        return ObservationResult(
            compact = CompactObservation(
                level = ObservationLevel.L0,
                packageName = pkg,
                activityName = activity,
                usedFallback = usedFallback,
                timestamp = System.currentTimeMillis(),
            ),
            snapshot = null,
        )
    }

    private suspend fun collectL1(l0: CompactObservation, afterAction: Boolean): ObservationResult {
        val tiered = TieredPerceptionEngine.instance
        if (tiered != null) {
            val perceived = runCatching {
                tiered.observe(
                    PerceptionRequest(
                        maxLevel = PerceptionLevel.L1_A11Y,
                        afterAction = afterAction,
                    )
                )
            }.getOrNull()
            if (perceived != null) {
                val snap = perceived.snapshot
                val pkg = snap?.packageName?.ifBlank { null }
                    ?: perceived.observation.packageName.ifBlank { l0.packageName }
                val page = snap?.page?.ifBlank { null }
                    ?: perceived.observation.page.ifBlank { l0.activityName }
                val compact = CompactObservation(
                    level = ObservationLevel.L1,
                    packageName = pkg,
                    activityName = page,
                    interactiveCount = snap?.flattenNodes()?.count {
                        it.clickable || it.editable || it.checkable
                    } ?: perceived.observation.fusedElements.size,
                    keyTexts = perceived.observation.fusedElements
                        .map { it.text.take(24) }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(8),
                    treeHash = perceived.treeHash,
                    fromCache = perceived.fromCache,
                    usedFallback = l0.usedFallback,
                )
                return ObservationResult(compact = compact, snapshot = snap)
            }
        }

        // Fallback: light dump + shared ObservationCache (same instance as Tiered when wired).
        val cache = sharedCache()
        val snap = runCatching { lightSnapshot() }.getOrElse {
            Log.w(TAG, "L1 lightSnapshot failed", it)
            return ObservationResult(l0.copy(level = ObservationLevel.L1), snapshot = null)
        }
        val pkg = snap.packageName.ifBlank { l0.packageName }
        val page = snap.page.ifBlank { l0.activityName }
        val hash = ObservationCache.hashSnapshot(snap)
        cache.getIfUnchanged(pkg, page, hash)?.let { hit ->
            val compact = summaryFromSnapshot(
                snap = hit.snapshot ?: snap,
                l0 = l0,
                level = ObservationLevel.L1,
                treeHash = hit.treeHash,
                fromCache = true,
            )
            return ObservationResult(compact = compact, snapshot = hit.snapshot ?: snap)
        }
        val compact = summaryFromSnapshot(snap, l0, ObservationLevel.L1, hash, fromCache = false)
        putShared(cache, snap, hash, PerceptionLevel.L1_A11Y)
        return ObservationResult(compact = compact, snapshot = snap)
    }

    private suspend fun collectL2(l0: CompactObservation, afterAction: Boolean): ObservationResult {
        val tiered = TieredPerceptionEngine.instance
        if (tiered != null) {
            val perceived = runCatching {
                tiered.observe(
                    PerceptionRequest(
                        maxLevel = PerceptionLevel.L1_A11Y, // Runtime RULE: no OCR escalate here
                        afterAction = afterAction,
                    )
                )
            }.getOrNull()
            if (perceived?.snapshot != null) {
                val snap = perceived.snapshot
                val compact = summaryFromSnapshot(
                    snap = snap,
                    l0 = l0,
                    level = ObservationLevel.L2,
                    treeHash = perceived.treeHash,
                    fromCache = perceived.fromCache,
                )
                return ObservationResult(compact = compact, snapshot = snap)
            }
        }

        val cache = sharedCache()
        val snap = runCatching { fullSnapshot() }.getOrElse {
            Log.w(TAG, "L2 fullSnapshot failed", it)
            return collectL1(l0, afterAction)
        }
        val pkg = snap.packageName.ifBlank { l0.packageName }
        val page = snap.page.ifBlank { l0.activityName }
        val hash = ObservationCache.hashSnapshot(snap)
        cache.getIfUnchanged(pkg, page, hash)?.let { hit ->
            return ObservationResult(
                compact = summaryFromSnapshot(
                    hit.snapshot ?: snap, l0, ObservationLevel.L2, hit.treeHash, fromCache = true,
                ),
                snapshot = hit.snapshot ?: snap,
            )
        }
        val compact = summaryFromSnapshot(snap, l0, ObservationLevel.L2, hash, fromCache = false)
        putShared(cache, snap, hash, PerceptionLevel.L1_A11Y)
        return ObservationResult(compact = compact, snapshot = snap)
    }

    private fun summaryFromSnapshot(
        snap: UISnapshot,
        l0: CompactObservation,
        level: ObservationLevel,
        treeHash: String,
        fromCache: Boolean,
    ): CompactObservation {
        val pkg = snap.packageName.ifBlank { l0.packageName }
        val page = snap.page.ifBlank { l0.activityName }
        val interactive = snap.flattenNodes().count { it.clickable || it.editable || it.checkable }
        val keyTexts = ObservationCache.sampleFromSnapshot(snap)
            .map { sample ->
                sample.split('|').getOrNull(1)?.takeIf { it.isNotBlank() } ?: sample.take(24)
            }
            .filter { it.isNotBlank() }
            .distinct()
            .take(if (level == ObservationLevel.L2) 12 else 8)
        return CompactObservation(
            level = level,
            packageName = pkg,
            activityName = page,
            interactiveCount = interactive,
            keyTexts = keyTexts,
            treeHash = treeHash,
            fromCache = fromCache,
            usedFallback = l0.usedFallback,
        )
    }

    private fun putShared(
        cache: ObservationCache,
        snap: UISnapshot,
        hash: String,
        level: PerceptionLevel,
    ) {
        val pkg = snap.packageName
        val page = snap.page
        if (pkg.isBlank() || hash.isBlank()) return
        cache.put(
            ObservationCache.Entry(
                packageName = pkg,
                page = page,
                treeHash = hash,
                level = level,
                incremental = IncrementalUISnapshot(
                    packageName = pkg,
                    page = page,
                    treeHash = hash,
                ),
                snapshot = snap,
                observation = UnifiedObservation.fromSnapshot(snap),
            )
        )
    }

    /** Clears fallback cache only (Tiered-owned ObservationCache is not wiped). */
    fun clearLightCache() {
        if (observationCacheProvider() == null) {
            localFallbackCache.clear()
        }
    }
}
