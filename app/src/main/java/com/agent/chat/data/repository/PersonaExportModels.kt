package com.agent.chat.data.repository

data class PersonaExportPayload(
    val version: Int = 1,
    val personas: List<PersonaExportItem>,
)

data class PersonaExportItem(
    val id: String? = null,
    val name: String,
    val avatar: String = "",
    val systemPrompt: String,
    val defaultTemperature: Float = 0.7f,
    val description: String = "",
    val openingLine: String = "",
)
