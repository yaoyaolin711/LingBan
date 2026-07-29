package com.agent.chat.domain.model

/**
 * 会话运行时交流状态（临时、可衰减；不写入 Persona / Relationship）。
 */
enum class ConversationStateKind {
    /** 普通交流 */
    NORMAL,
    /** 情绪陪伴 */
    EMOTIONAL_SUPPORT,
    /** 知识 / 技术交流 */
    KNOWLEDGE,
    /** 轻松互动 */
    PLAYFUL,
    /** 角色互动 / 剧情 */
    ROLEPLAY,
}

/**
 * 当前对话状态快照。
 *
 * @param currentState 当前交流模式
 * @param confidence 判定置信度 0–1
 * @param trigger 触发原因（如 emotion:stress、intent:technical）
 * @param timestamp 最近一次更新（用于衰减）
 */
data class ConversationStateSnapshot(
    val currentState: ConversationStateKind = ConversationStateKind.NORMAL,
    val confidence: Float = 0f,
    val trigger: String = "",
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun isActive(): Boolean =
        currentState != ConversationStateKind.NORMAL && confidence >= ACTIVE_THRESHOLD

    companion object {
        const val ACTIVE_THRESHOLD = 0.35f
        val DEFAULT = ConversationStateSnapshot()
    }
}
