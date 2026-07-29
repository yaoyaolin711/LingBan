package com.agent.chat.data.ai

import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.PresetMessage
import com.agent.chat.data.provider.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Prompt 占位符、世界书、预设对话、时间提醒等工具。
 * System Prompt 正文组装请使用 [com.agent.chat.data.ai.prompt.PromptComposer]。
 */
object PromptContextInjector {

    /** 扫描近期消息时最多取多少条 */
    private const val LOREBOOK_SCAN_LIMIT = 12
    private const val LOREBOOK_MAX_ENTRIES = 8

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

    /**
     * 将预设示范对话转为 API 消息（占位符已替换）。
     */
    fun buildPresetChatMessages(
        persona: Persona?,
        userNickname: String,
    ): List<ChatMessage> {
        if (persona == null) return emptyList()
        return persona.presetMessages.mapNotNull { preset ->
            val content = applyPlaceholders(preset.content.trim(), persona, userNickname)
            if (content.isEmpty()) return@mapNotNull null
            when (preset.role.lowercase(Locale.ROOT)) {
                PresetMessage.ROLE_ASSISTANT, "assistant", "char", "bot" ->
                    ChatMessage.assistant(content)
                else -> ChatMessage.user(content)
            }
        }
    }

    fun matchLorebook(
        persona: Persona?,
        recentMessages: List<Message>,
        userNickname: String,
    ): List<String> {
        val entries = persona?.lorebookEntries.orEmpty().filter { it.enabled && it.content.isNotBlank() }
        if (entries.isEmpty()) return emptyList()

        val haystack = recentMessages
            .takeLast(LOREBOOK_SCAN_LIMIT)
            .joinToString("\n") { it.content }
        if (haystack.isBlank()) return emptyList()

        val matched = mutableListOf<String>()
        for (entry in entries) {
            if (matched.size >= LOREBOOK_MAX_ENTRIES) break
            val keys = entry.keys.map { it.trim() }.filter { it.isNotEmpty() }
            if (keys.isEmpty()) continue
            val hit = keys.any { key ->
                if (entry.caseSensitive) {
                    haystack.contains(key)
                } else {
                    haystack.contains(key, ignoreCase = true)
                }
            }
            if (hit) {
                matched.add(applyPlaceholders(entry.content.trim(), persona, userNickname))
            }
        }
        return matched
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
