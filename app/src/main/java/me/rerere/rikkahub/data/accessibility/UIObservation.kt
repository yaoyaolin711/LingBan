package me.rerere.rikkahub.data.accessibility

import kotlinx.serialization.Serializable

/**
 * Unified perception payload for Agent tools.
 * Sources can be fused later: accessibility / ocr / vision.
 */
@Serializable
data class UIObservation(
    val source: String,
    val elements: List<UIElement> = emptyList(),
    val page: String = "",
    val packageName: String = "",
    val timestamp: Long = 0L,
    val windowTitle: String = "",
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    /** Optional full hierarchy when [source] includes accessibility. */
    val tree: UiTreeNode? = null,
    val truncated: Boolean = false,
) {
    companion object {
        const val SOURCE_ACCESSIBILITY = "accessibility"
        const val SOURCE_OCR = "ocr"
        const val SOURCE_VISION = "vision"
        const val SOURCE_FUSED = "fused"

        fun fromSnapshot(
            snapshot: UISnapshot,
            source: String = SOURCE_ACCESSIBILITY,
        ): UIObservation {
            val flat = snapshot.flattenNodes()
            return UIObservation(
                source = source,
                elements = flat.map { it.toElement(source) },
                page = snapshot.page,
                packageName = snapshot.packageName,
                timestamp = snapshot.timestamp,
                windowTitle = snapshot.windowTitle,
                screenWidth = snapshot.screenWidth,
                screenHeight = snapshot.screenHeight,
                tree = snapshot.root,
                truncated = snapshot.truncated,
            )
        }

        /**
         * Build an OCR-only observation (for future fusion with accessibility).
         */
        fun fromOcrElements(
            elements: List<UIElement>,
            page: String = "",
            packageName: String = "",
            timestamp: Long = System.currentTimeMillis(),
            screenWidth: Int = 0,
            screenHeight: Int = 0,
        ): UIObservation = UIObservation(
            source = SOURCE_OCR,
            elements = elements.map { it.copy(source = SOURCE_OCR) },
            page = page,
            packageName = packageName,
            timestamp = timestamp,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )

        /**
         * Merge accessibility + OCR into a fused observation via [ElementMatcher].
         * Prefer [UnifiedObservation] for Planner; this keeps the legacy flat [elements] list.
         */
        fun fuse(accessibility: UIObservation, ocr: UIObservation): UIObservation {
            val fused = ElementMatcher.match(
                accessibilityElements = accessibility.elements,
                ocrElements = ocr.elements.map { it.copy(source = SOURCE_OCR) },
                visualElements = emptyList(),
            )
            return UIObservation(
                source = SOURCE_FUSED,
                elements = fused.map { it.toUIElement() },
                page = accessibility.page.ifBlank { ocr.page },
                packageName = accessibility.packageName.ifBlank { ocr.packageName },
                timestamp = maxOf(accessibility.timestamp, ocr.timestamp),
                windowTitle = accessibility.windowTitle.ifBlank { ocr.windowTitle },
                screenWidth = accessibility.screenWidth.takeIf { it > 0 } ?: ocr.screenWidth,
                screenHeight = accessibility.screenHeight.takeIf { it > 0 } ?: ocr.screenHeight,
                tree = accessibility.tree ?: ocr.tree,
                truncated = accessibility.truncated || ocr.truncated,
            )
        }
    }
}

/**
 * Flat actionable element used by Agent reasoning across modalities.
 */
@Serializable
data class UIElement(
    val id: String,
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val packageName: String = "",
    val viewId: String = "",
    val source: String = UIObservation.SOURCE_ACCESSIBILITY,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val focused: Boolean = false,
    val selected: Boolean = false,
    val bounds: UiBounds = UiBounds.EMPTY,
    val parentNodeId: String? = null,
    val x: Int = 0,
    val y: Int = 0,
)

fun UiTreeNode.toElement(
    source: String = UIObservation.SOURCE_ACCESSIBILITY,
): UIElement = UIElement(
    id = nodeId,
    text = text,
    contentDescription = contentDescription,
    className = className,
    packageName = packageName,
    viewId = viewId,
    source = source,
    clickable = clickable,
    enabled = enabled,
    editable = editable,
    scrollable = scrollable,
    checkable = checkable,
    checked = checked,
    focused = focused,
    selected = selected,
    bounds = bounds,
    parentNodeId = parentNodeId,
    x = bounds.centerX,
    y = bounds.centerY,
)

fun FusedUiElement.toUIElement(): UIElement = UIElement(
    id = id,
    text = text,
    contentDescription = contentDescription,
    className = className,
    viewId = viewId,
    source = sources.firstOrNull() ?: UIObservation.SOURCE_FUSED,
    clickable = actionable && type != FusedUiElement.TYPE_EDIT_TEXT,
    enabled = enabled,
    editable = editable || type == FusedUiElement.TYPE_EDIT_TEXT,
    bounds = bounds,
    x = x,
    y = y,
)
