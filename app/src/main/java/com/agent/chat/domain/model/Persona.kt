package com.agent.chat.domain.model

data class Persona(
    val id: String,
    val name: String,
    val avatar: String = "",
    val systemPrompt: String,
    val defaultTemperature: Float = 0.7f,
    val description: String = "",
    /** 符合角色语气的开场白，用于空会话问候等 */
    val openingLine: String = "",
)
