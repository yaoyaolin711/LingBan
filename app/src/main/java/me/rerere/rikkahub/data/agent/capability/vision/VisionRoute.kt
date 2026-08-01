package me.rerere.rikkahub.data.agent.capability.vision

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * Deterministic vision routing — decided by Runtime capability, never by the LLM.
 */
enum class VisionRoute {
    /** No chat image parts; leave messages unchanged. */
    PASSTHROUGH,

    /** Model accepts IMAGE input; keep [UIMessagePart.Image] and enrich with OCR text. */
    NATIVE_VISION,

    /** Text-only model; convert images to OCR text before LLM. */
    OCR_FALLBACK,
}

data class VisionRouteDecision(
    val route: VisionRoute,
    val reason: String = "",
)

fun Model.supportsVisionInput(): Boolean =
    inputModalities.contains(Modality.IMAGE)

/**
 * Chat-uploaded images that should go through OCR (local file / data / content URI).
 * Remote http(s) images are left for native vision only.
 */
fun UIMessagePart.isLocalImage(): Boolean {
    if (this !is UIMessagePart.Image) return false
    val u = url.trim()
    if (u.isEmpty()) return false
    if (u.startsWith("http://") || u.startsWith("https://")) return false
    return u.startsWith("file:") ||
        u.startsWith("content:") ||
        u.startsWith("data:image") ||
        u.startsWith("/") ||
        (u.length > 2 && u[1] == ':' && (u[2] == '\\' || u[2] == '/'))
}

fun List<UIMessagePart>.hasLocalImageParts(): Boolean =
    any { it.isLocalImage() }

fun List<UIMessage>.hasLocalImageMessages(): Boolean =
    any { message -> message.parts.hasLocalImageParts() }
