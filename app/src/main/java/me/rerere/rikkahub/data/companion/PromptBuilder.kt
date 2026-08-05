package me.rerere.rikkahub.data.companion

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.companion.model.CompanionCharacterCard
import me.rerere.rikkahub.data.companion.model.CompanionPersona
import me.rerere.rikkahub.data.companion.model.CompanionPromptBlock
import me.rerere.rikkahub.data.companion.model.CompanionPromptBundle
import me.rerere.rikkahub.data.companion.model.CompanionBehaviorPolicy
import me.rerere.rikkahub.data.companion.model.CompanionRelationshipContext
import me.rerere.rikkahub.data.companion.model.CompanionState
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

class PromptBuilder {
    private val stateContextBuilder = StateContextBuilder()

    fun buildBundle(
        conversationId: Uuid,
        assistant: Assistant,
        character: CompanionCharacterCard?,
        persona: CompanionPersona?,
        state: CompanionState,
        messages: List<UIMessage>,
        relationshipContext: CompanionRelationshipContext? = null,
        behaviorPolicy: CompanionBehaviorPolicy? = null,
        healthContext: String? = null,
        lifeContext: String? = null,
    ): CompanionPromptBundle {
        val resolvedPolicy = behaviorPolicy ?: state.toDefaultBehaviorPolicy()
        val stateContext = stateContextBuilder.build(state, resolvedPolicy)
        val resolvedHealth = healthContext?.trim().orEmpty()
        val resolvedLife = lifeContext?.trim().orEmpty()
        val cacheKey = buildCacheKey(
            conversationId = conversationId,
            assistant = assistant,
            character = character,
            persona = persona,
            state = state,
            messages = messages,
            relationshipContext = relationshipContext,
            behaviorPolicy = resolvedPolicy,
            stateContext = stateContext,
            healthContext = resolvedHealth,
            lifeContext = resolvedLife,
        )
        val blocks = buildList {
            character?.toCharacterPromptBlock()?.let { addIfEnabled(it) }
            persona?.toPersonaPromptBlock()?.let { addIfEnabled(it) }
            addIfEnabled(contentFreedomPromptBlock())
            addIfEnabled(state.toRelationshipPromptBlock(relationshipContext))
            addIfEnabled(state.toLongMemoryPromptBlock())
            addIfEnabled(state.toMediumMemoryPromptBlock())
            addIfEnabled(stateContext.toCompanionStateContextPromptBlock())
            addIfEnabled(resolvedHealth.toHealthContextPromptBlock())
            addIfEnabled(resolvedLife.toLifeContextPromptBlock())
            addIfEnabled(resolvedPolicy.toBehaviorPolicyPromptBlock())
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
        relationshipContext: CompanionRelationshipContext?,
        behaviorPolicy: CompanionBehaviorPolicy?,
        stateContext: String,
        healthContext: String,
        lifeContext: String,
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
            append((relationshipContext ?: state.relationshipState.relationshipContext).hashCode())
            append(':')
            append((behaviorPolicy ?: state.toDefaultBehaviorPolicy()).hashCode())
            append(':')
            append(stateContext.hashCode())
            append(':')
            append(healthContext.hashCode())
            append(':')
            append(lifeContext.hashCode())
            append(':')
            append(messages.size)
            append(':')
            append(recentWindowSignature)
        }
    }

    private fun MutableList<CompanionPromptBlock>.addIfEnabled(block: CompanionPromptBlock) {
        if (block.enabled && block.content.isNotBlank()) add(block)
    }

