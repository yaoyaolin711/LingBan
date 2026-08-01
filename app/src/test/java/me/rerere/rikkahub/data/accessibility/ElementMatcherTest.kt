package me.rerere.rikkahub.data.accessibility

import me.rerere.rikkahub.data.ocr.ScreenOcrBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementMatcherTest {

    @Test
    fun mergesButtonWithOcrText() {
        val a11y = listOf(
            UIElement(
                id = "n1",
                className = "android.widget.Button",
                clickable = true,
                bounds = UiBounds(100, 200, 300, 280),
                x = 200,
                y = 240,
                source = UIObservation.SOURCE_ACCESSIBILITY,
            ),
        )
        val ocr = listOf(
            UIElement(
                id = "ocr0",
                text = "发送",
                bounds = UiBounds(120, 210, 280, 270),
                x = 200,
                y = 240,
                source = UIObservation.SOURCE_OCR,
            ),
        )
        val fused = ElementMatcher.match(a11y, ocr)
        val button = fused.first { it.accessibilityId == "n1" }
        assertEquals(FusedUiElement.TYPE_BUTTON, button.type)
        assertEquals("发送", button.text)
        assertTrue(button.actionable)
        assertEquals(
            listOf(UIObservation.SOURCE_ACCESSIBILITY, UIObservation.SOURCE_OCR),
            button.sources,
        )
        // OCR matched → not duplicated as orphan
        assertFalse(fused.any { it.ocrId == "ocr0" && it.accessibilityId == null })
    }

    @Test
    fun ocrOnlyBecomesSupplement() {
        val a11y = listOf(
            UIElement(
                id = "n0",
                text = "标题",
                className = "android.widget.TextView",
                bounds = UiBounds(0, 0, 100, 40),
                x = 50,
                y = 20,
            ),
        )
        val ocr = listOf(
            UIElement(
                id = "ocr9",
                text = "画布文字",
                bounds = UiBounds(400, 800, 500, 840),
                x = 450,
                y = 820,
            ),
        )
        val fused = ElementMatcher.match(a11y, ocr)
        assertTrue(fused.any { it.text == "画布文字" && it.sources == listOf(UIObservation.SOURCE_OCR) })
    }

    @Test
    fun visionIsFallbackWhenNotCovered() {
        val fused = ElementMatcher.match(
            accessibilityElements = emptyList(),
            ocrElements = emptyList(),
            visualElements = listOf(
                UIElement(
                    id = "v0",
                    text = "图标区",
                    clickable = true,
                    bounds = UiBounds(10, 10, 80, 80),
                    x = 45,
                    y = 45,
                ),
            ),
        )
        assertEquals(1, fused.size)
        assertEquals(listOf(UIObservation.SOURCE_VISION), fused[0].sources)
        assertTrue(fused[0].actionable)
    }

    @Test
    fun buildFromSnapshotAndOcrBlocks() {
        val snapshot = UISnapshot(
            page = "com.example.ChatActivity",
            packageName = "com.example",
            timestamp = 1L,
            root = UiTreeNode(
                nodeId = "n0",
                className = "android.widget.Button",
                clickable = true,
                bounds = UiBounds(0, 0, 200, 80),
            ),
            nodeCount = 1,
            screenWidth = 1080,
            screenHeight = 2400,
        )
        val obs = UnifiedObservation.fromModalities(
            snapshot = snapshot,
            ocrBlocks = listOf(
                ScreenOcrBlock(text = "发送", x = 100, y = 40, bounds = "10,10,190,70"),
            ),
            hasScreenshot = true,
            ocrEngine = "mlkit_zh_en",
        )
        assertEquals(1, obs.accessibilityElements.size)
        assertEquals(1, obs.ocrElements.size)
        assertTrue(obs.hasScreenshot)
        val fused = obs.fusedElements.first()
        assertEquals("发送", fused.text)
        assertEquals(FusedUiElement.TYPE_BUTTON, fused.type)
        assertTrue(fused.actionable)
    }

    @Test
    fun accessibilityTextPreferredOverOcr() {
        val fused = ElementMatcher.match(
            accessibilityElements = listOf(
                UIElement(
                    id = "n1",
                    text = "Send",
                    className = "android.widget.Button",
                    clickable = true,
                    bounds = UiBounds(0, 0, 100, 50),
                    x = 50,
                    y = 25,
                ),
            ),
            ocrElements = listOf(
                UIElement(
                    id = "ocr0",
                    text = "发送",
                    bounds = UiBounds(0, 0, 100, 50),
                    x = 50,
                    y = 25,
                ),
            ),
        )
        assertEquals("Send", fused.first().text)
    }
}
