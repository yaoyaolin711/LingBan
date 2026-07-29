package com.agent.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaId: String? = null,
    val providerConfigId: String? = null,
    /** JSON: [RelationshipProfile]；空串表示默认朋友关系 */
    val relationshipProfileJson: String = "",
    /** JSON: [ExpressionProfile]；空串表示按关系推荐默认值 */
    val expressionProfileJson: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
