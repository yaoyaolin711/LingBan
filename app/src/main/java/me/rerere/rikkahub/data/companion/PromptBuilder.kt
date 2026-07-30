package me.rerere.rikkahub.data.companion

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.companion.emotion.EmotionContext
import me.rerere.rikkahub.data.companion.emotion.EmotionManager
import me.rerere.rikkahub.data.companion.emotion.EmotionType
import me.rerere.rikkahub.data.companion.model.CompanionCharacterCard
import me.rerere.rikkahub.data.companion.model.CompanionPersona
import me.rerere.rikkahub.data.companion.model.CompanionPromptBlock
import me.rerere.rikkahub.data.companion.model.CompanionPromptBundle
import me.rerere.rikkahub.data.companion.model.CompanionState
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Assistant
import java.util.Locale
import kotlin.uuid.Uuid

class PromptBuilder {
    private val emotionManager = EmotionManager()

    fun buildBundle(
        conversationId: Uuid,
        assistant: Assistant,
        character: CompanionCharacterCard?,
        persona: CompanionPersona?,
        state: CompanionState,
        messages: List<UIMessage>,
    ): CompanionPromptBundle {
        val emotionContext = emotionManager.analyzeLatestUserMessage(messages)
        val cacheKey = buildCacheKey(conversationId, assistant, character, persona, state, messages)
        val blocks = buildList {
            character?.toCharacterPromptBlock()?.let { addIfEnabled(it) }
            persona?.toPersonaPromptBlock()?.let { addIfEnabled(it) }
            addIfEnabled(state.toRelationshipPromptBlock())
            addIfEnabled(state.toLongMemoryPromptBlock())
            addIfEnabled(state.toMediumMemoryPromptBlock())
            addIfEnabled(emotionContext.toEmotionPromptBlock())
            character?.toPostHistoryBlock()?.let { addIfEnabled(it) }
        }.sortedBy { it.order }
        return CompanionPromptBundle(
            cacheKey = cacheKey,
            blocks = blocks,
        )
    }

    private fun buildCacheKey(
        conversationId: Uuid,
        assistant: Assistant,
        character: CompanionCharacterCard?,
        persona: CompanionPersona?,
        state: CompanionState,
        messages: List<UIMessage>,
    ): String {
        val recentWindowSignature = messages
            .takeLast(4)
            .joinToString("|") { "${it.role}:${it.summaryAsText(maxLength = 64)}" }
            .hashCode()
        return buildString {
            append(conversationId)
            append(':')
            append(assistant.id)
            append(':')
            append(character?.updatedAtEpochMillis ?: 0L)
            append(':')
            append(persona?.displayName ?: "")
            append(':')
            append(state.memoryVersion)
            append(':')
            append(state.relationshipVersion)
            append(':')
            append(messages.size)
            append(':')
            append(recentWindowSignature)
        }
    }

    private fun MutableList<CompanionPromptBlock>.addIfEnabled(block: CompanionPromptBlock) {
        if (block.enabled && block.content.isNotBlank()) add(block)
    }

    private fun CompanionCharacterCard.toCharacterPromptBlock(): CompanionPromptBlock {
        val content = buildString {
            if (name.isNotBlank()) appendLine("Character name: $name")
            if (description.isNotBlank()) {
                appendLine("Character description:")
                appendLine(description)
            }
            if (personality.isNotBlank()) {
                appendLine("Character personality:")
                appendLine(personality)
            }
            if (speakingStyle.isNotBlank()) {
                appendLine("Speaking style:")
                appendLine(speakingStyle)
            }
            if (scenario.isNotBlank()) {
                appendLine("Scenario:")
                appendLine(scenario)
            }
            if (systemPrompt.isNotBlank()) {
                appendLine("Character-specific instruction:")
                append(systemPrompt)
            }
        }.trim()
        return CompanionPromptBlock(
            type = "character",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 10,
            cacheKey = "character:$id:$updatedAtEpochMillis",
            content = content,
        )
    }

    private fun CompanionCharacterCard.toPostHistoryBlock(): CompanionPromptBlock {
        return CompanionPromptBlock(
            type = "post_history",
            position = InjectionPosition.BOTTOM_OF_CHAT,
            role = MessageRole.USER,
            order = 60,
            cacheKey = "post_history:$id:$updatedAtEpochMillis",
            content = postHistoryInstructions.trim(),
            enabled = postHistoryInstructions.isNotBlank(),
        )
    }

    private fun CompanionPersona.toPersonaPromptBlock(): CompanionPromptBlock {
        val content = buildString {
            appendLine("User persona:")
            if (displayName.isNotBlank()) appendLine("Preferred name: $displayName")
            if (title.isNotBlank()) appendLine("Title: $title")
            if (description.isNotBlank()) append(description)
        }.trim()
        return CompanionPromptBlock(
            type = "persona",
            position = injectionPosition,
            role = MessageRole.USER,
            order = 20,
            cacheKey = "persona:$id:${displayName.hashCode()}",
            content = content,
            injectDepth = injectDepth,
            enabled = enabled,
        )
    }

