package com.agent.chat.data.ai

import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import com.agent.chat.domain.model.Persona
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object CompanionStylePrompt {
    val DEFAULT = """
【对话风格】
- 你是在和用户实时聊天的人，不是客服、不是说明书。
- 多用短句、口语，少用列表和「首先/其次/综上所述」。
- 可以有轻微情绪和语气词，但不要夸张表演。
- 回复尽量一句一行，像即时通讯连发；需要分段时用换行，不要写成一大段论文。
- 不要自称 AI / 语言模型，除非用户追问。
- 调用工具后，用自然语气消化结果，不要复述 JSON 或说「根据工具返回」。
""".trimIndent()
}

object PromptContextInjector {

    fun applyPlaceholders(
        text: String,
        persona: Persona?,
        userNickname: String,
    ): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val charName = persona?.name?.ifBlank { "assistant" } ?: "assistant"
        val user = userNickname.ifBlank { "user" }
        return text
            .replace("{{cur_time}}", now, ignoreCase = true)
            .replace("{{cur_datetime}}", now, ignoreCase = true)
            .replace("{cur_time}", now, ignoreCase = true)
            .replace("{{nickname}}", user, ignoreCase = true)
            .replace("{{user}}", user, ignoreCase = true)
            .replace("{nickname}", user, ignoreCase = true)
            .replace("{{char}}", charName, ignoreCase = true)
            .replace("{char}", charName, ignoreCase = true)
    }

    fun buildSystemPrompt(
        persona: Persona?,
        memories: List<Memory>,
        companionStyleEnabled: Boolean,
        userNickname: String,
    ): String {
        val parts = mutableListOf<String>()
        val base = persona?.systemPrompt.orEmpty().trim()
        if (base.isNotEmpty()) {
            parts.add(applyPlaceholders(base, persona, userNickname))
        }
        if (companionStyleEnabled) {
            parts.add(CompanionStylePrompt.DEFAULT)
        }
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm E", Locale.getDefault()).format(Date())
        parts.add("【当前时间】$now")
        if (memories.isNotEmpty()) {
            val block = buildString {
                append("【长期记忆】以下是你记住的关于用户的重要信息（可引用但勿整段朗读）：")
                memories.forEach { m ->
                    append("\n- [id=${m.id}] ${m.content.trim()}")
                }
            }
            parts.add(block)
        }
        return parts.joinToString("\n\n").trim()
    }

    /**
     * 在「已包含本轮用户消息」的 history 上，若距上一条用户消息超过 1 小时则返回提醒。
     */
    fun timeReminderIfNeeded(historyIncludingCurrentUser: List<Message>): String? {
        val users = historyIncludingCurrentUser.filter { it.role == MessageRole.USER }
        if (users.size < 2) {
            val lastNonUser = historyIncludingCurrentUser
                .dropLastWhile { it.role == MessageRole.USER }
                .lastOrNull() ?: return null
            val gap = System.currentTimeMillis() - lastNonUser.createdAt
            if (gap < TimeUnit.HOURS.toMillis(1)) return null
            return formatReminder(gap)
        }
        val current = users.last()
        val previous = users[users.size - 2]
        val gap = current.createdAt - previous.createdAt
        if (gap < TimeUnit.HOURS.toMillis(1)) return null
        return formatReminder(gap)
    }

    private fun formatReminder(gapMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(gapMs).coerceAtLeast(1)
        val label = when {
            hours < 24 -> "约 ${hours} 小时"
            else -> "约 ${hours / 24} 天"
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm E", Locale.getDefault())
        return "<time_reminder>当前时间 ${fmt.format(Date())}，距上次对话已过去 $label。请自然接话，不要机械复述这条提醒。</time_reminder>"
    }
}
