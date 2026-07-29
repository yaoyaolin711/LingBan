package com.agent.chat.data.relationship

import com.agent.chat.domain.model.InteractionStyle
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import com.agent.chat.domain.model.clampRelationshipScore
import com.agent.chat.domain.model.normalized
import com.squareup.moshi.JsonClass
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelationshipProfileCodec @Inject constructor() {

    fun encode(profile: RelationshipProfile): String {
        val wire = profile.normalized().toWire()
        return """
            {
              "relationshipType":"${wire.relationshipType}",
              "intimacyLevel":${wire.intimacyLevel},
              "affectionLevel":${wire.affectionLevel},
              "interactionStyle":"${wire.interactionStyle}",
              "initiativeLevel":${wire.initiativeLevel}
            }
        """.trimIndent().replace("\n", "").replace(" ", "")
    }

    fun decode(json: String?): RelationshipProfile {
        if (json.isNullOrBlank()) return RelationshipProfile()
        return runCatching {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) return RelationshipProfile()
            parseManual(trimmed)
        }.getOrDefault(RelationshipProfile())
    }

    private fun parseManual(json: String): RelationshipProfile {
        fun stringField(key: String): String? {
            val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
            return pattern.find(json)?.groupValues?.getOrNull(1)
        }
        fun intField(key: String, default: Int): Int {
            val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
            return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()?.clampRelationshipScore()
                ?: default
        }
        return RelationshipProfile(
            relationshipType = RelationshipType.fromStorage(stringField("relationshipType")),
            intimacyLevel = intField("intimacyLevel", 50),
            affectionLevel = intField("affectionLevel", 50),
            interactionStyle = InteractionStyle.fromStorage(stringField("interactionStyle")),
            initiativeLevel = intField("initiativeLevel", 50),
        ).normalized()
    }

    private fun RelationshipProfile.toWire() = RelationshipProfileWire(
        relationshipType = relationshipType.storageKey,
        intimacyLevel = intimacyLevel,
        affectionLevel = affectionLevel,
        interactionStyle = interactionStyle.storageKey,
        initiativeLevel = initiativeLevel,
    )
}

@JsonClass(generateAdapter = true)
internal data class RelationshipProfileWire(
    val relationshipType: String = RelationshipType.FRIEND.storageKey,
    val intimacyLevel: Int = 50,
    val affectionLevel: Int = 50,
    val interactionStyle: String = InteractionStyle.CASUAL.storageKey,
    val initiativeLevel: Int = 50,
)

fun encodeRelationshipProfile(profile: RelationshipProfile): String =
    RelationshipProfileCodec().encode(profile)

fun decodeRelationshipProfile(json: String?): RelationshipProfile =
    RelationshipProfileCodec().decode(json)
