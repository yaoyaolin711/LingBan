package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryEntity

@Dao
interface MemoryDAO {
    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId AND status = :status ORDER BY updated_at DESC, id DESC")
    fun getMemoriesOfAssistantFlow(assistantId: String, status: String = "active"): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId AND status = :status ORDER BY updated_at DESC, id DESC")
    suspend fun getMemoriesOfAssistant(assistantId: String, status: String = "active"): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId ORDER BY updated_at DESC, id DESC")
    suspend fun getAllMemoriesOfAssistant(assistantId: String): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity WHERE id = :id")
    suspend fun getMemoryById(id: Int): MemoryEntity?

    @Query(
        """
        SELECT * FROM memoryentity
        WHERE assistant_id = :assistantId
          AND status = 'active'
          AND topic_key = :topicKey
        LIMIT 1
        """
    )
    suspend fun getActiveByTopic(assistantId: String, topicKey: String): MemoryEntity?

    @Query(
        """
        SELECT * FROM memoryentity
        WHERE assistant_id = :assistantId
          AND (:includeSuperseded = 1 OR status = 'active')
          AND (
            content LIKE '%' || :query || '%'
            OR IFNULL(topic_key, '') LIKE '%' || :query || '%'
          )
        ORDER BY
          CASE status WHEN 'active' THEN 0 ELSE 1 END,
          updated_at DESC,
          id DESC
        LIMIT :limit
        """
    )
    suspend fun searchMemories(
        assistantId: String,
        query: String,
        includeSuperseded: Boolean,
        limit: Int,
    ): List<MemoryEntity>

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memoryentity WHERE id = :id")
    suspend fun deleteMemory(id: Int)

    @Query("DELETE FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun deleteMemoriesOfAssistant(assistantId: String)
}
