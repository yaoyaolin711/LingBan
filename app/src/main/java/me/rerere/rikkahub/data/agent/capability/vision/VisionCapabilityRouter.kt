package me.rerere.rikkahub.data.agent.capability.vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * Runtime-layer vision routing: NativeVision vs OCR fallback.
 *
 * Decision is based solely on [Model.inputModalities] — never delegated to the LLM.
 */
interface VisionCapabilityRouter {
    fun decide(model: Model, parts: List<UIMessagePart>): VisionRouteDecision

    fun decideForMessages(model: Model, messages: List<UIMessage>): VisionRouteDecision

    /**
     * For `see_screen` [ocrMode]: `auto` | `force` | `skip`.
     * - auto: always OCR (cheap ML Kit; needed for click coords even with vision models)
     * - force: always OCR
     * - skip: never OCR (vision channel)
     */
    fun shouldRunLocalOcr(model: Model?, ocrMode: String): Boolean

    /**
     * Apply routing to chat messages before GenerationHandler sends them to the LLM.
     * Persisted conversation messages are unchanged; only the outbound copy is normalized.
     */
    suspend fun normalize(
        model: Model,
        messages: List<UIMessage>,
        onStatus: ((String?) -> Unit)? = null,
    ): List<UIMessage>
}

class DefaultVisionCapabilityRouter(
    private val ocr: OcrCapability,
) : VisionCapabilityRouter {

    override fun decide(model: Model, parts: List<UIMessagePart>): VisionRouteDecision {
        if (!parts.hasLocalImageParts()) {
            return VisionRouteDecision(VisionRoute.PASSTHROUGH, reason = "no_local_images")
        }
        return if (model.supportsVisionInput()) {
            VisionRouteDecision(VisionRoute.NATIVE_VISION, reason = "model_has_image_modality")
        } else {
            VisionRouteDecision(VisionRoute.OCR_FALLBACK, reason = "text_only_ocr")
        }
    }

    override fun decideForMessages(model: Model, messages: List<UIMessage>): VisionRouteDecision {
        if (!messages.hasLocalImageMessages()) {
            return VisionRouteDecision(VisionRoute.PASSTHROUGH, reason = "no_local_images")
        }
        return if (model.supportsVisionInput()) {
            VisionRouteDecision(VisionRoute.NATIVE_VISION, reason = "model_has_image_modality")
        } else {
            VisionRouteDecision(VisionRoute.OCR_FALLBACK, reason = "text_only_ocr")
        }
    }

    override fun shouldRunLocalOcr(model: Model?, ocrMode: String): Boolean {
        return when (ocrMode.lowercase().trim()) {
            "skip", "vision", "off", "false" -> false
            "force", "local", "on", "true" -> true
            // auto: always OCR for phone-control click coords (ML Kit is cheap).
            // Vision models still receive the screenshot as a separate channel.
            else -> true
        }
    }

    override suspend fun normalize(
        model: Model,
        messages: List<UIMessage>,
        onStatus: ((String?) -> Unit)?,
    ): List<UIMessage> {
        val decision = decideForMessages(model, messages)
        if (decision.route == VisionRoute.PASSTHROUGH) {
            return messages
        }

        return withContext(Dispatchers.IO) {
            try {
                onStatus?.invoke(ocr.chatOcrStatusMessage())
                val keepImage = decision.route == VisionRoute.NATIVE_VISION
                messages.map { message ->
                    message.copy(
                        parts = message.parts.flatMap { part ->
                            if (!part.isLocalImage()) {
                                listOf(part)
                            } else {
                                val ocrText = ocr.recognizeChatImage(part as UIMessagePart.Image)
                                if (keepImage) {
                                    // Keep pixels for vision models + inject OCR so Chinese text is readable
                                    listOf(part, UIMessagePart.Text(ocrText))
                                } else {
                                    listOf(UIMessagePart.Text(ocrText))
                                }
                            }
                        },
                    )
                }
            } finally {
                onStatus?.invoke(null)
            }
        }
    }
}
