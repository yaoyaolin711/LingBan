package me.rerere.rikkahub.data.companion.state

import kotlinx.serialization.Serializable

@Serializable
enum class EmotionType {
    NEUTRAL,
    HAPPY,
    EXCITED,
    CARING,
    LONELY,
    SAD,
    CONCERNED,
}

@Serializable
data class EmotionState(
    val emotionType: EmotionType = EmotionType.NEUTRAL,
    val emotionIntensity: Float = 0f,
    val lastUpdatedTime: Long = 0L,
) {
    init {
        require(emotionIntensity in 0f..1f) {
            "emotionIntensity must be in range [0, 1]"
        }
    }
}
