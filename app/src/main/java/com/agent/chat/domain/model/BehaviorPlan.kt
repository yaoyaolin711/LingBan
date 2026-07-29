package com.agent.chat.domain.model

/**
 * Runtime Decision 输出：本轮 AI 行为计划（非 Prompt 拼接，而是执行参数）。
 */
data class BehaviorPlan(
    val responseTone: ResponseTone = ResponseTone.CASUAL,
    val initiativeLevel: InitiativeLevel = InitiativeLevel.MEDIUM,
    val emotionalIntensity: EmotionalIntensity = EmotionalIntensity.NEUTRAL,
    val humorLevel: HumorLevel = HumorLevel.MEDIUM,
    val responseLength: ResponseLengthTarget = ResponseLengthTarget.MEDIUM,
    val focus: BehaviorFocus = BehaviorFocus.GENERAL,
) {
    companion object {
        val DEFAULT = BehaviorPlan()
    }
}

enum class ResponseTone(val storageKey: String) {
    PROFESSIONAL("professional"),
    CARING("caring"),
    CASUAL("casual"),
    PLAYFUL("playful"),
    WARM("warm"),
    RESERVED("reserved"),
}

enum class InitiativeLevel(val storageKey: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

enum class EmotionalIntensity(val storageKey: String) {
    NEUTRAL("neutral"),
    SUPPORT("support"),
    WARM("warm"),
    EXPRESSIVE("expressive"),
}

enum class HumorLevel(val storageKey: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

enum class ResponseLengthTarget(val storageKey: String) {
    SHORT("short"),
    MEDIUM("medium"),
    LONG("long"),
}

enum class BehaviorFocus(val storageKey: String) {
    GENERAL("general"),
    KNOWLEDGE("knowledge"),
    EMOTIONAL_SUPPORT("emotional_support"),
    PLAYFUL("playful"),
    ROLEPLAY("roleplay"),
}

data class RuntimeDecisionResult(
    val plan: BehaviorPlan,
    /** 决策置信度 0–1 */
    val confidence: Float = 1f,
    /** 各信号源贡献权重（调试） */
    val sourceWeights: Map<String, Float> = emptyMap(),
)
