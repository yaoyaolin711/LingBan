package com.agent.chat.data.conversationstate

/**
 * 启发式情绪检测：识别用户是否需要情绪陪伴。
 */
data class EmotionSignal(
    val score: Float,
    val labels: List<String>,
) {
    companion object {
        val NONE = EmotionSignal(score = 0f, labels = emptyList())
    }
}

object ConversationEmotionDetector {

    private val distressPatterns = listOf(
        "压力大" to "stress",
        "好难" to "difficulty",
        "好累" to "fatigue",
        "累了" to "fatigue",
        "难过" to "sad",
        "伤心" to "sad",
        "崩溃" to "overwhelm",
        "焦虑" to "anxiety",
        "烦" to "frustration",
        "受不了" to "overwhelm",
        "孤独" to "lonely",
        "孤单" to "lonely",
        "失眠" to "insomnia",
        "害怕" to "fear",
        "委屈" to "grievance",
        "抑郁" to "depression",
        "不开心" to "sad",
        "心情不好" to "low_mood",
        "想哭" to "sad",
        "撑不住" to "overwhelm",
        "没动力" to "low_energy",
        "迷茫" to "confusion",
    )

    fun detect(userMessage: String): EmotionSignal {
        val text = userMessage.trim()
        if (text.isBlank()) return EmotionSignal.NONE

        val labels = mutableListOf<String>()
        var score = 0f

        distressPatterns.forEach { (pattern, label) ->
            if (pattern in text) {
                labels += label
                score += 0.22f
            }
        }

        if (Regex("""[！!]{2,}|\.{3,}|…{2,}""").containsMatchIn(text)) {
            score += 0.08f
            labels += "emphasis"
        }

        return EmotionSignal(
            score = score.coerceIn(0f, 1f),
            labels = labels.distinct(),
        )
    }
}
