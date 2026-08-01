package me.rerere.rikkahub.data.agent.capability.vision

import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.ocr.ScreenOcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VisionCapabilityRouterTest {

    private val textOnly = Model(
        modelId = "text-only",
        displayName = "Text",
        inputModalities = listOf(Modality.TEXT),
    )

    private val vision = Model(
        modelId = "vision",
        displayName = "Vision",
        inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
    )

    private val fakeOcr = object : OcrCapability {
        var recognizeCalls = 0
        override fun chatOcrStatusMessage(): String = "ocr…"
        override suspend fun recognizeChatImage(image: UIMessagePart.Image): String {
            recognizeCalls++
            return "<image_file_ocr>\nocr:${image.url}\n</image_file_ocr>"
        }

        override suspend fun recognizeScreen(
            imageFile: File,
            settings: Settings,
            screenWidth: Int,
            screenHeight: Int,
        ): ScreenOcrResult? = null
    }

    private fun router(ocr: OcrCapability = fakeOcr) = DefaultVisionCapabilityRouter(ocr)

    @Test
    fun supportsVisionInput_readsModalities() {
        assertTrue(vision.supportsVisionInput())
        assertFalse(textOnly.supportsVisionInput())
    }

    @Test
    fun decide_noImages_passthrough() {
        val d = router().decide(textOnly, listOf(UIMessagePart.Text("hi")))
        assertEquals(VisionRoute.PASSTHROUGH, d.route)
    }

    @Test
    fun decide_visionModel_nativeVision() {
        val d = router().decide(
            vision,
            listOf(UIMessagePart.Image(url = "file:///tmp/a.jpg")),
        )
        assertEquals(VisionRoute.NATIVE_VISION, d.route)
        assertEquals("model_has_image_modality", d.reason)
    }

    @Test
    fun decide_textOnly_ocrFallback() {
        val d = router().decide(
            textOnly,
            listOf(UIMessagePart.Image(url = "file:///tmp/a.jpg")),
        )
        assertEquals(VisionRoute.OCR_FALLBACK, d.route)
        assertEquals("text_only_ocr", d.reason)
    }

    @Test
    fun decide_ignoresNonFileImages() {
        val d = router().decide(
            textOnly,
            listOf(UIMessagePart.Image(url = "https://example.com/a.jpg")),
        )
        assertEquals(VisionRoute.PASSTHROUGH, d.route)
    }

    @Test
    fun shouldRunLocalOcr_auto() {
        val r = router()
        // auto always OCR for clickable text boxes (vision + text models)
        assertTrue(r.shouldRunLocalOcr(vision, "auto"))
        assertTrue(r.shouldRunLocalOcr(textOnly, "auto"))
        assertTrue(r.shouldRunLocalOcr(null, "auto"))
    }

    @Test
    fun shouldRunLocalOcr_forceAndSkip() {
        val r = router()
        assertTrue(r.shouldRunLocalOcr(vision, "force"))
        assertFalse(r.shouldRunLocalOcr(textOnly, "skip"))
        assertFalse(r.shouldRunLocalOcr(textOnly, "vision"))
    }

    @Test
    fun normalize_visionModel_keepsImagesAndAddsOcr() = runBlocking {
        val ocr = fakeOcr
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(
                    UIMessagePart.Text("what is this"),
                    UIMessagePart.Image(url = "file:///tmp/a.jpg"),
                ),
            ),
        )
        val out = router(ocr).normalize(vision, messages)
        assertEquals(1, ocr.recognizeCalls)
        val parts = out.single().parts
        assertTrue(parts.any { it is UIMessagePart.Image })
        assertTrue(parts.any { it is UIMessagePart.Text && it.text.contains("ocr:file:///tmp/a.jpg") })
    }

    @Test
    fun normalize_textOnly_replacesImagesWithOcrText() = runBlocking {
        val ocr = fakeOcr
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(
                    UIMessagePart.Text("what is this"),
                    UIMessagePart.Image(url = "file:///tmp/a.jpg"),
                ),
            ),
        )
        val out = router(ocr).normalize(textOnly, messages)
        assertEquals(1, ocr.recognizeCalls)
        val parts = out.single().parts
        assertFalse(parts.any { it is UIMessagePart.Image })
        assertTrue(parts.any { it is UIMessagePart.Text && it.text.contains("ocr:file:///tmp/a.jpg") })
    }
}
