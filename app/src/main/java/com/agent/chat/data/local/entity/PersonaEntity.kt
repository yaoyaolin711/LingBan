package com.agent.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatar: String,
    val systemPrompt: String,
    val defaultTemperature: Float,
    val description: String,
    val openingLine: String = "",
    /** JSON: List<PresetMessage> */
    val presetMessagesJson: String = "[]",
    /** JSON: List<LorebookEntry> */
    val lorebookJson: String = "[]",
    /** JSON: List<OutputRegex> */
    val outputRegexesJson: String = "[]",
)
