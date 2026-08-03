package me.rerere.rikkahub.data.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.companion.model.CompanionBehaviorPolicy
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.model.CompanionRelationshipStage
import me.rerere.rikkahub.data.companion.model.CompanionState
import java.util.concurrent.ConcurrentHashMap

class BehaviorPolicyManager {
    private val cache = ConcurrentHashMap<String, CompanionBehaviorPolicy>()

    suspend fun resolvePolicyAsync(state: CompanionState): CompanionBehaviorPolicy {
        return withContext(Dispatchers.Default) {
            resolvePolicy(state)
        }
    }

    fun resolvePolicy(state: CompanionState): CompanionBehaviorPolicy {
        val stage = state.relationshipState.relationshipStage
        val emotion = state.relationshipState.emotionState
        val cacheKey = "${stage.name}:${emotion.name}"
        cache[cacheKey]?.let { return it }

        val basePolicy = basePolicyFor(stage)
        val adjusted = applyEmotionAdjustment(basePolicy, emotion)
        cache[cacheKey] = adjusted
        return adjusted
    }

    private fun basePolicyFor(stage: CompanionRelationshipStage): CompanionBehaviorPolicy {
        return when (stage) {
            CompanionRelationshipStage.STRANGER -> CompanionBehaviorPolicy(
                responseTone = "neutral",
                replyLength = "short",
                initiativeLevel = "low",
                questionFrequency = "low",
                affectionLevel = "low",
            )
            CompanionRelationshipStage.FAMILIAR -> CompanionBehaviorPolicy(
                responseTone = "friendly",
                replyLength = "balanced",
                initiativeLevel = "medium",
                questionFrequency = "medium",
                affectionLevel = "medium",
            )
            CompanionRelationshipStage.CLOSE -> CompanionBehaviorPolicy(
                responseTone = "casual",
                replyLength = "balanced",
                initiativeLevel = "medium",
                questionFrequency = "low",
                affectionLevel = "high",
            )
            CompanionRelationshipStage.INTIMATE -> CompanionBehaviorPolicy(
                responseTone = "warm",
                replyLength = "balanced",
                initiativeLevel = "high",
                questionFrequency = "low",
                affectionLevel = "high",
            )
        }
    }

    private fun applyEmotionAdjustment(
        base: CompanionBehaviorPolicy,
        emotion: CompanionEmotionState,
    ): CompanionBehaviorPolicy {
        return when (emotion) {
            CompanionEmotionState.CALM -> base
            CompanionEmotionState.WARM -> base.copy(
                responseTone = "warm",
                affectionLevel = elevate(base.affectionLevel),
            )
            CompanionEmotionState.PLAYFUL -> base.copy(
                responseTone = "playful",
                questionFrequency = elevate(base.questionFrequency),
            )
            CompanionEmotionState.CONCERNED -> base.copy(
                responseTone = "gentle",
                replyLength = "short",
                questionFrequency = "low",
                affectionLevel = "high",
            )
        }
    }

    private fun elevate(level: String): String {
        return when (level) {
            "low" -> "medium"
            "medium" -> "high"
            else -> "high"
        }
    }
}
