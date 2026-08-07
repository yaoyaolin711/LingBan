package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.db.entity.MemoryLayer
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.GlobalUserProfile
import me.rerere.rikkahub.data.repository.MemoryTurnHints
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
        assertTrue(withoutRecall.contains("memory_search"))
        assertTrue(withoutRecall.contains("relation summary") || withoutRecall.contains("entity neighborhood"))
        assertFalse(withoutRecall.contains("recall_chat_history"))
    }

    @Test
    fun `preretrieve prompt includes relation line when present`() {
        val prompt = buildMemoryPreretrievePrompt(
            MemoryTurnHints(
                memories = listOf(
                    AssistantMemory(id = 9, content = "和阿明去了海边", layer = MemoryLayer.EPISODE),
                ),
                relationLine = "海边 —关联→ 阿明, 旅行",
            )
        )
        assertTrue(prompt.contains("Memory hints for this turn"))
        assertTrue(prompt.contains("Relations: 海边 —关联→ 阿明, 旅行"))
        assertTrue(prompt.contains("[id=9]"))
        assertTrue(prompt.contains("do not invent edges"))
    }

    @Test
    fun `memory prompt empty list injects nothing`() {
        assertEquals("", buildMemoryPrompt(emptyList()))
    }

    @Test
    fun `global user profile prompt is empty when card blank`() {
        assertEquals("", buildGlobalUserProfilePrompt(GlobalUserProfile()))
    }

    @Test
    fun `global user profile prompt includes filled fields and sharing note`() {
        val prompt = buildGlobalUserProfilePrompt(
            GlobalUserProfile(
                displayName = "阿雨",
                birthday = "1998-05-20",
                occupation = "设计师",
                preferredAddressing = "叫我阿雨",
            )
        )
        assertTrue(prompt.contains("User profile (shared across all companions)"))
        assertTrue(prompt.contains("Name: 阿雨"))
        assertTrue(prompt.contains("Birthday: 1998-05-20"))
        assertTrue(prompt.contains("Occupation: 设计师"))
        assertTrue(prompt.contains("Preferred addressing: 叫我阿雨"))
        assertFalse(prompt.contains("Personality:"))
    }

    @Test
    fun `memory prompt shows profile and episode index not full dump of every episode`() {
        val memories = listOf(
            AssistantMemory(
                id = 1,
                content = "User prefers brief replies",
                topicKey = "preference.reply_style",
                layer = MemoryLayer.PROFILE,
                updatedAt = 100,
            ),
            AssistantMemory(
                id = 2,
                content = "Discussed moving to Shanghai next spring with a long detailed plan about apartments",
                layer = MemoryLayer.EPISODE,
                updatedAt = 200,
            ),
        )
        val prompt = buildMemoryPrompt(memories)
        assertTrue(prompt.contains("**Memories**"))
        assertTrue(prompt.contains("Profile:"))
        assertTrue(prompt.contains("User prefers brief replies"))
        assertTrue(prompt.contains("Episode index"))
        assertTrue(prompt.contains("id=2"))
        assertTrue(prompt.contains("memory_search"))
        // Full long episode body should be truncated in the index preview
        assertFalse(prompt.contains("apartments"))
    }

    @Test
    fun `memory prompt appends graph hubs within budget and omits when blank`() {
        val memories = listOf(
            AssistantMemory(
                id = 1,
                content = "User prefers brief replies",
                topicKey = "preference.reply_style",
                layer = MemoryLayer.PROFILE,
                updatedAt = 100,
            ),
        )
        val hubs = "海边→阿明; 阿明→旅行; 旅行→海边"
        val prompt = buildMemoryPrompt(memories, graphHubsSummary = hubs)
        assertTrue(prompt.contains("Graph hubs"))
        assertTrue(prompt.contains(hubs))
        assertTrue(hubs.length <= MEMORY_GRAPH_HUBS_CHAR_BUDGET)

        val without = buildMemoryPrompt(memories, graphHubsSummary = "  ")
        assertFalse(without.contains("Graph hubs"))
        assertEquals("", buildMemoryPrompt(emptyList(), graphHubsSummary = null))
    }

    @Test
    fun `episode index respects character budget`() {
        val episodes = (1..80).map { index ->
            AssistantMemory(
                id = index,
                content = "Episode note number $index with some extra padding text for length",
                layer = MemoryLayer.EPISODE,
                updatedAt = index.toLong(),
            )
        }
        val index = buildBudgetedEpisodeIndex(episodes)!!
        assertTrue(index.length <= MEMORY_EPISODE_INDEX_CHAR_BUDGET + MEMORY_EPISODE_PREVIEW_CHARS)
        assertTrue(index.contains("id=80"))
        assertFalse(index.contains("id=1:"))
    }

    @Test
    fun `profile budget keeps newest entries within cap`() {
        val profiles = (1..40).map { index ->
            AssistantMemory(
                id = index,
                content = "x".repeat(80) + " profile-$index",
                topicKey = "preference.like",
                layer = MemoryLayer.PROFILE,
                updatedAt = index.toLong(),
            )
        }
        val json = buildBudgetedProfileJson(profiles)!!
        assertTrue(json.length <= MEMORY_PROFILE_CHAR_BUDGET + 120)
        assertTrue(json.contains("profile-40") || json.contains("\"id\": 40") || json.contains("\"id\":40"))
    }
}
