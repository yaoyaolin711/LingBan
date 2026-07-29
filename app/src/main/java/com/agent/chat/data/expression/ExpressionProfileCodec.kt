package com.agent.chat.data.expression

import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.InteractionStyle
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import com.agent.chat.domain.model.SentenceLength
import com.agent.chat.domain.model.clampExpressionScore
import com.agent.chat.domain.model.normalized
import com.squareup.moshi.JsonClass
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpressionProfileCodec @Inject constructor() {

    fun encode(profile: ExpressionProfile): String {
        val p = profile.normalized()
        return """{"naturalness":${p.naturalness},"dramaticLevel":${p.dramaticLevel},"poeticLevel":${p.poeticLevel},"humorLevel":${p.humorLevel},"emojiLevel":${p.emojiLevel},"sentenceLength":"${p.sentenceLength.name.lowercase()}"}"""
    }

    fun decode(json: String?): ExpressionProfile? {
        if (json.isNullOrBlank()) return null
        return runCatching { parseManual(json.trim()) }.getOrNull()
    }

    private fun parseManual(json: String): ExpressionProfile {
        fun intField(key: String, default: Int): Int {
            val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
            return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()?.clampExpressionScore()
                ?: default
        }
        fun sentenceLength(): SentenceLength {
            val pattern = """"sentenceLength"\s*:\s*"([^"]*)"""".toRegex()
            val raw = pattern.find(json)?.groupValues?.getOrNull(1)?.lowercase()
            return when (raw) {
                "short" -> SentenceLength.SHORT
                "long" -> SentenceLength.LONG
                else -> SentenceLength.MEDIUM
            }
        }
        return ExpressionProfile(
            naturalness = intField("naturalness", 90),
            dramaticLevel = intField("dramaticLevel", 20),
            poeticLevel = intField("poeticLevel", 10),
            humorLevel = intField("humorLevel", 60),
            emojiLevel = intField("emojiLevel", 20),
            sentenceLength = sentenceLength(),
        ).normalized()
    }
}

@JsonClass(generateAdapter = true)
internal data class ExpressionProfileWire(
    val naturalness: Int = 90,
    val dramaticLevel: Int = 20,
    val poeticLevel: Int = 10,
    val humorLevel: Int = 60,
    val emojiLevel: Int = 20,
    val sentenceLength: String = "medium",
)

fun encodeExpressionProfile(profile: ExpressionProfile): String =
    ExpressionProfileCodec().encode(profile)

fun decodeExpressionProfile(json: String?): ExpressionProfile? =
    ExpressionProfileCodec().decode(json)
