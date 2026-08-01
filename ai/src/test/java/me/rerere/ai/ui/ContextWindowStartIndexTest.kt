package me.rerere.ai.ui

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowStartIndexTest {

    @Test
    fun `start index is zero under limit`() {
        val messages = createMessages(8)
        assertEquals(0, messages.contextWindowStartIndex(10))
        assertEquals(messages, messages.limitContext(10))
    }

    @Test
    fun `start index matches limitContext drop prefix`() {
        val messages = createMessages(20)
        val start = messages.contextWindowStartIndex(10)
        assertTrue(start > 0)
        assertEquals(messages.subList(start, messages.size), messages.limitContext(10))
    }

    @Test
    fun `planRollingSummaryUpdate null when under limit`() {
        assertNull(
            planRollingSummaryUpdate(
                messages = createMessages(8),
                contextMessageLimit = 10,
                existingSummary = null,
                coveredCount = 0,
            )
        )
    }

    @Test
    fun `planRollingSummaryUpdate covers dropped prefix`() {
        val messages = createMessages(20)
        val start = messages.contextWindowStartIndex(10)
        val plan = planRollingSummaryUpdate(
            messages = messages,
            contextMessageLimit = 10,
            existingSummary = null,
            coveredCount = 0,
        )
        assertNotNull(plan)
        assertEquals(start, plan!!.coverCount)
        assertEquals(messages.subList(0, start), plan.uncoveredMessages)
        assertNull(plan.previousSummary)
    }

    @Test
    fun `planRollingSummaryUpdate skips when already covered`() {
        val messages = createMessages(20)
        val start = messages.contextWindowStartIndex(10)
        assertNull(
            planRollingSummaryUpdate(
                messages = messages,
                contextMessageLimit = 10,
                existingSummary = "existing",
                coveredCount = start,
            )
        )
    }

    @Test
    fun `planRollingSummaryUpdate incremental only newly dropped`() {
        val messages = createMessages(30)
        val start = messages.contextWindowStartIndex(10)
        val previousCovered = (start / 2).coerceAtLeast(1).coerceAtMost(start - 1)
        val plan = planRollingSummaryUpdate(
            messages = messages,
            contextMessageLimit = 10,
            existingSummary = "old",
            coveredCount = previousCovered,
        )
        assertNotNull(plan)
        assertEquals(start, plan!!.coverCount)
        assertEquals(messages.subList(previousCovered, start), plan.uncoveredMessages)
        assertEquals("old", plan.previousSummary)
    }

    private fun createMessages(count: Int): List<UIMessage> = List(count) { index ->
        UIMessage(
            role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("Message $index")),
        )
    }
}
