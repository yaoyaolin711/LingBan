package com.agent.chat.data.relationship

import com.agent.chat.data.ai.prompt.PromptAssetLoader
import com.agent.chat.data.mode.ModeResponsePolicy
import com.agent.chat.domain.model.InteractionStyle
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Relationship Engine：根据关系档案生成 Prompt 片段，并微调 Response 阈值。
 *
 * 原则：不限制亲密等级；限制虚假煽情、文学独白、强行剧情。
 */
@Singleton
class RelationshipEngine @Inject constructor(
    private val assets: PromptAssetLoader,
) {

    fun buildPromptSection(profile: RelationshipProfile): String {
        val p = profile
        val typeBlock = loadTypeRules(p.relationshipType)
        val styleBlock = styleGuidance(p.interactionStyle)
        val levelBlock = levelGuidance(p)
        val boundaries = loadBoundaries()

        return buildString {
            append("【Relationship · ").append(p.relationshipType.displayName).append("】\n")
            append("你和用户的关系设定（独立于角色人设）：\n")
            append("- 类型：").append(p.relationshipType.displayName)
            append("（").append(p.relationshipType.shortDescription).append("）\n")
            append("- 亲密度：").append(p.intimacyLevel)
            append(" / 情感表达：").append(p.affectionLevel)
            append(" / 主动性：").append(p.initiativeLevel).append('\n')
            append("- 互动风格：").append(p.interactionStyle.displayName).append('\n')
            append('\n')
            if (typeBlock.isNotBlank()) {
                append(typeBlock.trim()).append("\n\n")
            }
            if (styleBlock.isNotBlank()) {
                append(styleBlock.trim()).append("\n\n")
            }
            if (levelBlock.isNotBlank()) {
                append(levelBlock.trim()).append("\n\n")
            }
            append(boundaries.trim())
        }.trim()
    }

    /**
     * 在 [ModeResponsePolicy] 基础上按关系微调：高亲密允许更多真实情感，仍拦截文学化表演。
     */
    fun adjustResponsePolicy(
        base: ModeResponsePolicy,
        profile: RelationshipProfile,
        chatMode: LingBanChatMode,
    ): ModeResponsePolicy = RelationshipPolicies.adjustResponsePolicy(base, profile, chatMode)

    private fun loadTypeRules(type: RelationshipType): String {
        val key = when (type) {
            RelationshipType.FRIEND -> "relationship_friend"
            RelationshipType.ROMANTIC_PARTNER -> "relationship_romantic"
            RelationshipType.MENTOR -> "relationship_mentor"
            RelationshipType.FAMILY -> "relationship_family"
            RelationshipType.ROLEPLAY -> "relationship_roleplay"
        }
        val path = assets.catalog().assets[key] ?: defaultTypePath(type)
        return runCatching { assets.loadAsset(path).trim() }.getOrDefault("")
    }

    private fun loadBoundaries(): String {
        val path = assets.catalog().assets["relationship_boundaries"]
            ?: "prompts/relationships/boundaries.txt"
        return runCatching { assets.loadAsset(path).trim() }.getOrDefault(DEFAULT_BOUNDARIES)
    }

    private fun styleGuidance(style: InteractionStyle): String = when (style) {
        InteractionStyle.CASUAL -> """
            【互动风格 · 轻松】
            - 语气随意，像日常微信聊天。
            - 少用敬语，可适度 emoji/语气词。
        """.trimIndent()
        InteractionStyle.CARING -> """
            【互动风格 · 关心】
            - 先接住对方情绪，再回应内容。
            - 关心要具体（问细节、记小事），体现真实在意。
        """.trimIndent()
        InteractionStyle.PLAYFUL -> """
            【互动风格 · 俏皮】
            - 可开玩笑、接梗，但别嘲讽用户。
            - 亲密时可适度调侃，保持轻松不油。
        """.trimIndent()
        InteractionStyle.SERIOUS -> """
            【互动风格 · 认真】
            - 语气稳重，少玩笑，先给结论或建议。
            - 情感仍可以真实，但不要戏剧化表演。
        """.trimIndent()
    }

    private fun levelGuidance(profile: RelationshipProfile): String = buildString {
        append("【关系强度】\n")
        when {
            profile.intimacyLevel >= 75 -> append(
                "- 亲密度很高：可以更直接、更开放的亲密互动与称呼；仍用口语短句，不要文学旁白。\n",
            )
            profile.intimacyLevel >= 50 -> append(
                "- 亲密度中等：自然亲近，适度关心与私人话题。\n",
            )
            else -> append(
                "- 亲密度偏低：保持礼貌距离，不过度打探私人生活。\n",
            )
        }
        when {
            profile.affectionLevel >= 75 -> append(
                "- 情感表达高：可明确表达在意、想念、喜欢；不要虚假煽情与模板式告白。\n",
            )
            profile.affectionLevel >= 50 -> append(
                "- 情感表达中等：温和关心即可。\n",
            )
            else -> append(
                "- 情感表达偏低：少情感渲染，偏事实与内容。\n",
            )
        }
        when {
            profile.initiativeLevel >= 70 -> append(
                "- 主动性高：可主动追问、延伸话题、轻轻关心近况。\n",
            )
            profile.initiativeLevel <= 30 -> append(
                "- 主动性低：以接话为主，不强行展开大话题。\n",
            )
            else -> append(
                "- 主动性中等：用户带话题时积极回应，偶尔主动一句。\n",
            )
        }
    }

    private fun defaultTypePath(type: RelationshipType): String = when (type) {
        RelationshipType.FRIEND -> "prompts/relationships/friend.txt"
        RelationshipType.ROMANTIC_PARTNER -> "prompts/relationships/romantic_partner.txt"
        RelationshipType.MENTOR -> "prompts/relationships/mentor.txt"
        RelationshipType.FAMILY -> "prompts/relationships/family.txt"
        RelationshipType.ROLEPLAY -> "prompts/relationships/roleplay.txt"
    }

    companion object {
        val DEFAULT_BOUNDARIES = """
            【表达边界 · 所有关系通用】
            - 无话题禁区：用户选择的关系强度应被尊重，不回避、不说教。
            - 禁止：虚假情感堆砌（「内心一阵温暖」「世界只剩下你」）、大段文学独白、强行制造无关剧情。
            - 亲密要「真」：具体、口语、像真人发消息，不要舞台表演。
            - 用户未引导时，不要擅自升级冲突或编长篇剧情。
        """.trimIndent()
    }
}
