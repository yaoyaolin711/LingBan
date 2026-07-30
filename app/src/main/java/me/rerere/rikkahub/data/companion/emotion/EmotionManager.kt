package me.rerere.rikkahub.data.companion.emotion

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import kotlin.math.max
import kotlin.math.min

class EmotionManager(
    private val cacheSize: Int = 96,
) {
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<Int, EmotionContext>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, EmotionContext>?): Boolean {
            return size > cacheSize
        }
    }

    fun analyzeLatestUserMessage(messages: List<UIMessage>): EmotionContext {
        val latestUserText = messages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        return analyze(latestUserText)
    }

    fun analyze(text: String): EmotionContext {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return EmotionContext()
        val sourceHash = normalized.hashCode()
        synchronized(cacheLock) {
            cache[sourceHash]?.let { return it }
        }

        val hitKeywords = ArrayList<String>(4)
        var positiveScore = 0f
        var negativeScore = 0f
        var topEmotion = EmotionType.NEUTRAL
        var topScore = 0f

        EMOTION_RULES.forEach { (emotion, keywords) ->
            var score = 0f
            keywords.forEach { keyword ->
                if (normalized.contains(keyword)) {
                    score += keywordWeight(keyword.length)
                    if (hitKeywords.size < MAX_KEYWORDS) hitKeywords.add(keyword)
                }
            }
            if (score > 0f) {
                score += boosterScore(normalized)
                score -= reducerScore(normalized)
                score = score.coerceAtLeast(0f)
            }

            if (emotion == EmotionType.HAPPY || emotion == EmotionType.EXCITED) {
                positiveScore += score
            } else if (emotion != EmotionType.NEUTRAL) {
                negativeScore += score
            }

            if (score > topScore) {
                topScore = score
                topEmotion = emotion
            }
        }

        val emotion = resolveEmotion(topEmotion, positiveScore, negativeScore)
        val intensity = min(1f, topScore / 4f)
        val confidence = min(1f, max(positiveScore, negativeScore) / 5f)
        val context = EmotionContext(
            emotion = emotion,
            intensity = if (emotion == EmotionType.NEUTRAL) 0f else intensity,
            responseStyle = styleFor(emotion),
            keywords = hitKeywords.distinct(),
            confidence = confidence,
            sourceHash = sourceHash,
        )
        synchronized(cacheLock) {
            cache[sourceHash] = context
        }
        return context
    }

    private fun resolveEmotion(
        topEmotion: EmotionType,
        positiveScore: Float,
        negativeScore: Float,
    ): EmotionType {
        if (topEmotion == EmotionType.NEUTRAL) return EmotionType.NEUTRAL
        if (positiveScore <= 0f && negativeScore <= 0f) return EmotionType.NEUTRAL
        return if (kotlin.math.abs(positiveScore - negativeScore) < 0.6f) {
            EmotionType.NEUTRAL
        } else {
            topEmotion
        }
    }

    private fun styleFor(type: EmotionType): String {
        return when (type) {
            EmotionType.SAD -> "comfort"
            EmotionType.TIRED -> "gentle_brief"
            EmotionType.ANXIOUS -> "calm_grounding"
            EmotionType.ANGRY -> "calm_ack"
            EmotionType.LONELY -> "warm_presence"
            EmotionType.HAPPY -> "light_positive"
            EmotionType.EXCITED -> "high_energy_positive"
            EmotionType.NEUTRAL -> "neutral"
        }
    }

    private fun keywordWeight(length: Int): Float {
        return when {
            length >= 4 -> 1.2f
            length == 3 -> 1.0f
            else -> 0.8f
        }
    }

    private fun boosterScore(text: String): Float {
        var score = 0f
        BOOSTER_WORDS.forEach { if (text.contains(it)) score += 0.3f }
        val exclamationCount = text.count { it == '!' || it == '！' }
        score += min(0.4f, exclamationCount * 0.1f)
        return score
    }

    private fun reducerScore(text: String): Float {
        var score = 0f
        REDUCER_WORDS.forEach { if (text.contains(it)) score += 0.25f }
        return score
    }

    private companion object {
        const val MAX_KEYWORDS = 4
        val BOOSTER_WORDS = arrayOf("非常", "特别", "太", "真的", "超级", "so", "very")
        val REDUCER_WORDS = arrayOf("有点", "还好", "一般", "kind of", "a bit")

        val EMOTION_RULES: Map<EmotionType, Array<String>> = mapOf(
            EmotionType.SAD to arrayOf("难过", "伤心", "失落", "想哭", "心情不好", "sad", "down"),
            EmotionType.TIRED to arrayOf("累", "好困", "疲惫", "没力气", "tired", "exhausted"),
            EmotionType.ANXIOUS to arrayOf("焦虑", "紧张", "害怕", "压力大", "anxious", "nervous", "stress"),
            EmotionType.ANGRY to arrayOf("生气", "烦死", "火大", "气死", "恼火", "angry", "mad"),
            EmotionType.LONELY to arrayOf("孤独", "寂寞", "一个人", "没人懂", "lonely", "alone"),
            EmotionType.HAPPY to arrayOf("开心", "高兴", "愉快", "满足", "happy", "glad"),
            EmotionType.EXCITED to arrayOf("兴奋", "激动", "太棒了", "爽", "冲", "excited", "awesome"),
        )
    }
}
