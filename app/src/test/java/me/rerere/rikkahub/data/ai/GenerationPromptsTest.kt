package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.AssistantMemory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationPromptsTest {
    @Test
    fun `rolling summary prompt includes body and recall guidance`() {
        val prompt = buildRollingSummaryPrompt("User prefers short answers.")
        assertTrue(prompt.contains("Conversation context summary"))
        assertTrue(prompt.contains("User prefers short answers."))
        assertTrue(prompt.contains("recall_chat_history"))
        assertFalse(prompt.contains("MUST always"))
    }

    @Test
    fun `runtime policy is empty when no continuity features`() {
        assertTrue(
            buildRuntimeContinuityPolicy(
                hasRollingSummary = false,
                hasRecallTool = false,
                hasMemoryTool = false,
                hasCrossConversationSearch = false,
            ).isEmpty()
        )
    }

    @Test
    fun `runtime policy mentions recall only when tool present`() {
        val withRecall = buildRuntimeContinuityPolicy(
            hasRollingSummary = true,
            hasRecallTool = true,
            hasMemoryTool = false,
            hasCrossConversationSearch = false,
        )
        assertTrue(withRecall.contains("Runtime context policy"))
        assertTrue(withRecall.contains("recall_chat_history"))
        assertTrue(withRecall.contains("look it up") || withRecall.contains("call"))
        assertTrue(withRecall.contains("still found nothing") || withRecall.contains("don't have"))
        assertFalse(withRecall.contains("memory_tool"))

        val withoutRecall = buildRuntimeContinuityPolicy(
            hasRollingSummary = true,
            hasRecallTool = false,
            hasMemoryTool = true,
            hasCrossConversationSearch = false,
        )
        assertTrue(withoutRecall.contains("memory_tool"))
        assertFalse(withoutRecall.contains("recall_chat_history"))
    }

    @Test
    fun `memory prompt handles empty list`() {
        val empty = buildMemoryPrompt(emptyList())
        assertTrue(empty.contains("empty"))
        val filled = buildMemoryPrompt(listOf(AssistantMemory(id = 1, content = "likes tea")))
        assertTrue(filled.contains("likes tea"))
        assertTrue(filled.contains("\"id\": 1") || filled.contains("\"id\":1"))
    }
}
