package com.agent.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agent.chat.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query(
        """
        SELECT * FROM memories
        WHERE personaId = :personaId
        ORDER BY importance DESC, createdAt DESC
        """,
    )
    fun observeByPersona(personaId: String): Flow<List<MemoryEntity>>

    @Query(
        """
        SELECT * FROM memories
        WHERE personaId = :personaId
        ORDER BY importance DESC, createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getTopByPersona(personaId: String, limit: Int): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE conversationId = :conversationId
        ORDER BY createdAt DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestByConversation(conversationId: String): MemoryEntity?

    @Query(
        """
        SELECT * FROM memories
        WHERE personaId = :personaId
        ORDER BY createdAt DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestByPersona(personaId: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MemoryEntity?

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memories WHERE personaId = :personaId")
    suspend fun deleteByPersona(personaId: String)

    @Query("DELETE FROM memories WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}