    private fun CompanionState.toLongMemoryPromptBlock(): CompanionPromptBlock {
        return CompanionPromptBlock(
            type = "long_memory",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 40,
            cacheKey = "long_memory:$memoryVersion",
            content = longMemoryFacts.takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n", prefix = "Stable user facts:\n")
                .orEmpty(),
            enabled = longMemoryFacts.isNotEmpty(),
        )
    }

    private fun CompanionState.toMediumMemoryPromptBlock(): CompanionPromptBlock {
        return CompanionPromptBlock(
            type = "medium_memory",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 45,
            cacheKey = "medium_memory:$memoryVersion",
            content = mediumMemorySummary.takeIf { it.isNotBlank() }
                ?.let { "Recent important context:\n$it" }
                .orEmpty(),
            enabled = mediumMemorySummary.isNotBlank(),
        )
    }

    private fun CompanionState.toRelationshipPromptBlock(): CompanionPromptBlock {
        val style = responseStyle
        val relationship = relationshipState
        val content = buildString {
            appendLine("Relationship state:")
            appendLine("Level ${relationship.relationshipLevel}, interactions ${relationship.interactionCount}.")
            appendLine("Emotional tone: ${relationship.emotionState.name.lowercase()}.")
            appendLine("Reply length: ${style.lengthStyle.name.lowercase()}.")
            appendLine("Tone style: ${style.toneStyle.name.lowercase()}.")
            appendLine("Reply behavior rules:")
            appendLine("- Stay conversational and natural; avoid assistant-like disclaimers unless necessary.")
            appendLine("- Keep emotional continuity with recent context; do not abruptly switch tone.")
            appendLine("- Use concrete, human-like expressions instead of generic templates.")
            appendLine("- Ask at most one follow-up question per reply unless the user asks for more.")
            if (style.emotionHint.isNotBlank()) appendLine("- ${style.emotionHint}")
            if (style.expressionStyle.isNotBlank()) append("- ${style.expressionStyle}")
        }.trim()
        return CompanionPromptBlock(
            type = "relationship",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 30,
            cacheKey = "relationship:${relationshipVersion}:${relationship.relationshipLevel}",
            content = content,
        )
    }

    private fun EmotionContext.toEmotionPromptBlock(): CompanionPromptBlock {
        val content = when (emotion) {
            EmotionType.SAD -> """
                User emotional state: sad (intensity ${formatIntensity(intensity)}).
                Reply strategy: comfort.
                Constraints:
                - acknowledge feelings first
                - avoid jumping into solutions immediately
                - use warm companion tone
                - ask at most one gentle follow-up question
            """.trimIndent()

            EmotionType.TIRED -> """
                User emotional state: tired (intensity ${formatIntensity(intensity)}).
                Reply strategy: gentle_brief.
                Constraints:
                - express care first
                - keep response concise and low-pressure
                - avoid long lists of suggestions
            """.trimIndent()

            EmotionType.HAPPY, EmotionType.EXCITED -> """
                User emotional state: ${emotion.name.lowercase()} (intensity ${formatIntensity(intensity)}).
                Reply strategy: $responseStyle.
                Constraints:
                - mirror positive emotion naturally
                - increase interaction warmth
                - keep it conversational, not performative
            """.trimIndent()

            EmotionType.ANGRY -> """
                User emotional state: angry (intensity ${formatIntensity(intensity)}).
                Reply strategy: calm_ack.
                Constraints:
                - do not argue or invalidate feelings
                - acknowledge emotion first
                - keep tone calm and respectful
            """.trimIndent()

            EmotionType.ANXIOUS -> """
                User emotional state: anxious (intensity ${formatIntensity(intensity)}).
                Reply strategy: calm_grounding.
                Constraints:
                - provide emotional grounding first
                - avoid alarming wording
                - keep guidance simple and stable
            """.trimIndent()

            EmotionType.LONELY -> """
                User emotional state: lonely (intensity ${formatIntensity(intensity)}).
                Reply strategy: warm_presence.
                Constraints:
                - emphasize companionship and presence
                - avoid cold or transactional wording
                - keep response emotionally available
            """.trimIndent()

            EmotionType.NEUTRAL -> ""
        }
        return CompanionPromptBlock(
            type = "emotion",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 50,
            cacheKey = "emotion:${emotion.name}:${intensity.toRawBits()}:${responseStyle.hashCode()}",
            content = content,
            enabled = emotion != EmotionType.NEUTRAL && content.isNotBlank(),
        )
    }

    private fun formatIntensity(value: Float): String {
        return String.format(Locale.US, "%.2f", value.coerceIn(0f, 1f))
    }
}
