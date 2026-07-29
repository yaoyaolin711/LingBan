package com.agent.chat.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val personaId: String? = null,
    val providerConfigId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String = "",
)
