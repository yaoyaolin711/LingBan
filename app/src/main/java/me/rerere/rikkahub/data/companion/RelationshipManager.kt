package me.rerere.rikkahub.data.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.model.CompanionLengthStyle
import me.rerere.rikkahub.data.companion.model.CompanionRelationshipContext
import me.rerere.rikkahub.data.companion.model.CompanionRelationshipStage
import me.rerere.rikkahub.data.companion.model.CompanionRelationshipState
import me.rerere.rikkahub.data.companion.model.CompanionResponseStyle
import me.rerere.rikkahub.data.companion.model.CompanionState
import me.rerere.rikkahub.data.companion.model.CompanionToneStyle
import java.util.concurrent.ConcurrentHashMap

class RelationshipManager {
    private val contextCache = ConcurrentHashMap<String, CompanionRelationshipContext>()

    fun updateState(
        currentState: CompanionState,
        messages: List<UIMessage>,
    ): CompanionState {
        val userMessageCount = messages.count { it.role == MessageRole.USER }
        val relationship = currentState.relationshipState
        if (userMessageCount <= relationship.interactionCount) return currentState

        val latestUserText = messages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        val instantEmotion = inferEmotion(latestUserText)
        val emotionState = smoothEmotionTransition(relationship.emotionState, instantEmotion)
        val nextLevel = relationshipLevelFor(userMessageCount)
        val nextStage = stageFor(userMessageCount)
        val nextContext = relationshipContextFor(nextStage)
        val nextRelationship = relationship.copy(
            relationshipLevel = nextLevel,
            interactionCount = userMessageCount,
            lastInteractionEpochMillis = System.currentTimeMillis(),
            relationshipStage = nextStage,
            relationshipContext = nextContext,
            emotionState = emotionState,
            affectionScore = nextLevel.coerceAtMost(100),
            trustScore = (nextLevel + userMessageCount / 2).coerceAtMost(100),
        )
        return currentState.copy(
            relationshipState = nextRelationship,
            responseStyle = responseStyleFor(nextRelationship),
            relationshipVersion = currentState.relationshipVersion + 1,
        )
    }

    suspend fun updateStateAsync(
        currentState: CompanionState,
        messages: List<UIMessage>,
    ): CompanionState = withContext(Dispatchers.Default) {
        updateState(currentState, messages)
    }

    private fun stageFor(interactionCount: Int): CompanionRelationshipStage {
        return when {
            interactionCount >= 120 -> CompanionRelationshipStage.INTIMATE
            interactionCount >= 40 -> CompanionRelationshipStage.CLOSE
            interactionCount >= 10 -> CompanionRelationshipStage.FAMILIAR
            else -> CompanionRelationshipStage.STRANGER
        }
    }

    private fun relationshipContextFor(stage: CompanionRelationshipStage): CompanionRelationshipContext {
        val key = stage.name
        contextCache[key]?.let { return it }
        val context = when (stage) {
            CompanionRelationshipStage.STRANGER -> CompanionRelationshipContext(
                tone = "neutral",
                initiative = "low",
                care = "low",
            )
            CompanionRelationshipStage.FAMILIAR -> CompanionRelationshipContext(
                tone = "friendly",
                initiative = "medium",
                care = "medium",
            )
            CompanionRelationshipStage.CLOSE -> CompanionRelationshipContext(
                tone = "casual",
                initiative = "medium",
                care = "high",
            )
            CompanionRelationshipStage.INTIMATE -> CompanionRelationshipContext(
                tone = "warm",
                initiative = "high",
                care = "high",
            )
        }
        contextCache[key] = context
        return context
    }

    private fun inferEmotion(text: String): CompanionEmotionState {
        val normalized = text.lowercase()
        return when {
            normalized.containsAny(NEGATIVE_EMOTION_KEYWORDS) ->
                CompanionEmotionState.CONCERNED
            normalized.containsAny(PLAYFUL_EMOTION_KEYWORDS) ->
                CompanionEmotionState.PLAYFUL
            normalized.containsAny(WARM_EMOTION_KEYWORDS) ->
                CompanionEmotionState.WARM
            else -> CompanionEmotionState.CALM
        }
    }

    private fun relationshipLevelFor(interactionCount: Int): Int {
        return when {
            interactionCount >= 150 -> 50
            interactionCount >= 60 -> 20
            interactionCount >= 20 -> 10
            interactionCount >= 8 -> 5
            else -> 1
        }
    }

    private fun responseStyleFor(state: CompanionRelationshipState): CompanionResponseStyle {
        val lengthStyle = when {
            state.relationshipLevel >= 20 -> CompanionLengthStyle.DETAILED
            state.relationshipLevel >= 5 -> CompanionLengthStyle.BALANCED
            else -> CompanionLengthStyle.SHORT
        }
        val toneStyle = when (state.emotionState) {
            CompanionEmotionState.PLAYFUL -> CompanionToneStyle.PLAYFUL
            CompanionEmotionState.CONCERNED -> CompanionToneStyle.GENTLE
            CompanionEmotionState.WARM -> CompanionToneStyle.GENTLE
            CompanionEmotionState.CALM -> if (state.relationshipLevel >= 10) {
                CompanionToneStyle.MATURE
            } else {
                CompanionToneStyle.GENTLE
            }
        }
        val emotionHint = when (state.emotionState) {
            CompanionEmotionState.CONCERNED -> "Be caring and emotionally attentive."
            CompanionEmotionState.PLAYFUL -> "Be a little playful and lively."
            CompanionEmotionState.WARM -> "Be warm and affectionate."
            CompanionEmotionState.CALM -> "Stay calm, natural, and grounded."
        }
        return CompanionResponseStyle(
            lengthStyle = lengthStyle,
            toneStyle = toneStyle,
            emotionHint = emotionHint,
            expressionStyle = if (state.relationshipLevel >= 10) {
                "Use a familiar and companion-like voice."
            } else {
                "Keep the tone natural and companion-like."
            },
        )
    }

    private fun smoothEmotionTransition(
        previous: CompanionEmotionState,
        current: CompanionEmotionState,
    ): CompanionEmotionState {
        if (previous == current) return current
        // Avoid hard emotion jumps on a single turn unless concern is detected.
        if (current == CompanionEmotionState.CONCERNED) return current
        return when (previous) {
            CompanionEmotionState.CONCERNED -> CompanionEmotionState.WARM
            CompanionEmotionState.PLAYFUL -> CompanionEmotionState.WARM
            CompanionEmotionState.WARM -> current
            CompanionEmotionState.CALM -> current
        }
    }

    private fun String.containsAny(keywords: Array<String>): Boolean {
        return keywords.any(::contains)
    }

    private companion object {
        val NEGATIVE_EMOTION_KEYWORDS = arrayOf("累", "难过", "伤心", "烦", "stress", "tired", "sad", "upset")
        val PLAYFUL_EMOTION_KEYWORDS = arrayOf("哈哈", "开心", "有趣", "fun", "lol", "happy")
        val WARM_EMOTION_KEYWORDS = arrayOf("喜欢", "想你", "love", "miss")
    }
}
