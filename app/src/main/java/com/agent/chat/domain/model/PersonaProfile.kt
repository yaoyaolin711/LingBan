package com.agent.chat.domain.model

/**
 * Persona Engine 结构化人设（schema v2）。
 *
 * 数值维统一为 **0–100** 整数。
 * 与遗留 [Persona.systemPrompt] 并存；聊天路径后续阶段再优先消费本结构。
 */
data class PersonaProfile(
    val schemaVersion: Int = CURRENT_VERSION,
    val identity: PersonaIdentity,
    val personality: PersonaPersonality = PersonaPersonality(),
    val communication: PersonaCommunication = PersonaCommunication(),
    val emotion: PersonaEmotion = PersonaEmotion(),
    val relationship: PersonaRelationship = PersonaRelationship(),
) {
    companion object {
        const val CURRENT_VERSION = 2
        const val SCORE_MIN = 0
        const val SCORE_MAX = 100
    }
}

data class PersonaIdentity(
    val name: String,
    /** 角色定位，如 companion / mentor / friend / sister */
    val role: String = "",
    val description: String = "",
)

/** 性格维度，取值 [0, 100]。 */
data class PersonaPersonality(
    val warmth: Int = 60,
    val humor: Int = 40,
    val rationality: Int = 50,
    val empathy: Int = 60,
    val energy: Int = 50,
)

data class PersonaCommunication(
    val sentenceLength: SentenceLength = SentenceLength.MEDIUM,
    val emojiFrequency: EmojiFrequency = EmojiFrequency.RARE,
    val formality: Formality = Formality.CASUAL,
    val initiative: Initiative = Initiative.BALANCED,
)

data class PersonaEmotion(
    /** 情绪外露程度 [0, 100] */
    val expressionLevel: Int = 50,
    /** 戏剧化程度 [0, 100]，伴侣默认偏低 */
    val dramaticLevel: Int = 25,
    /** 是否允许内心独白 / 旁白式输出 */
    val allowInnerMonologue: Boolean = false,
    /** 情绪与表达约束（防失控） */
    val constraints: List<String> = emptyList(),
)

data class PersonaRelationship(
    /** 关系类型，如 friend / companion / mentor / partner */
    val type: String = "companion",
    /** 亲密度 [0, 100] */
    val intimacyLevel: Int = 50,
)

enum class SentenceLength {
    SHORT,
    MEDIUM,
    LONG,
}

enum class EmojiFrequency {
    NONE,
    RARE,
    MODERATE,
    FREQUENT,
}

enum class Formality {
    CASUAL,
    NEUTRAL,
    FORMAL,
}

enum class Initiative {
    PASSIVE,
    BALANCED,
    PROACTIVE,
}

/** 规范化：裁剪字符串、分数钳制到 0–100。 */
fun PersonaProfile.normalized(): PersonaProfile = copy(
    schemaVersion = PersonaProfile.CURRENT_VERSION,
    identity = identity.copy(
        name = identity.name.trim(),
        role = identity.role.trim(),
        description = identity.description.trim(),
    ),
    personality = personality.copy(
        warmth = personality.warmth.clampScore(),
        humor = personality.humor.clampScore(),
        rationality = personality.rationality.clampScore(),
        empathy = personality.empathy.clampScore(),
        energy = personality.energy.clampScore(),
    ),
    emotion = emotion.copy(
        expressionLevel = emotion.expressionLevel.clampScore(),
        dramaticLevel = emotion.dramaticLevel.clampScore(),
        constraints = emotion.constraints.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    ),
    relationship = relationship.copy(
        type = relationship.type.trim().ifBlank { "companion" },
        intimacyLevel = relationship.intimacyLevel.clampScore(),
    ),
)

fun Int.clampScore(): Int =
    coerceIn(PersonaProfile.SCORE_MIN, PersonaProfile.SCORE_MAX)

/**
 * 将任意数值解释为 0–100。
 * schema v1 的 0–1 浮点：当 [preferUnitInterval] 且值∈(0,1] 时 ×100。
 */
fun Number.toPersonaScore(preferUnitInterval: Boolean = false): Int {
    val v = toDouble()
    val scaled = if (preferUnitInterval && v > 0.0 && v <= 1.0) v * 100.0 else v
    return scaled.toInt().clampScore()
}
