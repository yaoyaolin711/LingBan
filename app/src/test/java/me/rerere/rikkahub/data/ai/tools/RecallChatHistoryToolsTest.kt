package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.contextWindowStartIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallChatHistoryToolsTest {

    @Test
    fun `search prefers outside-window hits for matching keywords`() {
        val messages = buildList {
            add(msg(MessageRole.USER, "部署端口是 8080，记住这个数字"))
            add(msg(MessageRole.ASSISTANT, "好的，端口 8080"))
            // pad so context limit trims the early messages
            repeat(18) { i ->
                add(msg(if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT, "闲聊 $i"))
            }
            add(msg(MessageRole.USER, "刚才端口是多少？"))
        }
        val limit = 10
        val start = messages.contextWindowStartIndex(limit)
        assertTrue(start > 0)

        val hits = searchCurrentChatHistory(
            messages = messages,
            contextMessageLimit = limit,
            query = "8080",
            limit = 5,
        )
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.outsideContextWindow && it.text.contains("8080") })
        assertTrue(hits.first().outsideContextWindow)
    }

    @Test
    fun `range read returns contiguous original messages`() {
        val messages = List(12) { i ->
            msg(
                if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                "Message $i detail"
            )
        }
        val hits = readCurrentChatRange(
            messages = messages,
            contextMessageLimit = 6,
            startIndex = 1,
            endIndexInclusive = 3,
        )
        assertEquals(3, hits.size)
        assertEquals(1, hits[0].index)
        assertEquals(3, hits[2].index)
        assertTrue(hits.all { it.text.contains("detail") })
    }

    private fun msg(role: MessageRole, text: String) = UIMessage(
        role = role,
        parts = listOf(UIMessagePart.Text(text)),
    )
}
