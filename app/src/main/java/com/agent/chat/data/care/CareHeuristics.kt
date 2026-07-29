package com.agent.chat.data.care

import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import java.util.Calendar
import java.util.Locale

enum class DayPeriod {
    EARLY_MORNING,
    MORNING,
    NOON,
    AFTERNOON,
    EVENING,
    LATE_NIGHT,
}

enum class UserMood {
    NEUTRAL,
    LOW,
    STRESSED,
    UPBEAT,
    TIRED,
}

/**
 * 从本地时钟与近期对话推断「该怎么陪」——不调用网络。
 */
object CareHeuristics {

    fun dayPeriod(nowMillis: Long = System.currentTimeMillis()): DayPeriod {
        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..8 -> DayPeriod.EARLY_MORNING
            in 9..11 -> DayPeriod.MORNING
            in 12..13 -> DayPeriod.NOON
            in 14..17 -> DayPeriod.AFTERNOON
            in 18..22 -> DayPeriod.EVENING
            else -> DayPeriod.LATE_NIGHT
        }
    }

    fun periodLabel(period: DayPeriod): String = when (period) {
        DayPeriod.EARLY_MORNING -> "清晨"
        DayPeriod.MORNING -> "上午"
        DayPeriod.NOON -> "午间"
        DayPeriod.AFTERNOON -> "下午"
        DayPeriod.EVENING -> "傍晚/晚上"
        DayPeriod.LATE_NIGHT -> "深夜"
    }

    fun situationalCareHint(period: DayPeriod): String = when (period) {
        DayPeriod.EARLY_MORNING -> "可以轻轻问早上好、睡得怎样，别太吵。"
        DayPeriod.MORNING -> "适合简单问问今天安排，别一上来塞任务清单。"
        DayPeriod.NOON -> "可以关心有没有吃饭，语气轻松。"
        DayPeriod.AFTERNOON -> "适合接一下午状态，累了就少问问题。"
        DayPeriod.EVENING -> "可以问问今天过得怎样，适合放松闲聊。"
        DayPeriod.LATE_NIGHT -> "优先关心睡眠与安全；回复更短更轻，别刺激兴奋。"
    }

    fun detectMood(recentMessages: List<Message>): UserMood {
        val userText = recentMessages
            .asReversed()
            .filter { it.role == MessageRole.USER }
            .take(6)
            .joinToString("\n") { it.content }
            .lowercase(Locale.ROOT)
        if (userText.isBlank()) return UserMood.NEUTRAL

        val low = listOf("难过", "伤心", "哭", "失望", "难受", "孤独", "郁闷", "丧", "心疼", "不想活")
        val stressed = listOf("焦虑", "紧张", "压力", "烦死", "崩溃", "忙死", "截止日期", "ddl", "加班", "面试")
        val tired = listOf("好累", "疲惫", "困", "没力气", "睡不着", "失眠", "想睡觉")
        val upbeat = listOf("开心", "太好了", "哈哈", "嘿嘿", "兴奋", "成功", "通过了", "耶", "爱了")

        fun score(words: List<String>) = words.count { userText.contains(it) }
        val scores = listOf(
            UserMood.LOW to score(low),
            UserMood.STRESSED to score(stressed),
            UserMood.TIRED to score(tired),
            UserMood.UPBEAT to score(upbeat),
        )
        return scores.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first ?: UserMood.NEUTRAL
    }

    fun moodGuidance(mood: UserMood): String? = when (mood) {
        UserMood.NEUTRAL -> null
        UserMood.LOW -> """
【情绪适应】用户近期情绪偏低。
- 先共情，少给「你应该」式建议；回复更短、更软。
- 可以陪着，不要急着解决问题或转移话题。
""".trimIndent()
        UserMood.STRESSED -> """
【情绪适应】用户近期偏紧张/有压力。
- 先承认压力真实存在；一次只帮一个小点。
- 少列表、少说教；语气稳一点。
""".trimIndent()
        UserMood.TIRED -> """
【情绪适应】用户显得疲惫。
- 回复短；鼓励休息比鼓励努力更合适。
- 别用高能量语气轰炸。
""".trimIndent()
        UserMood.UPBEAT -> """
【情绪适应】用户情绪不错。
- 可以更活泼一点，一起高兴；别泼冷水。
""".trimIndent()
    }

    /**
     * 从最近用户话里抽出「值得之后问起」的短线索（启发式，非二次模型调用）。
     */
    fun extractFollowUpCandidates(recentMessages: List<Message>): List<String> {
        val patterns = listOf(
            Regex("""(?:明天|后天|下周|今晚|待会|等会|一会儿).{0,24}"""),
            Regex("""(?:面试|考试|开会|手术|体检|答辩|出差|约会).{0,16}"""),
            Regex("""(?:我要|打算|准备|计划).{0,20}"""),
            Regex("""(?:等结果|等通知|再看|到时候).{0,12}"""),
        )
        val out = linkedSetOf<String>()
        recentMessages
            .filter { it.role == MessageRole.USER }
            .takeLast(10)
            .forEach { msg ->
                val text = msg.content.replace('\n', ' ').trim()
                if (text.length < 4) return@forEach
                for (p in patterns) {
                    p.findAll(text).forEach { m ->
                        val snippet = m.value.trim().take(40)
                        if (snippet.length >= 4) out.add(snippet)
                    }
                }
            }
        return out.take(5).toList()
    }
}
