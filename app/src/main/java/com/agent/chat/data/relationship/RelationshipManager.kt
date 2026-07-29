package com.agent.chat.data.relationship

import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.normalized
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 会话级关系档案管理（独立于 Persona）。
 */
@Singleton
class RelationshipManager @Inject constructor(
    private val chatRepository: ChatRepository,
    private val codec: RelationshipProfileCodec,
) {

    fun observe(conversationId: String): Flow<RelationshipProfile> =
        chatRepository.observeConversation(conversationId).map { conversation ->
            conversation?.relationshipProfile ?: RelationshipProfile()
        }

    suspend fun get(conversationId: String): RelationshipProfile =
        chatRepository.getConversation(conversationId)?.relationshipProfile
            ?: RelationshipProfile()

    suspend fun save(conversationId: String, profile: RelationshipProfile) {
        chatRepository.updateConversationRelationship(
            conversationId = conversationId,
            relationshipProfile = profile.normalized(),
        )
    }
}
