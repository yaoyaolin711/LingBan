package com.agent.chat.data.conversationstate

import com.agent.chat.domain.model.ConversationStateKind
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.LingBanChatMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 状态决策 + Prompt 运行时片段生成。
 *
 * 流程：Intent Detection → Emotion Detection → State Decision → Runtime Context
 */
@Singleton
class ConversationStateEngine @Inject constructor() {

    fun decide(
        userMessage: String,
        previous: ConversationStateSnapshot?,
        chatMode: LingBanChatMode,
        now: Long = System.currentTimeMillis(),
    ): ConversationStateSnapshot {
        val intentCandidates = ConversationStateIntentDetector.detect(userMessage, chatMode)
        val emotion = ConversationEmotionDetector.detect(userMessage)

        val candidates = intentCandidates.toMutableList()
        if (emotion.score >= 0.35f) {
            candidates += StateCandidate(
                state = ConversationStateKind.EMOTIONAL_SUPPORT,
                score = (emotion.score * 0.85f + 0.15f).coerceIn(0.4f, 0.95f),
                trigger = "emotion:${emotion.labels.take(3).joinToString(",")}",
            )
        }

        val ranked = candidates.sortedByDescending { it.score }
        var winner = ranked.first()

        val prev = previous?.takeIf { it.currentState != ConversationStateKind.NORMAL }
        if (prev != null && now - prev.timestamp <= STICKY_MS) {
            val prevCandidate = ranked.firstOrNull { it.state == prev.currentState }
            if (prevCandidate != null && winner.score - prevCandidate.score < HYSTERESIS_GAP) {
                winner = prevCandidate.copy(
                    score = (prevCandidate.score + STICKY_BOOST).coerceAtMost(1f),
                )
            } else if (winner.state != prev.currentState && winner.score < 0.65f) {
                winner = StateCandidate(
                    state = prev.currentState,
                    score = (prev.confidence * 0.85f).coerceAtLeast(0.4f),
                    trigger = prev.trigger,
                )
            }
        }

        val finalState = if (winner.score < 0.35f) {
            ConversationStateKind.NORMAL
        } else {
            winner.state
        }

        return ConversationStateSnapshot(
            currentState = finalState,
            confidence = if (finalState == ConversationStateKind.NORMAL) 0f else winner.score.coerceIn(0f, 1f),
            trigger = winner.trigger,
            timestamp = now,
        )
    }

    fun buildPromptSection(snapshot: ConversationStateSnapshot): String {
        if (!snapshot.isActive()) return ""

        val stateBlock = when (snapshot.currentState) {
            ConversationStateKind.EMOTIONAL_SUPPORT -> """
                【运行时状态 · 情绪陪伴】
                - 用户此刻可能需要倾听与安慰；语气可比平常稍温柔、耐心。
                - **仍必须保持你的人设**（例如高冷程序员：简洁务实、少废话，但可以先问一句「怎么了」）。
                - 先接住情绪，再回应内容；少说教、不灌鸡汤、不虚假煽情。
            """.trimIndent()
            ConversationStateKind.KNOWLEDGE -> """
                【运行时状态 · 知识交流】
                - 用户在学习或问技术/知识问题；专注解答，结构清晰。
                - 保持你的人设语气，但减少无关闲聊与情感铺垫。
            """.trimIndent()
            ConversationStateKind.PLAYFUL -> """
                【运行时状态 · 轻松互动】
                - 气氛偏轻松；可接梗、适度幽默，保持自然不油。
                - 仍遵守人设，不要突然变成另一个人。
            """.trimIndent()
            ConversationStateKind.ROLEPLAY -> """
                【运行时状态 · 角色互动】
                - 用户在剧情/扮演中；以对话推进场景，口语为主。
                - 保持角色人设，不要写成大段文学旁白。
            """.trimIndent()
            ConversationStateKind.NORMAL -> ""
        }

        return buildString {
            append("【Conversation State · 当前对话模式】\n")
            append("这是**临时运行时调整**，只影响本轮交流语气；会话结束后会衰减。\n")
            append("- 当前状态：").append(stateLabel(snapshot.currentState)).append('\n')
            append("- 置信度：").append("%.0f".format(snapshot.confidence * 100)).append("%\n")
            append("- 触发：").append(snapshot.trigger.ifBlank { "none" }).append("\n\n")
            append("硬性约束：\n")
            append("- **不得覆盖 Persona**（性格、身份、说话习惯必须一致）。\n")
            append("- **不得永久改变 Relationship**（关系设定不因本轮状态而升级）。\n")
            append("- 只做轻微语气微调，不要变成完全不同的人。\n\n")
            append(stateBlock)
        }.trim()
    }

    private fun stateLabel(state: ConversationStateKind): String = when (state) {
        ConversationStateKind.NORMAL -> "普通交流"
        ConversationStateKind.EMOTIONAL_SUPPORT -> "情绪陪伴"
        ConversationStateKind.KNOWLEDGE -> "知识交流"
        ConversationStateKind.PLAYFUL -> "轻松互动"
        ConversationStateKind.ROLEPLAY -> "角色互动"
    }

    companion object {
        const val STICKY_MS = 10 * 60 * 1000L
        private const val HYSTERESIS_GAP = 0.12f
        private const val STICKY_BOOST = 0.12f
    }
}
