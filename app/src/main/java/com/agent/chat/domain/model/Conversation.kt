package com.agent.chat.domain.model

import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.RelationshipProfile

data class Conversation(
    val id: String,
    val title: String,
    val personaId: String? = null,
    val providerConfigId: String? = null,
    val relationshipProfile: RelationshipProfile = RelationshipProfile(),
    /** null 表示尚未自定义，运行时按关系推荐 */
    val expressionProfile: ExpressionProfile? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String = "",
)
