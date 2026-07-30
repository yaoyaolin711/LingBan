package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.companion.model.CompanionCharacterCard
import me.rerere.rikkahub.data.model.Assistant
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class CharacterManager {
    private val assistantCache = ConcurrentHashMap<Uuid, CacheEntry>()

    fun getCharacter(assistant: Assistant): CompanionCharacterCard? {
        if (!assistant.enableCompanion) {
            assistantCache.remove(assistant.id)
            return null
        }

        val signature = signatureOf(assistant.companionCharacter)
        val cached = assistantCache[assistant.id]
        if (cached != null && cached.signature == signature) return cached.character

        val resolved = assistant.companionCharacter
            ?.takeIf { it.name.isNotBlank() || it.systemPrompt.isNotBlank() }
        assistantCache[assistant.id] = CacheEntry(signature = signature, character = resolved)
        return resolved
    }

    fun preload(assistant: Assistant) {
        getCharacter(assistant)
    }

    fun invalidate(assistantId: Uuid) {
        assistantCache.remove(assistantId)
    }

    private fun signatureOf(character: CompanionCharacterCard?): String {
        if (character == null) return "none"
        return buildString {
            append(character.id)
            append(':')
            append(character.updatedAtEpochMillis)
            append(':')
            append(character.name.hashCode())
            append(':')
            append(character.systemPrompt.hashCode())
        }
    }

    private data class CacheEntry(
        val signature: String,
        val character: CompanionCharacterCard?,
    )
}
