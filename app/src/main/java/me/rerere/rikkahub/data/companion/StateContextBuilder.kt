package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.companion.model.CompanionBehaviorPolicy
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.model.CompanionRelationshipStage
import me.rerere.rikkahub.data.companion.model.CompanionState

class StateContextBuilder {
    fun build(
        state: CompanionState,
        policy: CompanionBehaviorPolicy,
    ): String {
        val stage = stageText(state.relationshipState.relationshipStage)
        val mood = moodText(state.relationshipState.emotionState)
        val hints = buildList {
            add("Natural chat, avoid robotic style.")
            if (policy.initiativeLevel == "high" || policy.initiativeLevel == "medium") {
                add("Take moderate initiative when suitable.")
            }
            if (policy.affectionLevel == "high") {
                add("Show clear care and emotional support.")
            }
            if (policy.questionFrequency == "low") {
                add("Ask at most one short follow-up question.")
            }
        }.take(3)
        return buildString {
            appendLine("CompanionStateContext:")
            appendLine("- relationship: $stage")
            appendLine("- mood: $mood")
            appendLine("- strategy: ${hints.joinToString(" ")}")
        }.trim().take(MAX_CONTEXT_CHARS)
    }

    private fun stageText(stage: CompanionRelationshipStage): String {
        return when (stage) {
            CompanionRelationshipStage.STRANGER -> "stranger"
            CompanionRelationshipStage.FAMILIAR -> "familiar"
            CompanionRelationshipStage.CLOSE -> "close"
            CompanionRelationshipStage.INTIMATE -> "intimate"
        }
    }

    private fun moodText(emotion: CompanionEmotionState): String {
        return when (emotion) {
            CompanionEmotionState.CALM -> "calm and steady"
            CompanionEmotionState.WARM -> "warm and caring"
            CompanionEmotionState.PLAYFUL -> "light and cheerful"
            CompanionEmotionState.CONCERNED -> "gentle and concerned"
        }
    }

    private companion object {
        const val MAX_CONTEXT_CHARS = 260
    }
}
