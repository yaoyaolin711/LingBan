package com.agent.chat.data.conversationstate

import com.agent.chat.data.mode.ModeResponsePolicy
import com.agent.chat.domain.model.ConversationStateKind
import com.agent.chat.domain.model.ConversationStateSnapshot

/**
 * 按运行时状态微调 Response 阈值（轻微、不覆盖 Persona）。
 */
object ConversationStatePolicies {

    fun adjustResponsePolicy(
        base: ModeResponsePolicy,
        snapshot: ConversationStateSnapshot,
    ): ModeResponsePolicy {
        if (!snapshot.isActive()) return base
        val boost = snapshot.confidence.coerceIn(0.35f, 1f)

        return when (snapshot.currentState) {
            ConversationStateKind.EMOTIONAL_SUPPORT -> base.copy(
                humanFloor = (base.humanFloor + 0.06f * boost).coerceAtMost(0.88f),
                emotionFloor = (base.emotionFloor - 0.08f * boost).coerceAtLeast(0.18f),
                dramaticCap = (base.dramaticCap + 0.05f * boost).coerceAtMost(0.75f),
            )
            ConversationStateKind.KNOWLEDGE -> base.copy(
                dramaticCap = (base.dramaticCap - 0.12f * boost).coerceAtLeast(0.2f),
                monoCap = (base.monoCap - 0.08f * boost).coerceAtLeast(0.1f),
                lengthFloor = (base.lengthFloor + 0.05f * boost).coerceAtMost(0.65f),
                charIdealMax = base.charIdealMax + (boost * 60).toInt(),
            )
            ConversationStateKind.PLAYFUL -> base.copy(
                humanFloor = (base.humanFloor + 0.04f * boost).coerceAtMost(0.9f),
                dramaticCap = (base.dramaticCap + 0.08f * boost).coerceAtMost(0.8f),
            )
            ConversationStateKind.ROLEPLAY -> base.copy(
                dramaticCap = (base.dramaticCap + 0.1f * boost).coerceAtMost(0.85f),
                monoCap = (base.monoCap + 0.08f * boost).coerceAtMost(0.65f),
                idealSentenceMax = base.idealSentenceMax + (boost * 2).toInt(),
            )
            ConversationStateKind.NORMAL -> base
        }
    }
}
