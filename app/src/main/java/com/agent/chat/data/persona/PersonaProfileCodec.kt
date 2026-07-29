package com.agent.chat.data.persona

import com.agent.chat.domain.model.EmojiFrequency
import com.agent.chat.domain.model.Formality
import com.agent.chat.domain.model.Initiative
import com.agent.chat.domain.model.PersonaCommunication
import com.agent.chat.domain.model.PersonaEmotion
import com.agent.chat.domain.model.PersonaIdentity
import com.agent.chat.domain.model.PersonaPersonality
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PersonaRelationship
import com.agent.chat.domain.model.SentenceLength
import com.agent.chat.domain.model.normalized
import com.agent.chat.domain.model.toPersonaScore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persona Engine 结构化人设 ↔ JSON。
 * 空串 / 非法 JSON 解码为 null；兼容 schema v1（0–1 浮点分数）。
 */
@Singleton
class PersonaProfileCodec @Inject constructor(
    moshi: Moshi,
) {
    private val wireAdapter = moshi.adapter(PersonaProfileWire::class.java)
    private val outAdapter = moshi.adapter(PersonaProfile::class.java)

    fun encode(profile: PersonaProfile?): String {
        if (profile == null) return ""
        return outAdapter.toJson(profile.normalized())
    }

    fun decode(json: String?): PersonaProfile? {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty() || raw == "{}" || raw == "null") return null
        val wire = runCatching { wireAdapter.fromJson(raw) }.getOrNull() ?: return null
        return wire.toDomain()?.normalized()
    }

    fun encodePretty(profile: PersonaProfile): String =
        outAdapter.indent("  ").toJson(profile.normalized())
}

@JsonClass(generateAdapter = false)
internal data class PersonaProfileWire(
    val schemaVersion: Int? = null,
    val identity: PersonaIdentityWire? = null,
    val personality: PersonaPersonalityWire? = null,
    val communication: PersonaCommunicationWire? = null,
    val emotion: PersonaEmotionWire? = null,
    val relationship: PersonaRelationshipWire? = null,
)

@JsonClass(generateAdapter = false)
internal data class PersonaIdentityWire(
    val name: String? = null,
    val role: String? = null,
    val description: String? = null,
)

@JsonClass(generateAdapter = false)
internal data class PersonaPersonalityWire(
    val warmth: Double? = null,
    val humor: Double? = null,
    val rationality: Double? = null,
    val empathy: Double? = null,
    val energy: Double? = null,
)

@JsonClass(generateAdapter = false)
internal data class PersonaCommunicationWire(
    val sentenceLength: String? = null,
    val emojiFrequency: String? = null,
    val formality: String? = null,
    val initiative: String? = null,
)

@JsonClass(generateAdapter = false)
internal data class PersonaEmotionWire(
    val expressionLevel: Double? = null,
    val dramaticLevel: Double? = null,
    val allowInnerMonologue: Boolean? = null,
    val constraints: List<String>? = null,
)

@JsonClass(generateAdapter = false)
internal data class PersonaRelationshipWire(
    val type: String? = null,
    val intimacyLevel: Double? = null,
)

private fun PersonaProfileWire.toDomain(): PersonaProfile? {
    val name = identity?.name?.trim().orEmpty()
    if (name.isEmpty()) return null
    val version = schemaVersion ?: 1
    val unit = version < 2
    val defaults = PersonaPersonality()
    val emotionDefaults = PersonaEmotion()
    return PersonaProfile(
        schemaVersion = version,
        identity = PersonaIdentity(
            name = name,
            role = identity?.role.orEmpty(),
            description = identity?.description.orEmpty(),
        ),
        personality = PersonaPersonality(
            warmth = personality?.warmth?.toPersonaScore(unit) ?: defaults.warmth,
            humor = personality?.humor?.toPersonaScore(unit) ?: defaults.humor,
            rationality = personality?.rationality?.toPersonaScore(unit) ?: defaults.rationality,
            empathy = personality?.empathy?.toPersonaScore(unit) ?: defaults.empathy,
            energy = personality?.energy?.toPersonaScore(unit) ?: defaults.energy,
        ),
        communication = PersonaCommunication(
            sentenceLength = parseEnum(communication?.sentenceLength, SentenceLength.MEDIUM),
            emojiFrequency = parseEnum(communication?.emojiFrequency, EmojiFrequency.RARE),
            formality = parseEnum(communication?.formality, Formality.CASUAL),
            initiative = parseEnum(communication?.initiative, Initiative.BALANCED),
        ),
        emotion = PersonaEmotion(
            expressionLevel = emotion?.expressionLevel?.toPersonaScore(unit)
                ?: emotionDefaults.expressionLevel,
            dramaticLevel = emotion?.dramaticLevel?.toPersonaScore(unit)
                ?: emotionDefaults.dramaticLevel,
            allowInnerMonologue = emotion?.allowInnerMonologue ?: false,
            constraints = emotion?.constraints.orEmpty(),
        ),
        relationship = PersonaRelationship(
            type = relationship?.type?.trim().orEmpty().ifBlank { "companion" },
            intimacyLevel = relationship?.intimacyLevel?.toPersonaScore(unit) ?: 50,
        ),
    )
}

private inline fun <reified E : Enum<E>> parseEnum(raw: String?, fallback: E): E {
    if (raw.isNullOrBlank()) return fallback
    return enumValues<E>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        ?: fallback
}
