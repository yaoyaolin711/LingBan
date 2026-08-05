package me.rerere.rikkahub.ui.components.message

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceRevealTest {
    @Test
    fun splitSentences_keepsDelimitersAndIsLossless() {
        val text = "你好。世界！\nHello. OK?"
        val parts = SentenceReveal.splitSentences(text)
        assertEquals(listOf("你好。", "世界！", "\n", "Hello.", " OK?"), parts)
        assertEquals(text, parts.joinToString(""))
    }

    @Test
    fun availableSentences_holdsIncompleteWhileStreaming() {
        val text = "第一句。未完成"
        assertEquals(
            listOf("第一句。"),
            SentenceReveal.availableSentences(text, includeIncomplete = false),
        )
        assertEquals(
            listOf("第一句。", "未完成"),
            SentenceReveal.availableSentences(text, includeIncomplete = true),
        )
    }

    @Test
    fun delayMs_respectsBounds() {
        assertEquals(0L, SentenceReveal.delayMs("   ", 10f))
        assertEquals(SentenceReveal.MIN_DELAY_MS, SentenceReveal.delayMs("a", 100f))
        assertEquals(SentenceReveal.MAX_DELAY_MS, SentenceReveal.delayMs("x".repeat(10_000), 1f))
        assertEquals(1000L, SentenceReveal.delayMs("abcdefghij", 10f))
    }

    @Test
    fun applyRevealToParts_truncatesTextPrefix() {
        val parts = listOf(
            UIMessagePart.Text("Hello "),
            UIMessagePart.Text("World!"),
        )
        val revealed = SentenceReveal.applyRevealToParts(parts, 8)
        assertEquals("Hello ", (revealed[0] as UIMessagePart.Text).text)
        assertEquals("Wo", (revealed[1] as UIMessagePart.Text).text)
        assertEquals(2, revealed.size)
        assertTrue(revealed.all { it is UIMessagePart.Text })
    }

    @Test
    fun expandToSentenceBubbles_oneBubblePerSentence() {
        val parts = listOf(
            UIMessagePart.Text("你好。世界！还有吗"),
        )
        val sentences = SentenceReveal.splitSentences("你好。世界！还有吗")
        val expanded = SentenceReveal.expandToSentenceBubbles(parts, sentences)
        assertEquals(
            listOf("你好。", "世界！", "还有吗"),
            expanded.filterIsInstance<UIMessagePart.Text>().map { it.text },
        )
    }
}
