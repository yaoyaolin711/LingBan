package com.agent.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agent.chat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY timestamp ASC
        """,
    )
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getByConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        DELETE FROM messages
        WHERE conversationId = :conversationId AND timestamp >= :fromTimestamp
        """,
    )
    suspend fun deleteFromTimestamp(conversationId: String, fromTimestamp: Long)

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
          AND content LIKE '%' || :query || '%'
        ORDER BY timestamp ASC
        """,
    )
    suspend fun searchInConversation(conversationId: String, query: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}
