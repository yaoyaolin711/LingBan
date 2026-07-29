package com.agent.chat.data.repository

import com.agent.chat.data.local.dao.MemoryDao
import com.agent.chat.data.local.mapper.toDomain
import com.agent.chat.data.local.mapper.toEntity
import com.agent.chat.data.memory.MemoryManager
import com.agent.chat.data.memory.MemoryRetrieveRequest
import com.agent.chat.data.memory.MemoryRetrieveResult
import com.agent.chat.data.memory.MemorySettingsStore
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.MemoryCategory
import com.agent.chat.domain.model.Message
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val memoryManager: MemoryManager,
) {

    fun observeByPersona(personaId: String): Flow<List<Memory>> =
        memoryDao.observeByPersona(personaId).map { list -> list.map { it.toDomain() } }

    fun observeRecent(limit: Int = 8): Flow<List<Memory>> =
        memoryDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<Memory>> =
        memoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * 按当前问题检索相关记忆（分类 + 重要性 + token 预算），不灌全量历史。
     */
    suspend fun retrieveForPrompt(
        personaId: String,
        queryText: String = "",
        recentMessages: List<Message> = emptyList(),
        maxTokens: Int = MemorySettingsStore.PROMPT_MEMORY_MAX_TOKENS,
        maxItems: Int = MemoryManager.DEFAULT_MAX_ITEMS,
    ): MemoryRetrieveResult =
        memoryManager.retrieveForPrompt(
            MemoryRetrieveRequest(
                personaId = personaId,
                queryText = queryText,
                recentMessages = recentMessages,
                maxTokens = maxTokens,
                maxItems = maxItems,
            ),
        )

    /**
     * 兼容旧调用：无查询词时仍走 MemoryManager（偏 Core / 高重要度）。
     */
    suspend fun getForPrompt(
        personaId: String,
        maxCount: Int = MemoryManager.DEFAULT_MAX_ITEMS,
        maxChars: Int = MemorySettingsStore.PROMPT_MEMORY_MAX_CHARS,
    ): List<Memory> {
        val maxTokens = (maxChars / 1.5f).toInt()
            .coerceIn(1, MemorySettingsStore.PROMPT_MEMORY_MAX_TOKENS)
        return memoryManager.retrieveForPrompt(
            MemoryRetrieveRequest(
                personaId = personaId,
                queryText = "",
                maxTokens = maxTokens,
                maxItems = maxCount,
            ),
        ).memories
    }

    suspend fun getLatestByConversation(conversationId: String): Memory? =
        memoryDao.getLatestByConversation(conversationId)?.toDomain()

    suspend fun getLatestByPersona(personaId: String): Memory? =
        memoryDao.getLatestByPersona(personaId)?.toDomain()

    suspend fun getById(id: String): Memory? =
        memoryDao.getById(id)?.toDomain()

    suspend fun saveMemory(
        personaId: String,
        conversationId: String,
        content: String,
        importance: Int = 5,
        category: MemoryCategory? = null,
    ): Memory {
        val trimmed = content.trim()
        val importanceClamped = importance.coerceIn(1, 10)
        val now = System.currentTimeMillis()
        val memory = Memory(
            id = "mem_${UUID.randomUUID().toString().take(8)}",
            personaId = personaId,
            conversationId = conversationId,
            content = trimmed,
            createdAt = now,
            importance = importanceClamped,
            category = category ?: MemoryCategory.infer(trimmed, importanceClamped, now),
            blockedFromAi = false,
        )
        memoryDao.upsert(memory.toEntity())
        return memory
    }

    suspend fun updateMemoryContent(
        memory: Memory,
        content: String,
        importance: Int = memory.importance,
        conversationId: String = memory.conversationId,
    ): Memory {
        val trimmed = content.trim()
        val importanceClamped = importance.coerceIn(1, 10)
        val updated = memory.copy(
            content = trimmed,
            importance = importanceClamped,
            conversationId = conversationId,
            createdAt = System.currentTimeMillis(),
            category = MemoryCategory.infer(trimmed, importanceClamped, memory.createdAt),
        )
        memoryDao.upsert(updated.toEntity())
        return updated
    }

    suspend fun updateMemory(memory: Memory) {
        memoryDao.upsert(memory.toEntity())
    }

    suspend fun setBlockedFromAi(id: String, blocked: Boolean) {
        val current = memoryDao.getById(id)?.toDomain() ?: return
        memoryDao.upsert(current.copy(blockedFromAi = blocked).toEntity())
    }

    suspend fun deleteMemory(id: String) {
        memoryDao.deleteById(id)
    }

    suspend fun deleteByPersona(personaId: String) {
        memoryDao.deleteByPersona(personaId)
    }
}
