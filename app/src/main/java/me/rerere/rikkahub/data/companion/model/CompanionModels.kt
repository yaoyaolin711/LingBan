package me.rerere.rikkahub.data.companion.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import kotlin.uuid.Uuid

@Serializable
data class CompanionCharacterCard(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val avatar: String? = null,
    val description: String = "",
    val personality: String = "",
    val speakingStyle: String = "",
    val scenario: String = "",
    val systemPrompt: String = "",
    val firstMessage: String = "",
    val postHistoryInstructions: String = "",
    val exampleDialogue: String = "",
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class CompanionPersona(
    val id: Uuid = Uuid.random(),
    val displayName: String = "",
    val description: String = "",
    val title: String = "",
    val injectionPosition: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
    val injectDepth: Int = 4,
    val enabled: Boolean = true,
)

@Serializable
enum class CompanionEmotionState {
    CALM,
    WARM,
    PLAYFUL,
    CONCERNED,
}

@Serializable
enum class CompanionToneStyle {
    GENTLE,
    HUMOROUS,
    PLAYFUL,
    MATURE,
}

@Serializable
enum class CompanionLengthStyle {
    SHORT,
    BALANCED,
    DETAILED,
}

@Serializable
data class CompanionResponseStyle(
    val lengthStyle: CompanionLengthStyle = CompanionLengthStyle.BALANCED,
    val toneStyle: CompanionToneStyle = CompanionToneStyle.GENTLE,
    val emotionHint: String = "",
    val expressionStyle: String = "",
)

@Serializable
data class CompanionRelationshipState(
    val relationshipLevel: Int = 1,
    val interactionCount: Int = 0,
    val lastInteractionEpochMillis: Long = 0L,
    val emotionState: CompanionEmotionState = CompanionEmotionState.CALM,
    val affectionScore: Int = 0,
    val trustScore: Int = 0,
)

@Serializable
data class CompanionState(
    val mediumMemorySummary: String = "",
    val mediumMemoryUpdatedAtEpochMillis: Long = 0L,
    val lastAnalyzedUserMessageCount: Int = 0,
    val longMemoryFacts: List<String> = emptyList(),
    val relationshipState: CompanionRelationshipState = CompanionRelationshipState(),
    val responseStyle: CompanionResponseStyle = CompanionResponseStyle(),
    val memoryVersion: Int = 0,
    val relationshipVersion: Int = 0,
)

@Serializable
data class CompanionPromptBlock(
    val type: String,
    val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
    val role: MessageRole = MessageRole.USER,
    val order: Int = 0,
    val cacheKey: String = "",
    val content: String = "",
    val enabled: Boolean = true,
    val injectDepth: Int = 4,
)

@Serializable
data class CompanionPromptBundle(
    val cacheKey: String,
    val blocks: List<CompanionPromptBlock> = emptyList(),
)
