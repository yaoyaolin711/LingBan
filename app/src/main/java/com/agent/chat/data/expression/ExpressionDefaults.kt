package com.agent.chat.data.expression

import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.normalized
import com.agent.chat.domain.model.InteractionStyle
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import com.agent.chat.domain.model.SentenceLength

/**
 * 根据 [RelationshipProfile] 推荐表达风格默认值。
 */
object ExpressionDefaults {

    fun recommend(relationship: RelationshipProfile): ExpressionProfile {
        val base = when (relationship.relationshipType) {
            RelationshipType.FRIEND -> ExpressionProfile(
                naturalness = 90,
                dramaticLevel = 20,
                poeticLevel = 10,
                humorLevel = 60,
                emojiLevel = 25,
                sentenceLength = SentenceLength.MEDIUM,
            )
            RelationshipType.ROMANTIC_PARTNER -> ExpressionProfile(
                naturalness = 90,
                dramaticLevel = 30,
                poeticLevel = 15,
                humorLevel = 50,
                emojiLevel = 35,
                sentenceLength = SentenceLength.MEDIUM,
            )
            RelationshipType.MENTOR -> ExpressionProfile(
                naturalness = 90,
                dramaticLevel = 5,
                poeticLevel = 5,
                humorLevel = 30,
                emojiLevel = 10,
                sentenceLength = SentenceLength.SHORT,
            )
            RelationshipType.FAMILY -> ExpressionProfile(
                naturalness = 88,
                dramaticLevel = 25,
                poeticLevel = 12,
                humorLevel = 55,
                emojiLevel = 30,
                sentenceLength = SentenceLength.MEDIUM,
            )
            RelationshipType.ROLEPLAY -> ExpressionProfile(
                naturalness = 75,
                dramaticLevel = 45,
                poeticLevel = 40,
                humorLevel = 45,
                emojiLevel = 20,
                sentenceLength = SentenceLength.MEDIUM,
            )
        }
        return adjustByInteraction(base, relationship.interactionStyle)
            .let { adjustByIntimacy(it, relationship) }
            .normalized()
    }

    private fun adjustByInteraction(
        profile: ExpressionProfile,
        style: InteractionStyle,
    ): ExpressionProfile = when (style) {
        InteractionStyle.CASUAL -> profile.copy(humorLevel = profile.humorLevel + 5)
        InteractionStyle.CARING -> profile.copy(
            dramaticLevel = profile.dramaticLevel + 5,
            poeticLevel = profile.poeticLevel + 3,
        )
        InteractionStyle.PLAYFUL -> profile.copy(
            humorLevel = profile.humorLevel + 15,
            emojiLevel = profile.emojiLevel + 10,
        )
        InteractionStyle.SERIOUS -> profile.copy(
            humorLevel = profile.humorLevel - 15,
            dramaticLevel = profile.dramaticLevel - 5,
            emojiLevel = profile.emojiLevel - 10,
        )
    }

    private fun adjustByIntimacy(
        profile: ExpressionProfile,
        relationship: RelationshipProfile,
    ): ExpressionProfile {
        if (relationship.intimacyLevel < 60) return profile
        val boost = ((relationship.intimacyLevel - 60) / 8).coerceAtMost(8)
        return profile.copy(
            dramaticLevel = profile.dramaticLevel + boost,
            poeticLevel = profile.poeticLevel + boost / 2,
        )
    }
}
