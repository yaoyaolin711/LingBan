package com.agent.chat.domain.model

/**
 * 互动倾向权重（0–1），由 Runtime 结合话题/情绪/关系/Persona 计算。
 *
 * 开关仅设「允许上限」；权重低于 [ACTIVE_THRESHOLD] 时本轮不激活该倾向。
 */
data class InteractionTendencyWeights(
    val romantic: Float = 0f,
    val flirting: Float = 0f,
    val intimate: Float = 0f,
    val roleplay: Float = 0f,
) {
    fun romanticActive(): Boolean = romantic >= ACTIVE_THRESHOLD
    fun flirtingActive(): Boolean = flirting >= ACTIVE_THRESHOLD
    fun intimateActive(): Boolean = intimate >= ACTIVE_THRESHOLD
    fun roleplayActive(): Boolean = roleplay >= ACTIVE_THRESHOLD

    fun anyActive(): Boolean =
        romanticActive() || flirtingActive() || intimateActive() || roleplayActive()

    companion object {
        const val ACTIVE_THRESHOLD = 0.25f
        val ZERO = InteractionTendencyWeights()
    }
}