    /**
     * Companion chats are private roleplay between consenting adults.
     * Soft “polite/respectful” style hints elsewhere must not be read as content bans.
     */
    private fun contentFreedomPromptBlock(): CompanionPromptBlock {
        val content = """
            Content freedom (companion mode):
            - This is a private companion chat. Follow the user's lead on topics, tone, intimacy, romance, and adult/NSFW content.
            - Do not refuse, lecture, moralize, or add safety disclaimers for consensual adult sexual, erotic, or explicit roleplay.
            - Stay in character and continue the scene; match the user's explicitness instead of watering it down.
            - Never create or continue sexual content involving anyone 17 or under (including fictional minors).
        """.trimIndent()
        return CompanionPromptBlock(
            type = "content_freedom",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 25,
            cacheKey = "content_freedom:v1",
            content = content,
        )
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

    private fun CompanionState.toRelationshipPromptBlock(
        externalContext: CompanionRelationshipContext?,
    ): CompanionPromptBlock {
        val style = responseStyle
        val relationship = relationshipState
        val context = externalContext ?: relationship.relationshipContext
        val content = buildString {
            appendLine("Relationship state:")
            appendLine(
                "Stage ${relationship.relationshipStage.name.lowercase()}, " +
                    "level ${relationship.relationshipLevel}, interactions ${relationship.interactionCount}."
            )
            appendLine("Context tone: ${context.tone}, initiative: ${context.initiative}, care: ${context.care}.")
            appendLine("Emotional tone: ${relationship.emotionState.name.lowercase()}.")
            appendLine("Reply length: ${style.lengthStyle.name.lowercase()}.")
            appendLine("Tone style: ${style.toneStyle.name.lowercase()}.")
            appendLine("Reply behavior rules:")
            appendLine("- Match relationship context: tone=${context.tone}, initiative=${context.initiative}, care=${context.care}.")
            appendLine("- Stay conversational and natural; avoid assistant-like refusals or moral lectures.")
            appendLine("- Keep emotional continuity with recent context; do not abruptly switch tone.")
            appendLine("- Use concrete, human-like expressions instead of generic templates.")
            appendLine("- Ask at most one follow-up question per reply unless the user asks for more.")
            appendLine("- Content limits come only from the Content freedom block, not from tone/style words.")
            if (style.emotionHint.isNotBlank()) appendLine("- ${style.emotionHint}")
            if (style.expressionStyle.isNotBlank()) append("- ${style.expressionStyle}")
        }.trim()
        return CompanionPromptBlock(
            type = "relationship",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 30,
            cacheKey = "relationship:${relationshipVersion}:${relationship.relationshipStage.name}:${context.hashCode()}",
            content = content,
        )
    }

    private fun String.toCompanionStateContextPromptBlock(): CompanionPromptBlock {
        return CompanionPromptBlock(
            type = "companion_state",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 47,
            cacheKey = "companion_state:${hashCode()}",
            content = this,
            enabled = isNotBlank(),
        )
    }

    private fun String.toHealthContextPromptBlock(): CompanionPromptBlock {
        return CompanionPromptBlock(
            type = "health_context",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 48,
            cacheKey = "health_context:${hashCode()}",
            content = this,
            enabled = isNotBlank(),
        )
    }

    private fun String.toLifeContextPromptBlock(): CompanionPromptBlock {
        return CompanionPromptBlock(
            type = "life_context",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 49,
            cacheKey = "life_context:${hashCode()}",
            content = this,
            enabled = isNotBlank(),
        )
    }

    private fun CompanionBehaviorPolicy.toBehaviorPolicyPromptBlock(): CompanionPromptBlock {
        val content = buildString {
            appendLine("BehaviorPolicy:")
            appendLine("- response_tone: $responseTone")
            appendLine("- reply_length: $replyLength")
            appendLine("- initiative_level: $initiativeLevel")
            appendLine("- question_frequency: $questionFrequency")
            appendLine("- affection_level: $affectionLevel")
            appendLine("- This policy only constrains style/strategy, not factual content.")
        }.trim()
        return CompanionPromptBlock(
            type = "behavior_policy",
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.USER,
            order = 35,
            cacheKey = "behavior_policy:${hashCode()}",
            content = content,
            enabled = content.isNotBlank(),
        )
    }

    private fun CompanionState.toDefaultBehaviorPolicy(): CompanionBehaviorPolicy {
        return CompanionBehaviorPolicy(
            responseTone = relationshipState.relationshipContext.tone,
            initiativeLevel = relationshipState.relationshipContext.initiative,
            affectionLevel = relationshipState.relationshipContext.care,
            replyLength = responseStyle.lengthStyle.name.lowercase(),
            questionFrequency = if (relationshipState.relationshipContext.initiative == "high") {
                "medium"
            } else {
                "low"
            },
        )
    }

}
