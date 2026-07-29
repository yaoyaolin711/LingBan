package com.agent.chat.domain.model

/**
 * 用户互动偏好（全局、独立于 Persona / Relationship）。
 *
 * 开关代表**允许上限**（行为倾向），不是强制规则。
 * Runtime 结合话题、情绪、关系、Persona 计算权重；权重低时保持普通交流。
 */
data class InteractionPreference(
    val romanticConversation: Boolean = false,
    val flirting: Boolean = false,
    val intimateConversation: Boolean = false,
    val roleplay: Boolean = false,
) {
    val hasAnyEnabled: Boolean get() =
        romanticConversation || flirting || intimateConversation || roleplay
}
