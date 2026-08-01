package me.rerere.rikkahub.data.accessibility

import android.util.Log
import me.rerere.rikkahub.data.ocr.ScreenOcrBlock
import me.rerere.rikkahub.data.agent.trace.AgentTrace
import me.rerere.rikkahub.data.agent.trace.AgentTracer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tiered perception engine.
 *
 * Level 1: Accessibility events → incremental node updates (default)
 * Level 2: OCR only when L1 nodes are insufficient
 * Level 3: Vision only when OCR still cannot explain the UI
 *
 * Never runs screenshot+OCR on [PerceptionRequest.afterAction].
 */
class TieredPerceptionEngine(
    private val cache: ObservationCache = ObservationCache(),
    private val lightSnapshot: () -> UISnapshot,
    private val fullSnapshot: () -> UISnapshot = lightSnapshot,
    /** Optional OCR — invoked only at L2. Must be cheap to leave null. */
    private val ocrProvider: (suspend (UISnapshot) -> Pair<String, List<ScreenOcrBlock>>?)? = null,
    /** Optional vision hints — invoked only at L3. */
    private val visionProvider: (suspend (UISnapshot) -> List<UIElement>)? = null,
) {
    companion object {
        private const val TAG = "TieredPerception"

        @Volatile
        var instance: TieredPerceptionEngine? = null
            private set
    }

    private val deltaSeq = AtomicInteger(0)

    @Volatile
    private var lastIncremental: IncrementalUISnapshot = IncrementalUISnapshot()

    @Volatile
    private var lastPackage: String = ""

    @Volatile
    private var lastPage: String = ""

    init {
        instance = this
    }

    val observationCache: ObservationCache get() = cache

    /**
     * Level 1 hook — called from accessibility event path (must stay cheap).
     */
    fun onAgentEvent(event: AgentEvent) {
        if (event.packageName.isNotBlank()) lastPackage = event.packageName
        if (event.activityName.isNotBlank()) lastPage = event.activityName

        val changed = ChangedNode.fromAffected(event, deltaSeq.incrementAndGet())
        val merged = ArrayList<ChangedNode>(8)
        if (changed != null) merged += changed
        // Keep a short rolling window of recent deltas (not a full tree).
        lastIncremental.changedNodes.takeLast(7).forEach { prev ->
            if (merged.none { it.text == prev.text && it.className == prev.className }) {
                merged += prev
            }
        }

        val hash = ObservationCache.computeTreeHash(
            packageName = lastPackage,
            page = lastPage,
            nodeCount = merged.size,
            sample = ObservationCache.sampleFromIncremental(
                IncrementalUISnapshot(changedNodes = merged),
            ),
        )
        lastIncremental = IncrementalUISnapshot(
            packageName = lastPackage,
            page = lastPage,
            timestamp = event.timestamp,
            changedNodes = merged,
            treeHash = hash,
            previousTreeHash = lastIncremental.treeHash,
            eventType = event.eventType,
        )
    }

    /**
     * Run tiered observe. Default max level L1; escalate only when allowed.
     */
    suspend fun observe(request: PerceptionRequest = PerceptionRequest()): PerceptionResult {
        val effectiveMax = if (request.afterAction) PerceptionLevel.L1_A11Y else request.maxLevel

        // --- Cache check via light fingerprint ---
        val light = runCatching { lightSnapshot() }.getOrElse {
            UISnapshot(page = lastPage, packageName = lastPackage, timestamp = System.currentTimeMillis())
        }
        val pkg = light.packageName.ifBlank { lastPackage }
        val page = light.page.ifBlank { lastPage }
        val hash = if (light.root != null) {
            ObservationCache.hashSnapshot(light)
        } else {
            lastIncremental.treeHash.ifBlank {
                ObservationCache.computeTreeHash(pkg, page, lastIncremental.changeCount, emptyList())
            }
        }

        if (!request.forceRefresh) {
            cache.getIfUnchanged(pkg, page, hash)?.let { hit ->
                Log.d(TAG, "cache hit $pkg/$page hash=$hash level=${hit.level}")
                return PerceptionResult(
                    level = hit.level,
                    fromCache = true,
                    incremental = hit.incremental,
                    snapshot = hit.snapshot,
                    observation = hit.observation,
                    treeHash = hash,
                    reason = "cache_hit",
                )
            }
        }

        // --- Level 1: incremental + optional light a11y ---
        val l1 = buildL1(light, pkg, page, hash)
        if (effectiveMax == PerceptionLevel.L1_A11Y || request.afterAction || isSufficient(l1.observation, request)) {
            return remember(l1.copy(reason = if (request.afterAction) "after_action_l1" else "l1_ok"))
        }

        // --- Level 2: OCR supplement ---
        val ocrFn = ocrProvider
        if (effectiveMax >= PerceptionLevel.L2_OCR && ocrFn != null) {
            val snap = light.takeIf { it.root != null } ?: runCatching { fullSnapshot() }.getOrNull() ?: light
            val tracer = AgentTracer.instance
            val ocr = try {
                if (tracer != null) {
                    tracer.measureSuspend(AgentTrace.OCR) { ocrFn.invoke(snap) }
                } else {
                    ocrFn.invoke(snap)
                }
            } catch (_: Exception) {
                null
            }
            if (ocr != null) {
                val (engine, blocks) = ocr
                val obs = UnifiedObservation.fromModalities(
                    snapshot = snap,
                    ocrBlocks = blocks,
                    hasScreenshot = false,
                    ocrEngine = engine,
                )
                val inc = mergeOcrIntoIncremental(l1.incremental, blocks, hash)
                val result = PerceptionResult(
                    level = PerceptionLevel.L2_OCR,
                    fromCache = false,
                    incremental = inc,
                    snapshot = snap,
                    observation = obs,
                    treeHash = hash,
                    reason = "l2_ocr",
                )
                if (effectiveMax == PerceptionLevel.L2_OCR || isSufficient(obs, request)) {
                    return remember(result)
                }

                // --- Level 3: Vision fallback ---
                val visionFn = visionProvider
                if (effectiveMax >= PerceptionLevel.L3_VISION && visionFn != null) {
                    val visuals = try {
                        visionFn.invoke(snap)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (visuals.isNotEmpty()) {
                        val fused = UnifiedObservation.fromModalities(
                            snapshot = snap,
                            ocrBlocks = blocks,
                            visualElements = visuals,
                            hasScreenshot = true,
                            ocrEngine = engine,
                        )
                        return remember(
                            PerceptionResult(
                                level = PerceptionLevel.L3_VISION,
                                fromCache = false,
                                incremental = inc,
                                snapshot = snap,
                                observation = fused,
                                treeHash = hash,
                                reason = "l3_vision",
                            )
                        )
                    }
                }
                return remember(result)
            }
        }

        return remember(l1.copy(reason = "l1_fallback"))
    }

    fun lastIncrementalSnapshot(): IncrementalUISnapshot = lastIncremental

    private fun buildL1(
        light: UISnapshot,
        pkg: String,
        page: String,
        hash: String,
    ): PerceptionResult {
        val inc = if (lastIncremental.packageName == pkg || lastIncremental.page == page) {
            lastIncremental.copy(
                packageName = pkg,
                page = page,
                treeHash = hash,
                timestamp = System.currentTimeMillis(),
            )
        } else {
            IncrementalUISnapshot(
                packageName = pkg,
                page = page,
                treeHash = hash,
                changedNodes = lastIncremental.changedNodes,
            )
        }

        val observation = if (light.root != null) {
            UnifiedObservation.fromSnapshot(light)
        } else {
            // Build a minimal observation from deltas only — no full tree.
            val elements = inc.changedNodes.map {
                UIElement(
                    id = it.nodeId,
                    text = it.text,
                    contentDescription = it.contentDescription,
                    className = it.className,
                    viewId = it.viewId,
                    clickable = it.clickable,
                    editable = it.editable,
                    bounds = it.bounds,
                    x = it.x,
                    y = it.y,
                    source = UIObservation.SOURCE_ACCESSIBILITY,
                )
            }
            val fused = ElementMatcher.match(elements, emptyList(), emptyList())
            UnifiedObservation(
                accessibilityElements = elements,
                fusedElements = fused,
                page = page,
                packageName = pkg,
                timestamp = System.currentTimeMillis(),
            )
        }

        return PerceptionResult(
            level = PerceptionLevel.L1_A11Y,
            fromCache = false,
            incremental = inc,
            snapshot = light.takeIf { it.root != null },
            observation = observation,
            treeHash = hash,
            reason = "l1",
        )
    }

    private fun isSufficient(obs: UnifiedObservation, request: PerceptionRequest): Boolean {
        val useful = obs.fusedElements.count {
            it.actionable || it.text.isNotBlank()
        }
        // Also accept rich a11y lists
        val a11yUseful = obs.accessibilityElements.count {
            it.clickable || it.editable || it.text.isNotBlank()
        }
        return maxOf(useful, a11yUseful) >= request.minUsefulNodes
    }

    private fun mergeOcrIntoIncremental(
        base: IncrementalUISnapshot,
        blocks: List<ScreenOcrBlock>,
        hash: String,
    ): IncrementalUISnapshot {
        val extras = blocks.take(10).mapIndexed { i, b ->
            val box = UiBounds.parseCompact(b.bounds) ?: UiBounds.EMPTY
            ChangedNode(
                nodeId = "ocr_delta_$i",
                text = b.text,
                actionable = true,
                bounds = box,
                x = b.x,
                y = b.y,
                sourceEvent = "OCR",
            )
        }
        return base.copy(
            changedNodes = (base.changedNodes + extras).distinctBy { it.text to it.x },
            treeHash = hash,
            timestamp = System.currentTimeMillis(),
            eventType = "OCR",
        )
    }

    private fun remember(result: PerceptionResult): PerceptionResult {
        lastIncremental = result.incremental
        lastPackage = result.observation.packageName.ifBlank { lastPackage }
        lastPage = result.observation.page.ifBlank { lastPage }
        cache.put(
            ObservationCache.Entry(
                packageName = result.observation.packageName.ifBlank { lastPackage },
                page = result.observation.page.ifBlank { lastPage },
                treeHash = result.treeHash,
                level = result.level,
                incremental = result.incremental,
                snapshot = result.snapshot,
                observation = result.observation,
            )
        )
        Log.d(TAG, "observe level=${result.level} cache=${result.fromCache} reason=${result.reason}")
        return result
    }
}
