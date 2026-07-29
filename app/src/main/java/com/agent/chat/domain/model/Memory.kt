package com.agent.chat.domain.model

data class Memory(
    val id: String,
    val personaId: String,
    val conversationId: String,
    val content: String,
    val createdAt: Long,
    val importance: Int = 5,
)
