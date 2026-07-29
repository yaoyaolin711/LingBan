package com.agent.chat.data.expression

import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.normalized
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ExpressionManager @Inject constructor(
    private val chatRepository: ChatRepository,
) {

    fun observe(conversationId: String): Flow<ExpressionProfile> =
        chatRepository.observeConversation(conversationId).map { conversation ->
            resolve(conversation?.expressionProfile, conversation?.relationshipProfile)
        }

    suspend fun get(conversationId: String): ExpressionProfile {
        val conversation = chatRepository.getConversation(conversationId)
        return resolve(conversation?.expressionProfile, conversation?.relationshipProfile)
    }

    suspend fun save(conversationId: String, profile: ExpressionProfile) {
        chatRepository.updateConversationExpression(
            conversationId = conversationId,
            expressionProfile = profile.normalized(),
        )
    }

    suspend fun applyRecommended(conversationId: String, relationship: RelationshipProfile) {
        save(conversationId, ExpressionDefaults.recommend(relationship))
    }

    fun resolve(
        stored: ExpressionProfile?,
        relationship: RelationshipProfile?,
    ): ExpressionProfile {
        if (stored != null) return stored
        return ExpressionDefaults.recommend(relationship ?: RelationshipProfile())
    }
}
