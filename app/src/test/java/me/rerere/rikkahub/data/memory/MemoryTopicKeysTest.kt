package me.rerere.rikkahub.data.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryTopicKeysTest {
    @Test
    fun `infers birthday topic`() {
        assertEquals(MemoryTopicKeys.PROFILE_BIRTHDAY, MemoryTopicKeys.inferTopicKey("我的生日是3月1日"))
        assertEquals(MemoryTopicKeys.PROFILE_BIRTHDAY, MemoryTopicKeys.inferTopicKey("My birthday is March 1"))
    }

    @Test
    fun `infers like and dislike without colliding`() {
        assertEquals(MemoryTopicKeys.PREFERENCE_LIKE, MemoryTopicKeys.inferTopicKey("我喜欢手冲咖啡"))
        assertEquals(MemoryTopicKeys.PREFERENCE_DISLIKE, MemoryTopicKeys.inferTopicKey("我不喜欢加班"))
    }

    @Test
    fun `infers reply style`() {
        assertEquals(
            MemoryTopicKeys.PREFERENCE_REPLY_STYLE,
            MemoryTopicKeys.inferTopicKey("User prefers brief replies")
        )
    }

    @Test
    fun `unmatched content stays unclassified`() {
        assertNull(MemoryTopicKeys.inferTopicKey("上周跟朋友去了趟海边，下次想再去"))
        assertNull(MemoryTopicKeys.inferTopicKey(""))
    }
}
