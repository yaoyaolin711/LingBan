package me.rerere.rikkahub.data.ocr

import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.agent.capability.vision.OcrCapability
import me.rerere.rikkahub.data.agent.capability.vision.VisionCapabilityRouter
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File

/**
 * Screen OCR facade for phone-control tools.
 *
 * Routing (vision vs OCR) is owned by [VisionCapabilityRouter];
 * execution is owned by [OcrCapability].
 */
object ScreenOcrHelper : KoinComponent {

    /**
     * @param ocrMode `auto` | `force` | `skip`
 * - auto: always OCR (ML Kit provides clickable text boxes; screenshot still returned)
 * - force: always OCR
 * - skip: never OCR (vision channel)
     */
    fun shouldRunLocalOcr(settings: Settings, ocrMode: String): Boolean {
        val model = currentChatModel(settings)
        return get<VisionCapabilityRouter>().shouldRunLocalOcr(model, ocrMode)
    }

    fun currentChatModel(settings: Settings): Model? {
        val assistant = settings.getCurrentAssistant()
        val modelId = assistant.chatModelId ?: settings.chatModelId
        return settings.findModelById(modelId)
    }

    suspend fun recognizeScreen(
        imageFile: File,
        settings: Settings,
        screenWidth: Int,
        screenHeight: Int,
    ): ScreenOcrResult? =
        get<OcrCapability>().recognizeScreen(
            imageFile = imageFile,
            settings = settings,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
}
