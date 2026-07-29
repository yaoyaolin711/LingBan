package com.agent.chat.data.repository

import com.agent.chat.data.local.dao.ConversationDao
import com.agent.chat.data.local.dao.MessageDao
import com.agent.chat.data.local.entity.ConversationEntity
import com.agent.chat.data.local.mapper.toDomain
import com.agent.chat.data.local.mapper.toEntity
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Message
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {

    fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeConversationsWithLastMessage().map { rows ->
            rows.map { it.toDomain() }
        }

    fun searchConversations(query: String): Flow<List<Conversation>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            observeConversations()
        } else {
            conversationDao.searchConversationsWithLastMessage(trimmed).map { rows ->
                rows.map { it.toDomain() }
            }
        }
    }

    fun observeConversation(id: String): Flow<Conversation?> =
        conversationDao.observeById(id).map { it?.toDomain() }

    fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeByConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getMessages(conversationId: String): List<Message> =
        messageDao.getByConversation(conversationId).map { it.toDomain() }

    suspend fun getConversation(id: String): Conversation? =
        conversationDao.getById(id)?.toDomain()

    suspend fun createConversation(
        title: String = "新会话",
        personaId: String? = null,
        providerConfigId: String? = null,
    ): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = "conv_${UUID.randomUUID().toString().take(8)}",
            title = title,
            personaId = personaId,
            providerConfigId = providerConfigId,
            createdAt = now,
            updatedAt = now,
        )
        conversationDao.upsert(conversation.toEntity())
        return conversation
    }

    suspend fun ensureConversationExists(
        id: String,
        title: String = "新会话",
    ): Conversation {
        val existing = conversationDao.getById(id)
        if (existing != null) return existing.toDomain()

        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            id = id,
            title = title,
            personaId = null,
            providerConfigId = null,
            createdAt = now,
            updatedAt = now,
        )
        conversationDao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun saveMessage(message: Message) {
        messageDao.upsert(message.toEntity())
        conversationDao.touch(message.conversationId, message.createdAt)
    }

    suspend fun updateMessageContent(messageId: String, content: String) {
        messageDao.updateContent(messageId, content)
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteById(messageId)
    }

    suspend fun deleteMessagesFrom(conversationId: String, fromTimestamp: Long) {
        messageDao.deleteFromTimestamp(conversationId, fromTimestamp)
        conversationDao.touch(conversationId, System.currentTimeMillis())
    }

    suspend fun searchMessages(conversationId: String, query: String): List<Message> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getMessages(conversationId)
        return messageDao.searchInConversation(conversationId, trimmed).map { it.toDomain() }
    }

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        conversationDao.updateTitle(
            id = conversationId,
            title = title,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun touchConversation(conversationId: String, updatedAt: Long = System.currentTimeMillis()) {
        conversationDao.touch(conversationId, updatedAt)
    }

    suspend fun clearMessages(conversationId: String) {
        messageDao.deleteByConversation(conversationId)
        conversationDao.touch(conversationId, System.currentTimeMillis())
    }

    suspend fun updateConversationPersona(conversationId: String, personaId: String?) {
        conversationDao.updatePersona(
            id = conversationId,
            personaId = personaId,
            updatedAt = System.currentTimeMillis(),
        )
    }
}
