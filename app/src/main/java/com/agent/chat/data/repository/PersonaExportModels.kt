package com.agent.chat.data.repository

import com.agent.chat.domain.model.LorebookEntry
import com.agent.chat.domain.model.OutputRegex
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PresetMessage

data class PersonaExportPayload(
    /** v3：增加可选 Persona Engine [PersonaProfile] */
    val version: Int = 3,
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
    val presetMessages: List<PresetMessage> = emptyList(),
    val lorebookEntries: List<LorebookEntry> = emptyList(),
    val outputRegexes: List<OutputRegex> = emptyList(),
    /** 结构化人设；旧导出 JSON 无此字段时为 null */
    val profile: PersonaProfile? = null,
)
