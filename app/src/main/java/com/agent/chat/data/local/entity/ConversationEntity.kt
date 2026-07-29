package com.agent.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaId: String? = null,
    val providerConfigId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
