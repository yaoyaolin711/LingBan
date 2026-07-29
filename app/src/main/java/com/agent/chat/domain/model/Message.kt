package com.agent.chat.domain.model

import androidx.compose.runtime.Immutable

enum class MessageRole {
    USER,
    ASSISTANT,
}

@Immutable
data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    /** 用户附件图片的本地 URI（可选，视觉功能） */
    val imageUri: String? = null,
)
