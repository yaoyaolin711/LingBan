package me.rerere.rikkahub.data.companion

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.companion.model.CompanionState

class MemoryManager {
    fun updateState(
        currentState: CompanionState,
        messages: List<UIMessage>,
    ): CompanionState {
        val userMessages = messages.filter { it.role == MessageRole.USER }
        val previousCount = currentState.lastAnalyzedUserMessageCount
        val deltaCount = userMessages.size - previousCount
        if (deltaCount <= 0) return currentState
        if (deltaCount < MIN_USER_MESSAGES_PER_UPDATE && currentState.mediumMemorySummary.isNotBlank()) {
            // Skip tiny deltas to avoid high-frequency memory recomputation on mobile.
            return currentState
        }

        val deltaUserMessages = userMessages.drop(previousCount)
        val extractedFacts = mergeFactsWithOverride(
            existingFacts = currentState.longMemoryFacts,
            newFacts = deltaUserMessages.flatMap { extractFacts(it.toText()) }
        )

        val summary = buildRecentSummary(messages)
        return currentState.copy(
            mediumMemorySummary = summary,
            mediumMemoryUpdatedAtEpochMillis = System.currentTimeMillis(),
            lastAnalyzedUserMessageCount = userMessages.size,
            longMemoryFacts = extractedFacts,
            memoryVersion = currentState.memoryVersion + 1,
        )
    }

    private fun buildRecentSummary(messages: List<UIMessage>): String {
        if (messages.isEmpty()) return ""
        return messages.takeLast(RECENT_SUMMARY_MESSAGES)
            .mapNotNull { message ->
                val text = message.toText().trim()
                if (text.isBlank()) null else "${message.role.name.lowercase()}: ${text.take(SUMMARY_LINE_LIMIT)}"
            }
            .joinToString("\n")
            .take(MAX_SUMMARY_CHARS)
    }

    private fun extractFacts(text: String): List<String> {
        val normalized = text.trim()
        if (normalized.isBlank()) return emptyList()

        return FACT_PATTERNS.mapNotNull { regex ->
            regex.find(normalized)?.value?.trim()?.takeIf { it.length >= 4 }
        }
    }

    private fun mergeFactsWithOverride(
        existingFacts: List<String>,
        newFacts: List<String>,
    ): List<String> {
        if (newFacts.isEmpty()) return existingFacts

        val merged = LinkedHashMap<String, String>()
        existingFacts.forEach { fact ->
            merged[factKey(fact)] = fact
        }
        // New facts override old facts in the same bucket.
        newFacts.forEach { fact ->
            merged[factKey(fact)] = fact
        }
        return merged.values.toList().takeLast(MAX_LONG_MEMORY_FACTS)
    }

    private fun factKey(fact: String): String {
        val normalized = fact.lowercase()
        return when {
            normalized.contains("喜欢") || normalized.contains("i like") -> "preference_like"
            normalized.contains("不喜欢") || normalized.contains("讨厌") || normalized.contains("don't like") -> "preference_dislike"
            normalized.contains("生日") || normalized.contains("birthday") -> "profile_birthday"
            normalized.contains("习惯") || normalized.contains("通常会") || normalized.contains("i usually") -> "habit"
            else -> "generic:${normalized.take(16)}"
        }
    }

    private companion object {
        const val MIN_USER_MESSAGES_PER_UPDATE = 2
        const val RECENT_SUMMARY_MESSAGES = 6
        const val SUMMARY_LINE_LIMIT = 120
        const val MAX_SUMMARY_CHARS = 800
        const val MAX_LONG_MEMORY_FACTS = 12
        val FACT_PATTERNS = listOf(
            Regex("""(?:我喜欢|我很喜欢)(.{1,24})"""),
            Regex("""(?:我不喜欢|我讨厌)(.{1,24})"""),
            Regex("""(?:我的生日是)(.{1,24})"""),
            Regex("""(?:我习惯|我通常会)(.{1,24})"""),
            Regex("""(?:I like)(.{1,24})""", RegexOption.IGNORE_CASE),
            Regex("""(?:I don't like)(.{1,24})""", RegexOption.IGNORE_CASE),
            Regex("""(?:My birthday is)(.{1,24})""", RegexOption.IGNORE_CASE),
            Regex("""(?:I usually)(.{1,24})""", RegexOption.IGNORE_CASE),
        )
    }
}
