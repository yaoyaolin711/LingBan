package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemoryRelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRuleIndexerTest {
    @Test
    fun `preference memory does not spawn false place nodes from zai context`() {
        val text = "在魅魔角色扮演中，用户最喜欢的姿势是后入式（从背后插入），用户会在角色扮演中要求记住这一点。"
        val extracted = MemoryRuleIndexer.extract(text, MemoryTopicKeys.PREFERENCE_LIKE)
        val names = extracted.entities.map { it.name }
        val types = extracted.entities.associate { it.name to it.type }
        assertFalse(names.any { types[it] == me.rerere.rikkahub.data.db.entity.MemoryEntityType.PLACE })
        assertFalse(names.contains("like"))
        assertTrue(names.any { it.contains("后入") })
        assertEquals(1, extracted.entities.size)
    }

    @Test
    fun `extract pulls person and place and coOccur pairs`() {
        val extracted = MemoryRuleIndexer.extract("上周和阿明一起去了海边旅行", null)
        val names = extracted.entities.map { it.name }
        assertTrue(names.any { it.contains("阿明") || it == "阿明" })
        assertTrue(names.any { it.contains("海边") || it == "海边" })
        val pairs = MemoryRuleIndexer.coOccurPairs(extracted.entities)
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.all { it.relation == MemoryRelationType.CO_OCCURS })
        assertNotNull(extracted.observedAtHint)
    }

    @Test
    fun `inferObservedAtHint for relative time words`() {
        val now = 1_700_000_000_000L
        assertEquals(now - 24L * 60 * 60 * 1000, MemoryRuleIndexer.inferObservedAtHint("昨天去了公园", now))
        assertEquals(now - 7L * 24 * 60 * 60 * 1000, MemoryRuleIndexer.inferObservedAtHint("上周和朋友吃饭", now))
        assertNull(MemoryRuleIndexer.inferObservedAtHint("喜欢喝茶", now))
    }

    @Test
    fun `embedding cosine identical vectors is one`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, MemoryEmbeddingMath.cosine(a, b), 1e-5f)
        val encoded = MemoryEmbeddingMath.encodeVector(a)
        val decoded = MemoryEmbeddingMath.decodeVector(encoded)!!
        assertEquals(a.toList(), decoded.toList())
    }
}
