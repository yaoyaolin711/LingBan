package com.agent.chat.domain.model

/**
 * 灵伴聊天模式。
 */
enum class LingBanChatMode(
    val storageKey: String,
    val displayName: String,
    val shortDescription: String,
) {
    ASSISTANT(
        storageKey = "assistant",
        displayName = "助手模式",
        shortDescription = "理性、简洁、低情绪",
    ),
    COMPANION(
        storageKey = "companion",
        displayName = "伴侣模式",
        shortDescription = "有温度、有记忆、自然互动",
    ),
    ROLEPLAY(
        storageKey = "roleplay",
        displayName = "角色扮演",
        shortDescription = "允许剧情、角色扮演与内心描写",
    ),
    ;

    companion object {
        fun fromStorage(key: String?): LingBanChatMode {
            if (key.isNullOrBlank()) return COMPANION
            return entries.find { it.storageKey.equals(key, ignoreCase = true) } ?: COMPANION
        }
    }
}
