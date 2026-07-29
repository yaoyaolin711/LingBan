package com.agent.chat.data.persona

import com.agent.chat.domain.model.EmojiFrequency
import com.agent.chat.domain.model.Formality
import com.agent.chat.domain.model.Initiative
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.SentenceLength

/**
 * 由结构化 [PersonaProfile] 生成可兼容现有聊天链路的 systemPrompt。
 */
object PersonaSystemPromptBuilder {

    fun build(profile: PersonaProfile, openingHint: String = ""): String {
        val id = profile.identity
        val p = profile.personality
        val c = profile.communication
        val e = profile.emotion
        val r = profile.relationship

        return buildString {
            append("你是 ").append(id.name.trim().ifBlank { "伙伴" }).append("。")
            if (id.role.isNotBlank()) {
                append("角色定位：").append(id.role.trim()).append("。")
            }
            append('\n')
            if (id.description.isNotBlank()) {
                append(id.description.trim()).append("\n\n")
            }

            append("【性格倾向 0-100】\n")
            append("- 温暖 ").append(p.warmth)
            append(" / 幽默 ").append(p.humor)
            append(" / 理性 ").append(p.rationality)
            append(" / 共情 ").append(p.empathy)
            append(" / 活跃 ").append(p.energy)
            append("\n\n")

            append("【沟通】\n")
            append("- 句子长度：").append(labelSentence(c.sentenceLength)).append('\n')
            append("- 表情频率：").append(labelEmoji(c.emojiFrequency)).append('\n')
            append("- 正式度：").append(labelFormality(c.formality)).append('\n')
            append("- 主动性：").append(labelInitiative(c.initiative)).append('\n')
            append('\n')

            append("【情绪】\n")
            append("- 情绪外露：").append(e.expressionLevel).append('\n')
            append("- 戏剧化：").append(e.dramaticLevel).append("（越低越像真人闲聊）\n")
            append("- 内心独白：").append(if (e.allowInnerMonologue) "允许（克制使用）" else "禁止").append('\n')
            if (e.constraints.isNotEmpty()) {
                append("- 约束：\n")
                e.constraints.forEach { append("  · ").append(it).append('\n') }
            }
            append('\n')

            append("【关系】\n")
            append("- 类型：").append(r.type.ifBlank { "companion" }).append('\n')
            append("- 亲密度：").append(r.intimacyLevel).append('\n')
            append('\n')

            append("【输出要求】\n")
            append("- 像真人即时通讯，短句口语，不要客服腔。\n")
            append("- 不要写 *动作*、舞台指示或大段旁白。\n")
            append("- 无话题禁区：用户聊什么都自然接上，不回避、不说教。\n")
            if (!e.allowInnerMonologue) {
                append("- 不要输出内心独白。\n")
            }
            if (openingHint.isNotBlank()) {
                append("\n开场气质参考：").append(openingHint.trim())
            }
        }.trim()
    }

    fun buildOpeningLine(profile: PersonaProfile): String {
        val name = profile.identity.name.trim().ifBlank { "我" }
        val warm = profile.personality.warmth
        return when {
            warm >= 70 -> "嗨，我是$name。今天想聊点什么？"
            warm >= 40 -> "你好，我是$name。我在这儿。"
            else -> "我是$name。说吧。"
        }
    }

    private fun labelSentence(v: SentenceLength) = when (v) {
        SentenceLength.SHORT -> "短"
        SentenceLength.MEDIUM -> "中"
        SentenceLength.LONG -> "长"
    }

    private fun labelEmoji(v: EmojiFrequency) = when (v) {
        EmojiFrequency.NONE -> "无"
        EmojiFrequency.RARE -> "很少"
        EmojiFrequency.MODERATE -> "适中"
        EmojiFrequency.FREQUENT -> "较多"
    }

    private fun labelFormality(v: Formality) = when (v) {
        Formality.CASUAL -> "随意"
        Formality.NEUTRAL -> "中性"
        Formality.FORMAL -> "正式"
    }

    private fun labelInitiative(v: Initiative) = when (v) {
        Initiative.PASSIVE -> "偏被动"
        Initiative.BALANCED -> "平衡"
        Initiative.PROACTIVE -> "偏主动"
    }
}
