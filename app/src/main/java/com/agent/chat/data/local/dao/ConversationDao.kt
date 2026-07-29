package com.agent.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agent.chat.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query(
        """
        SELECT c.*,
               COALESCE(
                   (SELECT m.content FROM messages m
                    WHERE m.conversationId = c.id
                    ORDER BY m.timestamp DESC LIMIT 1),
                   ''
               ) AS lastMessage
        FROM conversations c
        ORDER BY c.updatedAt DESC
        """,
    )
    fun observeConversationsWithLastMessage(): Flow<List<ConversationWithLastMessage>>

    @Query(
        """
        SELECT c.*,
               COALESCE(
                   (SELECT m.content FROM messages m
                    WHERE m.conversationId = c.id
                    ORDER BY m.timestamp DESC LIMIT 1),
                   ''
               ) AS lastMessage
        FROM conversations c
        WHERE c.title LIKE '%' || :query || '%'
           OR EXISTS (
                SELECT 1 FROM messages m
                WHERE m.conversationId = c.id
                  AND m.content LIKE '%' || :query || '%'
           )
        ORDER BY c.updatedAt DESC
        """,
    )
    fun searchConversationsWithLastMessage(query: String): Flow<List<ConversationWithLastMessage>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query(
        """
        UPDATE conversations
        SET personaId = :personaId, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updatePersona(id: String, personaId: String?, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}

data class ConversationWithLastMessage(
    val id: String,
    val title: String,
    val personaId: String?,
    val providerConfigId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String,
)
