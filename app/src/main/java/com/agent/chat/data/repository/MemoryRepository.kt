package com.agent.chat.data.repository

import com.agent.chat.data.local.dao.MemoryDao
import com.agent.chat.data.local.mapper.toDomain
import com.agent.chat.data.local.mapper.toEntity
import com.agent.chat.data.memory.MemorySettingsStore
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.MemoryCategory
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
) {

    fun observeByPersona(personaId: String): Flow<List<Memory>> =
        memoryDao.observeByPersona(personaId).map { list -> list.map { it.toDomain() } }

    fun observeRecent(limit: Int = 8): Flow<List<Memory>> =
        memoryDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<Memory>> =
        memoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * 按重要度与时间取最近若干条，并限制总字符数，避免挤占过多上下文。
     * 已「禁止 AI 使用」的记忆不会进入 Prompt。
     */
    suspend fun getForPrompt(
        personaId: String,
        maxCount: Int = DEFAULT_PROMPT_COUNT,
        maxChars: Int = MemorySettingsStore.PROMPT_MEMORY_MAX_CHARS,
    ): List<Memory> {
        val candidates = memoryDao.getTopByPersona(personaId, maxCount).map { it.toDomain() }
            .filterNot { it.blockedFromAi }
        if (candidates.isEmpty()) return emptyList()

        val result = ArrayList<Memory>(candidates.size)
        var used = 0
        for (memory in candidates) {
            val len = memory.content.length
            if (result.isNotEmpty() && used + len > maxChars) break
            if (result.isEmpty() && len > maxChars) {
                result.add(memory.copy(content = memory.content.take(maxChars)))
                break
            }
            result.add(memory)
            used += len
        }
        return result
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

    companion object {
        const val DEFAULT_PROMPT_COUNT = 40
    }
}
