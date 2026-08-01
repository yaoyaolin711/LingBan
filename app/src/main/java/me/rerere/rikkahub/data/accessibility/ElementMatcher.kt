package me.rerere.rikkahub.data.accessibility

import me.rerere.rikkahub.data.ocr.ScreenOcrBlock

/**
 * Merges Accessibility nodes with OCR (and optional Vision) regions.
 *
 * Priority rules:
 * 1. Accessibility is primary (structure, clickability, viewId)
 * 2. OCR supplements missing / weak text
 * 3. Vision is fallback for regions covered by neither
 */
object ElementMatcher {

    /** Minimum IoU to attach OCR text onto an accessibility node. */
    const val IOU_THRESHOLD = 0.15f

    /** Max center distance (px) as secondary match when IoU is low. */
    const val CENTER_DISTANCE_PX = 72f

    fun build(
        snapshot: UISnapshot,
        ocrBlocks: List<ScreenOcrBlock> = emptyList(),
        visualElements: List<UIElement> = emptyList(),
        hasScreenshot: Boolean = false,
        ocrEngine: String? = null,
    ): UnifiedObservation {
        val a11y = snapshot.flattenNodes()
            .map { it.toElement(UIObservation.SOURCE_ACCESSIBILITY) }
            .filter { usefulAccessibility(it) }
        val ocr = ocrBlocks.mapIndexed { i, b -> b.toUIElement(i) }
            .filter { it.text.isNotBlank() }
        val vision = visualElements.map { it.copy(source = UIObservation.SOURCE_VISION) }
        val fused = match(a11y, ocr, vision)
        return UnifiedObservation(
            accessibilityElements = a11y,
            ocrElements = ocr,
            visualElements = vision,
            fusedElements = fused,
            page = snapshot.page,
            packageName = snapshot.packageName,
            windowTitle = snapshot.windowTitle,
            timestamp = snapshot.timestamp,
            screenWidth = snapshot.screenWidth,
            screenHeight = snapshot.screenHeight,
            tree = snapshot.root,
            truncated = snapshot.truncated,
            hasScreenshot = hasScreenshot,
            ocrEngine = ocrEngine,
        )
    }

    /**
     * Core merge: Accessibility-first, OCR enrich, Vision fallback.
     */
    fun match(
        accessibilityElements: List<UIElement>,
        ocrElements: List<UIElement>,
        visualElements: List<UIElement> = emptyList(),
    ): List<FusedUiElement> {
        val usedOcr = HashSet<String>()
        val usedVision = HashSet<String>()
        val fused = ArrayList<FusedUiElement>(accessibilityElements.size + ocrElements.size)

        // 1) Accessibility primary
        for (node in accessibilityElements) {
            val ocrMatch = findBestOcr(node, ocrElements, usedOcr)
            val text = resolveText(node, ocrMatch)
            val sources = buildList {
                add(UIObservation.SOURCE_ACCESSIBILITY)
                if (ocrMatch != null) add(UIObservation.SOURCE_OCR)
            }
            if (ocrMatch != null) usedOcr += ocrMatch.id

            val type = inferElementType(node.className, node.editable, node.clickable)
            val actionable = node.clickable || node.editable || node.checkable ||
                node.scrollable || type == FusedUiElement.TYPE_BUTTON ||
                type == FusedUiElement.TYPE_EDIT_TEXT

            fused += FusedUiElement(
                id = "fused_${node.id}",
                type = type,
                text = text,
                actionable = actionable,
                bounds = node.bounds,
                x = node.x,
                y = node.y,
                sources = sources,
                accessibilityId = node.id,
                ocrId = ocrMatch?.id,
                viewId = node.viewId,
                className = node.className,
                contentDescription = node.contentDescription,
                enabled = node.enabled,
                editable = node.editable,
                confidence = if (ocrMatch != null) 0.95f else 0.9f,
            )
        }

        // 2) OCR supplement — unmatched text regions (e.g. canvas / WebView)
        for (ocr in ocrElements) {
            if (ocr.id in usedOcr) continue
            fused += FusedUiElement(
                id = "fused_${ocr.id}",
                type = FusedUiElement.TYPE_TEXT,
                text = ocr.text,
                actionable = true, // OCR-only can still be tapped via x,y
                bounds = ocr.bounds,
                x = ocr.x,
                y = ocr.y,
                sources = listOf(UIObservation.SOURCE_OCR),
                ocrId = ocr.id,
                confidence = 0.7f,
            )
        }

        // 3) Vision fallback — only if not covered by a11y/ocr bounds
        for (vis in visualElements) {
            if (vis.id in usedVision) continue
            val covered = fused.any { f ->
                f.bounds.iou(vis.bounds) >= IOU_THRESHOLD ||
                    f.bounds.containsPoint(vis.x, vis.y)
            }
            if (covered) continue
            usedVision += vis.id
            fused += FusedUiElement(
                id = "fused_${vis.id}",
                type = inferElementType(vis.className, vis.editable, vis.clickable)
                    .let { if (it == FusedUiElement.TYPE_UNKNOWN) FusedUiElement.TYPE_IMAGE else it },
                text = vis.text.ifBlank { vis.contentDescription },
                actionable = vis.clickable || vis.editable || vis.text.isNotBlank(),
                bounds = vis.bounds,
                x = vis.x,
                y = vis.y,
                sources = listOf(UIObservation.SOURCE_VISION),
                visionId = vis.id,
                className = vis.className,
                confidence = 0.5f,
            )
        }

        return fused.sortedWith(
            compareByDescending<FusedUiElement> { it.actionable }
                .thenByDescending { sourceRank(it.sources) }
                .thenByDescending { it.confidence }
        )
    }

    private fun usefulAccessibility(el: UIElement): Boolean {
        if (el.bounds.isEmpty) return false
        return el.text.isNotBlank() ||
            el.contentDescription.isNotBlank() ||
            el.clickable ||
            el.editable ||
            el.scrollable ||
            el.checkable ||
            el.focused ||
            el.viewId.isNotBlank()
    }

    private fun resolveText(node: UIElement, ocr: UIElement?): String {
        val a11yText = node.text.ifBlank { node.contentDescription }
        return when {
            a11yText.isNotBlank() -> a11yText
            ocr != null && ocr.text.isNotBlank() -> ocr.text
            else -> ""
        }
    }

    private fun findBestOcr(
        node: UIElement,
        ocrElements: List<UIElement>,
        used: Set<String>,
    ): UIElement? {
        var best: UIElement? = null
        var bestScore = 0f
        for (ocr in ocrElements) {
            if (ocr.id in used) continue
            val iou = node.bounds.iou(ocr.bounds)
            val centerHit = node.bounds.containsPoint(ocr.x, ocr.y) ||
                ocr.bounds.containsPoint(node.x, node.y)
            val dist = node.bounds.centerDistance(ocr.bounds)
            val score = when {
                iou >= IOU_THRESHOLD -> 2f + iou
                centerHit -> 1.5f
                dist <= CENTER_DISTANCE_PX -> 1f - (dist / CENTER_DISTANCE_PX) * 0.5f
                else -> 0f
            }
            if (score > bestScore) {
                bestScore = score
                best = ocr
            }
        }
        return best
    }

    private fun sourceRank(sources: List<String>): Int {
        var rank = 0
        if (UIObservation.SOURCE_ACCESSIBILITY in sources) rank += 4
        if (UIObservation.SOURCE_OCR in sources) rank += 2
        if (UIObservation.SOURCE_VISION in sources) rank += 1
        return rank
    }
}
