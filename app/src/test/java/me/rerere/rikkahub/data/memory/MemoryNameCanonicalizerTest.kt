package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.memory.MemoryTopicKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryNameCanonicalizerTest {
    @Test
    fun `canonicalize preference like chinese extracts value`() {
        val canon = MemoryNameCanonicalizer.canonicalizeNameOrAddressing(
            content = "我喜欢“手冲咖啡”",
            topicKey = MemoryTopicKeys.PREFERENCE_LIKE,
        )
        assertEquals("手冲咖啡", canon)
    }

    @Test
    fun `canonicalize preference like english extracts multiword value`() {
        val canon = MemoryNameCanonicalizer.canonicalizeNameOrAddressing(
            content = "User: I like \"hand brew coffee\"",
            topicKey = MemoryTopicKeys.PREFERENCE_LIKE,
        )
        assertEquals("handbrewcoffee", canon)
    }

    @Test
    fun `canonicalize preference dislike chinese extracts value`() {
        val canon = MemoryNameCanonicalizer.canonicalizeNameOrAddressing(
            content = "我不喜欢加班",
            topicKey = MemoryTopicKeys.PREFERENCE_DISLIKE,
        )
        assertEquals("加班", canon)
    }

    @Test
    fun `canonicalize preference dislike english extracts value`() {
        val canon = MemoryNameCanonicalizer.canonicalizeNameOrAddressing(
            content = "User: I don't like \"black tea\"",
            topicKey = MemoryTopicKeys.PREFERENCE_DISLIKE,
        )
        assertEquals("blacktea", canon)
    }
}

