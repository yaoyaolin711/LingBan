package com.agent.chat.domain.model

/**
 * 用户与 AI 的关系设定，独立于 [PersonaProfile]。
 *
 * - Persona：AI 是谁
 * - Relationship：AI 和用户是什么关系、如何互动
 */
data class RelationshipProfile(
    val relationshipType: RelationshipType = RelationshipType.FRIEND,
    /** 亲密度 [0, 100]：身体/情感距离感，可高可低，不限制开放互动 */
    val intimacyLevel: Int = 50,
    /** 情感表达 [0, 100]：关心、称呼、暧昧/依恋程度 */
    val affectionLevel: Int = 50,
    val interactionStyle: InteractionStyle = InteractionStyle.CASUAL,
    /** 主动性 [0, 100]：是否主动关心、追问、发起话题 */
    val initiativeLevel: Int = 50,
) {
    companion object {
        const val SCORE_MIN = 0
        const val SCORE_MAX = 100
    }
}

enum class RelationshipType(
    val storageKey: String,
    val displayName: String,
    val shortDescription: String,
) {
    FRIEND("friend", "朋友", "自然聊天、平等互动"),
    ROMANTIC_PARTNER("romantic_partner", "恋人", "更多关心与亲密表达"),
    MENTOR("mentor", "导师", "指导、点拨、结构化建议"),
    FAMILY("family", "家人", "亲近、包容、长期陪伴感"),
    ROLEPLAY("roleplay", "角色关系", "允许剧情与角色互动，仍以对话为主"),
    ;

    companion object {
        fun fromStorage(key: String?): RelationshipType {
            if (key.isNullOrBlank()) return FRIEND
            return entries.find { it.storageKey.equals(key, ignoreCase = true) } ?: FRIEND
        }
    }
}

enum class InteractionStyle(
    val storageKey: String,
    val displayName: String,
) {
    CASUAL("casual", "轻松"),
    CARING("caring", "关心"),
    PLAYFUL("playful", "俏皮"),
    SERIOUS("serious", "认真"),
    ;

    companion object {
        fun fromStorage(key: String?): InteractionStyle {
            if (key.isNullOrBlank()) return CASUAL
            return entries.find { it.storageKey.equals(key, ignoreCase = true) } ?: CASUAL
        }
    }
}

fun RelationshipProfile.normalized(): RelationshipProfile = copy(
    intimacyLevel = intimacyLevel.clampRelationshipScore(),
    affectionLevel = affectionLevel.clampRelationshipScore(),
    initiativeLevel = initiativeLevel.clampRelationshipScore(),
)

fun Int.clampRelationshipScore(): Int =
    coerceIn(RelationshipProfile.SCORE_MIN, RelationshipProfile.SCORE_MAX)
