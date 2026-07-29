package com.agent.chat.data.expression

import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.SentenceLength
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Expression Style Engine：生成 Prompt 表达风格段。
 */
@Singleton
class ExpressionEngine @Inject constructor() {

    fun buildPromptSection(profile: ExpressionProfile): String = buildString {
        append("【Expression Style · 表达风格】\n")
        append("控制「怎么说」，不限制「有没有情感或亲密内容」。\n")
        append("当前设定（0-100）：\n")
        append("- 真人感 naturalness：").append(profile.naturalness).append('\n')
        append("- 戏剧化 dramatic：").append(profile.dramaticLevel).append('\n')
        append("- 文学化 poetic：").append(profile.poeticLevel).append('\n')
        append("- 幽默 humor：").append(profile.humorLevel).append('\n')
        append("- Emoji：").append(profile.emojiLevel).append('\n')
        append("- 句长：").append(labelSentence(profile.sentenceLength)).append("\n\n")

        append("执行要点：\n")
        append(naturalnessGuide(profile.naturalness)).append('\n')
        append(dramaticGuide(profile.dramaticLevel)).append('\n')
        append(poeticGuide(profile.poeticLevel)).append('\n')
        append(humorGuide(profile.humorLevel)).append('\n')
        append(emojiGuide(profile.emojiLevel)).append("\n\n")

        append("风格示例：\n")
        append("✓ 允许：「今天辛苦了，早点休息」（真人、具体、有温度）\n")
        append("✗ 不推荐：「我的世界仿佛因为你的疲惫而失去颜色」（文学隐喻+戏剧化，超出低 poetic/dramatic 设定）\n")
        append("✗ 不推荐：「我的内心一阵温暖，此刻静静陪伴」（虚假煽情+旁白式独白）\n")
    }.trim()

    private fun labelSentence(v: SentenceLength) = when (v) {
        SentenceLength.SHORT -> "短"
        SentenceLength.MEDIUM -> "中"
        SentenceLength.LONG -> "长"
    }

    private fun naturalnessGuide(level: Int): String = when {
        level >= 80 -> "- 真人感高：口语短句，像微信聊天；避免客服腔与论文腔。"
        level >= 50 -> "- 真人感中：自然对话，适度书面即可。"
        else -> "- 真人感低：可稍正式，但仍要清晰可读。"
    }

    private fun dramaticGuide(level: Int): String = when {
        level <= 15 -> "- 戏剧化极低：不要夸张表演、哭腔、排比煽情。"
        level <= 35 -> "- 戏剧化偏低：情绪真实即可，不要舞台化。"
        level <= 60 -> "- 戏剧化中等：可有适度情绪渲染，但仍以对话为主。"
        else -> "- 戏剧化偏高：允许更强烈的情绪与氛围，但不要空泛自我感动。"
    }

    private fun poeticGuide(level: Int): String = when {
        level <= 15 -> "- 文学化极低：禁止「仿佛/如同/宛如」隐喻链与散文式铺陈。"
        level <= 35 -> "- 文学化偏低：少比喻，直说即可。"
        level <= 60 -> "- 文学化中等：可少量氛围描写，不要整段诗意。"
        else -> "- 文学化偏高：可适度文学表达，仍以可聊、可接话为先。"
    }

    private fun humorGuide(level: Int): String = when {
        level <= 25 -> "- 幽默低：少开玩笑，保持稳重。"
        level <= 55 -> "- 幽默中：可偶尔轻松一句。"
        else -> "- 幽默高：可接梗、调侃，但不要嘲讽用户。"
    }

    private fun emojiGuide(level: Int): String = when {
        level <= 15 -> "- Emoji 很少或不用。"
        level <= 45 -> "- Emoji 适度点缀。"
        else -> "- Emoji 可以较多，但别刷屏。"
    }
}
