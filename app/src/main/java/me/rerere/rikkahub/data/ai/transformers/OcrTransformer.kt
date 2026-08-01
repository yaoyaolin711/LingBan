package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.agent.capability.vision.OcrCapability
import me.rerere.rikkahub.data.agent.capability.vision.VisionCapabilityRouter
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Chat input adapter: delegates vision vs OCR routing to [VisionCapabilityRouter].
 * Does not let the LLM decide whether to OCR.
 */
object OcrTransformer : InputMessageTransformer, KoinComponent {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val router = get<VisionCapabilityRouter>()
        return router.normalize(
            model = ctx.model,
            messages = messages,
            onStatus = { ctx.processingStatus.value = it },
        )
    }

    /** @deprecated Prefer [OcrCapability.recognizeChatImage]; kept for call-site compatibility. */
    suspend fun performOcr(part: UIMessagePart.Image): String =
        get<OcrCapability>().recognizeChatImage(part)
}
