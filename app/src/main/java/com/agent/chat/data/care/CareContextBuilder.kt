package com.agent.chat.data.care

import com.agent.chat.domain.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 组装「生活感知 + 情绪 + 待跟进」关怀块，供 system prompt 注入。
 */
@Singleton
class CareContextBuilder @Inject constructor(
    private val calendarPeek: CalendarPeek,
    private val followUpStore: FollowUpStore,
) {
    fun build(
        personaId: String?,
        recentMessages: List<Message>,
        includeCalendar: Boolean = true,
    ): String {
        val parts = mutableListOf<String>()
        val period = CareHeuristics.dayPeriod()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm E", Locale.getDefault()).format(Date())
        parts.add(
            buildString {
                append("【生活感知】当前 ")
                append(now)
                append("（")
                append(CareHeuristics.periodLabel(period))
                append("）。")
                append(CareHeuristics.situationalCareHint(period))
            },
        )

        CareHeuristics.moodGuidance(CareHeuristics.detectMood(recentMessages))?.let { parts.add(it) }

        if (includeCalendar) {
            calendarPeek.formatForPrompt(calendarPeek.upcoming())?.let { parts.add(it) }
        }

        // 合并本轮抽到的线索，再读出注入
        followUpStore.merge(personaId, CareHeuristics.extractFollowUpCandidates(recentMessages))
        followUpStore.formatForPrompt(personaId)?.let { parts.add(it) }

        return parts.joinToString("\n\n")
    }

    fun proactiveScenarioHint(): Pair<String, String> {
        val period = CareHeuristics.dayPeriod()
        val events = calendarPeek.upcoming(withinHours = 3, limit = 2)
        if (events.isNotEmpty()) {
            val e = events.first()
            val mins = ((e.beginAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
            return "calendar" to "用户大约 $mins 分钟后有日程「${e.title}」。请像关心朋友一样轻轻提一句，别像闹钟。"
        }
        val hint = when (period) {
            DayPeriod.EARLY_MORNING, DayPeriod.MORNING ->
                "morning" to "现在是早上。发一句简短的早安/关心，像刚醒想起对方。"
            DayPeriod.NOON ->
                "noon" to "现在是午饭前后。可以轻轻问有没有吃饭，别唠叨。"
            DayPeriod.EVENING ->
                "evening" to "现在是晚上。可以问问今天累不累，语气放松。"
            DayPeriod.LATE_NIGHT ->
                "latenight" to "现在是深夜。关心对方早点休息，回复极短，别刺激聊天。"
            DayPeriod.AFTERNOON ->
                "afternoon" to "下午了。可以随口关心一句状态，别太正式。"
        }
        return hint
    }
}
