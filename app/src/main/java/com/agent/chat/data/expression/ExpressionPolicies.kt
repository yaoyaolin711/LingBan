package com.agent.chat.data.expression

import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.SentenceLength

/**
 * Response Controller 用的表达风格阈值（由 [ExpressionProfile] 推导）。
 *
 * 检查「表达是否符合用户选择的风格」，**不**检查「有没有情感」。
 */
data class ExpressionStylePolicy(
    val naturalnessFloor: Float,
    val dramaticCap: Float,
    val poeticCap: Float,
    val humorCap: Float,
    val monoCap: Float,
    val lengthFloor: Float = 0.4f,
    val idealSentenceMax: Int,
    val charIdealMax: Int,
    val emojiCap: Float,
)

object ExpressionPolicies {

    fun fromProfile(profile: ExpressionProfile): ExpressionStylePolicy {
        val n = profile.naturalness / 100f
        val d = profile.dramaticLevel / 100f
        val p = profile.poeticLevel / 100f
        val h = profile.humorLevel / 100f
        val e = profile.emojiLevel / 100f

        return ExpressionStylePolicy(
            naturalnessFloor = (n * 0.5f + 0.22f).coerceIn(0.25f, 0.82f),
            dramaticCap = (d + 0.1f).coerceIn(0.12f, 0.92f),
            poeticCap = (p + 0.1f).coerceIn(0.1f, 0.88f),
            humorCap = (h + 0.2f).coerceIn(0.25f, 0.98f),
            monoCap = (0.4f - d * 0.12f - p * 0.18f).coerceIn(0.1f, 0.68f),
            lengthFloor = 0.38f,
            idealSentenceMax = when (profile.sentenceLength) {
                SentenceLength.SHORT -> 3
                SentenceLength.MEDIUM -> 5
                SentenceLength.LONG -> 9
            },
            charIdealMax = when (profile.sentenceLength) {
                SentenceLength.SHORT -> 140
                SentenceLength.MEDIUM -> 240
                SentenceLength.LONG -> 420
            },
            emojiCap = (e / 100f + 0.15f).coerceIn(0.15f, 0.95f),
        )
    }
}
