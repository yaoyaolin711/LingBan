package me.rerere.rikkahub.data.accessibility

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.ocr.ScreenOcrBlock

/**
 * Multimodal UI understanding payload for Agent Planner.
 *
 * Priority: Accessibility > OCR > Vision (fallback).
 * [fusedElements] is produced by [ElementMatcher] for planning.
 */
@Serializable
data class UnifiedObservation(
    val accessibilityElements: List<UIElement> = emptyList(),
    val ocrElements: List<UIElement> = emptyList(),
    val visualElements: List<UIElement> = emptyList(),
    /** Matcher output: actionable fused nodes for Planner. */
    val fusedElements: List<FusedUiElement> = emptyList(),
    val page: String = "",
    val packageName: String = "",
    val windowTitle: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val tree: UiTreeNode? = null,
    val truncated: Boolean = false,
    val hasScreenshot: Boolean = false,
    val ocrEngine: String? = null,
) {
    val actionableElements: List<FusedUiElement>
        get() = fusedElements.filter { it.actionable }

    companion object {
        fun fromSnapshot(snapshot: UISnapshot): UnifiedObservation =
            ElementMatcher.build(
                snapshot = snapshot,
                ocrBlocks = emptyList(),
                visualElements = emptyList(),
            )

        fun fromModalities(
            snapshot: UISnapshot,
            ocrBlocks: List<ScreenOcrBlock> = emptyList(),
            visualElements: List<UIElement> = emptyList(),
            hasScreenshot: Boolean = false,
            ocrEngine: String? = null,
        ): UnifiedObservation = ElementMatcher.build(
            snapshot = snapshot,
            ocrBlocks = ocrBlocks,
            visualElements = visualElements,
            hasScreenshot = hasScreenshot,
            ocrEngine = ocrEngine,
        )
    }
}

/**
 * Planner-facing fused UI element.
 *
 * Example: Accessibility Button + OCR "发送" → type=button, text=发送, actionable=true
 */
@Serializable
data class FusedUiElement(
    val id: String,
    val type: String,
    val text: String = "",
    val actionable: Boolean = false,
    val bounds: UiBounds = UiBounds.EMPTY,
    val x: Int = 0,
    val y: Int = 0,
    /** Contributing modalities, ordered by priority. */
    val sources: List<String> = emptyList(),
    val accessibilityId: String? = null,
    val ocrId: String? = null,
    val visionId: String? = null,
    val viewId: String = "",
    val className: String = "",
    val contentDescription: String = "",
    val enabled: Boolean = true,
    val editable: Boolean = false,
    val confidence: Float = 1f,
) {
    companion object {
        const val TYPE_BUTTON = "button"
        const val TYPE_EDIT_TEXT = "edittext"
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_SCROLL = "scroll"
        const val TYPE_CHECKBOX = "checkbox"
        const val TYPE_SWITCH = "switch"
        const val TYPE_UNKNOWN = "unknown"
    }
}

fun ScreenOcrBlock.toUIElement(index: Int): UIElement {
    val box = UiBounds.parseCompact(bounds) ?: UiBounds(
        left = x - 1,
        top = y - 1,
        right = x + 1,
        bottom = y + 1,
    )
    return UIElement(
        id = "ocr$index",
        text = text,
        source = UIObservation.SOURCE_OCR,
        bounds = box,
        x = if (x != 0) x else box.centerX,
        y = if (y != 0) y else box.centerY,
    )
}

fun inferElementType(className: String, editable: Boolean, clickable: Boolean): String {
    val cls = className.substringAfterLast('.')
    return when {
        editable || cls.contains("EditText", true) || cls.contains("TextField", true) ->
            FusedUiElement.TYPE_EDIT_TEXT
        cls.contains("Button", true) || cls.contains("ImageButton", true) ->
            FusedUiElement.TYPE_BUTTON
        cls.contains("CheckBox", true) || cls.contains("CheckedTextView", true) ->
            FusedUiElement.TYPE_CHECKBOX
        cls.contains("Switch", true) || cls.contains("Toggle", true) ->
            FusedUiElement.TYPE_SWITCH
        cls.contains("Image", true) || cls.contains("Icon", true) ->
            FusedUiElement.TYPE_IMAGE
        cls.contains("Scroll", true) || cls.contains("Recycler", true) ||
            cls.contains("ListView", true) -> FusedUiElement.TYPE_SCROLL
        clickable -> FusedUiElement.TYPE_BUTTON
        cls.contains("Text", true) -> FusedUiElement.TYPE_TEXT
        else -> FusedUiElement.TYPE_UNKNOWN
    }
}
