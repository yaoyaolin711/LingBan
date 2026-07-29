package com.agent.chat.data.conversationstate

import com.agent.chat.data.interaction.InteractionIntent
import com.agent.chat.data.interaction.InteractionIntentDetector
import com.agent.chat.domain.model.ConversationStateKind
import com.agent.chat.domain.model.LingBanChatMode

/**
 * 将用户输入映射为候选交流状态及得分。
 */
data class StateCandidate(
    val state: ConversationStateKind,
    val score: Float,
    val trigger: String,
)

object ConversationStateIntentDetector {

    fun detect(
        userMessage: String,
        chatMode: LingBanChatMode,
    ): List<StateCandidate> {
        val text = userMessage.trim()
        if (text.isBlank()) {
            return listOf(StateCandidate(ConversationStateKind.NORMAL, 0.5f, "empty_message"))
        }

        val intents = InteractionIntentDetector.detect(text)
        val candidates = mutableListOf<StateCandidate>()

        if (InteractionIntent.TECHNICAL in intents) {
            candidates += StateCandidate(
                state = ConversationStateKind.KNOWLEDGE,
                score = 0.85f,
                trigger = "intent:technical",
            )
        }

        if (chatMode == LingBanChatMode.ROLEPLAY || InteractionIntent.ROLEPLAY in intents) {
            candidates += StateCandidate(
                state = ConversationStateKind.ROLEPLAY,
                score = if (chatMode == LingBanChatMode.ROLEPLAY) 0.9f else 0.75f,
                trigger = "intent:roleplay",
            )
        }

        if (isPlayful(text)) {
            candidates += StateCandidate(
                state = ConversationStateKind.PLAYFUL,
                score = playfulScore(text),
                trigger = "intent:playful",
            )
        }

        if (candidates.isEmpty()) {
            candidates += StateCandidate(
                state = ConversationStateKind.NORMAL,
                score = 0.45f,
                trigger = "intent:general",
            )
        }

        return candidates.sortedByDescending { it.score }
    }

    private fun isPlayful(text: String): Boolean {
        val patterns = listOf(
            "哈哈", "hhh", "笑死", "好玩", "有趣", "逗", "梗", "皮一下", "开玩笑",
            "整活", "乐", "搞笑", "沙雕", "666", "牛啊",
        )
        return patterns.any { it in text } ||
            Regex("""[😂🤣😆😜🙃]""").containsMatchIn(text)
    }

    private fun playfulScore(text: String): Float {
        var score = 0.55f
        if ("哈哈" in text || "笑" in text) score += 0.15f
        if (Regex("""[😂🤣😆]""").containsMatchIn(text)) score += 0.1f
        return score.coerceIn(0.5f, 0.9f)
    }
}
