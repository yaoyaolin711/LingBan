package me.rerere.rikkahub.data.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.uuid.Uuid

@Serializable
enum class ScheduledWorkflowTimeMode {
    @SerialName("daily_at_time")
    DAILY_AT_TIME,

    @SerialName("weekdays_at_time")
    WEEKDAYS_AT_TIME,
}

@Serializable
data class ScheduledWorkflowTargetAssistant(
    val assistantId: Uuid,
    val enabled: Boolean = true,
)

@Serializable
data class ScheduledWorkflowRule(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val workflowId: Uuid,
    val targets: List<ScheduledWorkflowTargetAssistant> = emptyList(),
    val assistantPriority: List<Uuid> = emptyList(),
    val timeMode: ScheduledWorkflowTimeMode = ScheduledWorkflowTimeMode.DAILY_AT_TIME,
    val hour: Int = 9,
    val minute: Int = 0,
    val timezoneId: String = ZoneId.systemDefault().id,
    val enabled: Boolean = true,
    val lastTriggeredAtEpochMs: Long = 0L,
    val lastTriggerKey: String = "",
) {
    fun normalized(): ScheduledWorkflowRule {
        val uniqueTargets = targets
            .groupBy { it.assistantId }
            .map { (_, values) -> values.first() }
        val allowed = uniqueTargets.mapTo(LinkedHashSet()) { it.assistantId }
        val priority = buildList {
            assistantPriority.forEach { id ->
                if (id in allowed && id !in this) add(id)
            }
            uniqueTargets.forEach { target ->
                if (target.assistantId !in this) add(target.assistantId)
            }
        }
        return copy(
            name = name.trim(),
            description = description.trim(),
            targets = uniqueTargets,
            assistantPriority = priority,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
        )
    }

    fun activeAssistantIds(): List<Uuid> {
        val enabledIds = targets.filter { it.enabled }.map { it.assistantId }.toSet()
        return assistantPriority.filter { it in enabledIds }
    }
}

data class ScheduledWorkflowDue(
    val rule: ScheduledWorkflowRule,
    val triggerKey: String,
    val triggerAt: ZonedDateTime,
)

object ScheduledWorkflowRules {
    private const val WORKER_LOOKBACK_MINUTES = 15L

    fun validate(rule: ScheduledWorkflowRule) {
        require(rule.name.isNotBlank()) { "Rule name is required" }
        require(rule.targets.isNotEmpty()) { "At least one target assistant is required" }
        require(rule.activeAssistantIds().isNotEmpty()) { "At least one enabled assistant is required" }
        require(runCatching { ZoneId.of(rule.timezoneId) }.isSuccess) { "Invalid timezone: ${rule.timezoneId}" }
    }

    fun resolveDueRules(
        rules: List<ScheduledWorkflowRule>,
        now: ZonedDateTime,
        lookbackMinutes: Long = WORKER_LOOKBACK_MINUTES,
    ): List<ScheduledWorkflowDue> {
        return rules.mapNotNull { rule ->
            resolveDueRule(rule, now, lookbackMinutes)
        }
    }

    fun resolveDueRule(
        rule: ScheduledWorkflowRule,
        now: ZonedDateTime,
        lookbackMinutes: Long = WORKER_LOOKBACK_MINUTES,
    ): ScheduledWorkflowDue? {
        if (!rule.enabled) return null
        if (rule.activeAssistantIds().isEmpty()) return null
        val zone = runCatching { ZoneId.of(rule.timezoneId) }.getOrNull() ?: return null
        val localNow = now.withZoneSameInstant(zone)
        // "最接近过去"的定时点：如果当前时间早于目标时刻，说明今天的目标还没来，应取昨天的目标时刻。
        var scheduledAt = localNow
            .withHour(rule.hour)
            .withMinute(rule.minute)
            .withSecond(0)
            .withNano(0)
        if (scheduledAt.isAfter(localNow)) {
            scheduledAt = scheduledAt.minusDays(1)
        }
        if (!matchesTimeMode(rule, scheduledAt)) return null
        val earliest = localNow.minusMinutes(lookbackMinutes)
        if (scheduledAt.isBefore(earliest) || scheduledAt.isAfter(localNow)) return null
        val triggerKey = buildTriggerKey(rule, scheduledAt)
        if (triggerKey == rule.lastTriggerKey) return null
        return ScheduledWorkflowDue(
            rule = rule,
            triggerKey = triggerKey,
            triggerAt = scheduledAt,
        )
    }

    fun buildTriggerKey(
        rule: ScheduledWorkflowRule,
        scheduledAt: ZonedDateTime,
    ): String {
        val datePart = scheduledAt.toLocalDate().toString()
        val timePart = "%02d:%02d".format(rule.hour, rule.minute)
        return "${rule.id}:${rule.timeMode.name}:$datePart:$timePart"
    }

    private fun matchesTimeMode(
        rule: ScheduledWorkflowRule,
        scheduledAt: ZonedDateTime,
    ): Boolean = when (rule.timeMode) {
        ScheduledWorkflowTimeMode.DAILY_AT_TIME -> true
        ScheduledWorkflowTimeMode.WEEKDAYS_AT_TIME -> {
            scheduledAt.dayOfWeek in setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            )
        }
    }
}
