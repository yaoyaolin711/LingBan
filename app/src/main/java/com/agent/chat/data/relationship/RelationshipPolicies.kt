package com.agent.chat.data.relationship

import com.agent.chat.data.mode.ModeResponsePolicy
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType

object RelationshipPolicies {

    fun adjustResponsePolicy(
        base: ModeResponsePolicy,
        profile: RelationshipProfile,
        chatMode: LingBanChatMode,
    ): ModeResponsePolicy {
        val intimacy = profile.intimacyLevel / 100f
        val affection = profile.affectionLevel / 100f

        return when (profile.relationshipType) {
            RelationshipType.ROMANTIC_PARTNER -> base.copy(
                dramaticCap = (base.dramaticCap + intimacy * 0.18f + affection * 0.12f)
                    .coerceAtMost(0.82f),
                monoCap = if (chatMode == LingBanChatMode.ROLEPLAY || profile.intimacyLevel >= 75) {
                    (base.monoCap + intimacy * 0.12f).coerceAtMost(0.55f)
                } else {
                    base.monoCap
                },
                emotionFloor = (base.emotionFloor - affection * 0.12f).coerceAtLeast(0.22f),
                charIdealMax = base.charIdealMax + (intimacy * 80).toInt(),
            )
            RelationshipType.ROLEPLAY -> base.copy(
                dramaticCap = (base.dramaticCap + intimacy * 0.15f).coerceAtMost(0.85f),
                monoCap = (base.monoCap + intimacy * 0.2f).coerceAtMost(0.7f),
                lengthFloor = (base.lengthFloor - intimacy * 0.08f).coerceAtLeast(0.25f),
                charIdealMax = base.charIdealMax + (intimacy * 100).toInt(),
                idealSentenceMax = base.idealSentenceMax + (intimacy * 3).toInt(),
            )
            RelationshipType.MENTOR -> base.copy(
                dramaticCap = base.dramaticCap.coerceAtMost(0.38f),
                monoCap = base.monoCap.coerceAtMost(0.22f),
                humanFloor = base.humanFloor + 0.04f,
            )
            RelationshipType.FAMILY -> base.copy(
                dramaticCap = (base.dramaticCap + affection * 0.1f).coerceAtMost(0.55f),
                emotionFloor = (base.emotionFloor - affection * 0.08f).coerceAtLeast(0.25f),
            )
            RelationshipType.FRIEND -> base.copy(
                dramaticCap = (base.dramaticCap + intimacy * 0.08f).coerceAtMost(0.5f),
            )
        }
    }
}
