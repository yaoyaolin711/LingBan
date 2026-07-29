package com.agent.chat.domain.model

/**
 * 表达风格档案：控制 AI **如何表达**，不限制是否拥有情感或亲密内容。
 *
 * 独立于 [PersonaProfile]（AI 是谁）与 [RelationshipProfile]（与用户什么关系）。
 */
data class ExpressionProfile(
    /** 真人聊天程度 [0, 100]：越高越口语、越像即时通讯 */
    val naturalness: Int = 90,
    /** 戏剧化程度 [0, 100]：允许的上限，非「必须有」 */
    val dramaticLevel: Int = 20,
    /** 文学化 / 诗意程度 [0, 100] */
    val poeticLevel: Int = 10,
    /** 幽默程度 [0, 100] */
    val humorLevel: Int = 60,
    /** Emoji 使用 [0, 100] */
    val emojiLevel: Int = 20,
    val sentenceLength: SentenceLength = SentenceLength.MEDIUM,
) {
    companion object {
        const val SCORE_MIN = 0
        const val SCORE_MAX = 100
    }
}

fun ExpressionProfile.normalized(): ExpressionProfile = copy(
    naturalness = naturalness.clampExpressionScore(),
    dramaticLevel = dramaticLevel.clampExpressionScore(),
    poeticLevel = poeticLevel.clampExpressionScore(),
    humorLevel = humorLevel.clampExpressionScore(),
    emojiLevel = emojiLevel.clampExpressionScore(),
)

fun Int.clampExpressionScore(): Int =
    coerceIn(ExpressionProfile.SCORE_MIN, ExpressionProfile.SCORE_MAX)
