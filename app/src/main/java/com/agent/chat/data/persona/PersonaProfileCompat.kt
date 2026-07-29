package com.agent.chat.data.persona

import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.PersonaCommunication
import com.agent.chat.domain.model.PersonaEmotion
import com.agent.chat.domain.model.PersonaIdentity
import com.agent.chat.domain.model.PersonaPersonality
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PersonaRelationship
import com.agent.chat.domain.model.normalized

/**
 * 遗留自然语言人设 → 结构化 [PersonaProfile] 的兼容桥。
 */
object PersonaProfileCompat {

    fun resolve(persona: Persona): PersonaProfile =
        persona.profile?.normalized() ?: fromLegacy(
            name = persona.name,
            description = persona.description,
            systemPrompt = persona.systemPrompt,
        )

    fun fromLegacy(
        name: String,
        description: String = "",
        systemPrompt: String = "",
        role: String = "companion",
    ): PersonaProfile {
        val trimmedName = name.trim().ifBlank { "助手" }
        val desc = description.trim().ifBlank {
            systemPrompt.trim().lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?.take(160)
                .orEmpty()
        }
        return PersonaProfile(
            identity = PersonaIdentity(
                name = trimmedName,
                role = role.trim().ifBlank { "companion" },
                description = desc,
            ),
            personality = PersonaPersonality(),
            communication = PersonaCommunication(),
            emotion = PersonaEmotion(),
            relationship = PersonaRelationship(),
        ).normalized()
    }
}
