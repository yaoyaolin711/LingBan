package me.rerere.rikkahub.data.companion.emotion

data class EmotionContext(
    val emotion: EmotionType = EmotionType.NEUTRAL,
    val intensity: Float = 0f,
    val responseStyle: String = "neutral",
    val keywords: List<String> = emptyList(),
    val confidence: Float = 0f,
    val sourceHash: Int = 0,
)
