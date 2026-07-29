package com.agent.chat.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["personaId"]),
        Index(value = ["conversationId"]),
        Index(value = ["category"]),
    ],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val conversationId: String,
    val content: String,
    val createdAt: Long,
    val importance: Int = 5,
    val category: String = "short_term",
    val blockedFromAi: Boolean = false,
)
