package me.rerere.rikkahub.data.companion.state

import kotlinx.serialization.Serializable

@Serializable
data class CompanionState(
    val emotionState: String = DEFAULT_EMOTION_STATE,
    val emotion: EmotionState = EmotionState(),
    val relationshipStage: String = DEFAULT_RELATIONSHIP_STAGE,
    val interactionPattern: String = DEFAULT_INTERACTION_PATTERN,
    val behaviorStyle: List<String> = DEFAULT_BEHAVIOR_STYLE,
    val version: Int = 0,
    val updatedAtEpochMillis: Long = 0L,
    val extras: Map<String, String> = emptyMap(),
) {
    companion object {
        const val DEFAULT_EMOTION_STATE = "neutral"
        const val DEFAULT_RELATIONSHIP_STAGE = "close"
        const val DEFAULT_INTERACTION_PATTERN = "normal"
        val DEFAULT_BEHAVIOR_STYLE = listOf("warm", "caring")
    }
}
