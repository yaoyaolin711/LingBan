package me.rerere.rikkahub.data.workflow

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ScheduledWorkflowRulesTest {
    private val workflowId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val assistantA = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val assistantB = Uuid.parse("33333333-3333-3333-3333-333333333333")

    @Test
    fun `daily rule triggers once inside lookback window`() {
        val rule = ScheduledWorkflowRule(
            id = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            name = "daily",
            workflowId = workflowId,
            targets = listOf(ScheduledWorkflowTargetAssistant(assistantA)),
            assistantPriority = listOf(assistantA),
            hour = 9,
            minute = 0,
            timezoneId = "Asia/Shanghai",
        )
        val now = ZonedDateTime.of(2026, 8, 4, 9, 6, 0, 0, ZoneId.of("Asia/Shanghai"))
        val due = ScheduledWorkflowRules.resolveDueRule(rule, now, lookbackMinutes = 15)
        assertNotNull(due)
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:DAILY_AT_TIME:2026-08-04:09:00", due?.triggerKey)

        val consumed = rule.copy(lastTriggerKey = due!!.triggerKey)
        assertNull(ScheduledWorkflowRules.resolveDueRule(consumed, now, lookbackMinutes = 15))
    }

    @Test
    fun `weekday rule skips weekend`() {
        val rule = ScheduledWorkflowRule(
            name = "weekday",
            workflowId = workflowId,
            targets = listOf(ScheduledWorkflowTargetAssistant(assistantA)),
            assistantPriority = listOf(assistantA),
            timeMode = ScheduledWorkflowTimeMode.WEEKDAYS_AT_TIME,
            hour = 10,
            minute = 30,
            timezoneId = "Asia/Shanghai",
        )
        val saturday = ZonedDateTime.of(2026, 8, 8, 10, 35, 0, 0, ZoneId.of("Asia/Shanghai"))
        assertNull(ScheduledWorkflowRules.resolveDueRule(rule, saturday, lookbackMinutes = 15))
    }

    @Test
    fun `normalization keeps selected priority order and appends missing assistants`() {
        val rule = ScheduledWorkflowRule(
            name = "normalize",
            workflowId = workflowId,
            targets = listOf(
                ScheduledWorkflowTargetAssistant(assistantA),
                ScheduledWorkflowTargetAssistant(assistantB),
            ),
            assistantPriority = listOf(assistantB),
        ).normalized()

        assertEquals(listOf(assistantB, assistantA), rule.assistantPriority)
        assertEquals(listOf(assistantB, assistantA), rule.activeAssistantIds())
    }

    @Test
    fun `disabled or targetless rules are never due`() {
        val disabled = ScheduledWorkflowRule(
            name = "disabled",
            workflowId = workflowId,
            targets = listOf(ScheduledWorkflowTargetAssistant(assistantA)),
            assistantPriority = listOf(assistantA),
            enabled = false,
        )
        val noTarget = ScheduledWorkflowRule(
            name = "empty",
            workflowId = workflowId,
            targets = emptyList(),
            assistantPriority = emptyList(),
        )
        val now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
        assertNull(ScheduledWorkflowRules.resolveDueRule(disabled, now))
        assertNull(ScheduledWorkflowRules.resolveDueRule(noTarget, now))
        assertFalse(disabled.enabled)
        assertTrue(noTarget.activeAssistantIds().isEmpty())
    }
}
