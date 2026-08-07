package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryEmbedding
import me.rerere.rikkahub.data.db.entity.MemoryEntityEdge
import me.rerere.rikkahub.data.db.entity.MemoryEntityLink
import me.rerere.rikkahub.data.db.entity.MemoryEntityNode
import me.rerere.rikkahub.data.db.entity.MemoryRecallMeta

@Dao
interface MemoryIndexDAO {
    @Query("SELECT * FROM memory_entity_node WHERE assistant_id = :assistantId ORDER BY mention_count DESC, updated_at DESC")
    fun observeEntities(assistantId: String): Flow<List<MemoryEntityNode>>

    @Query("SELECT * FROM memory_entity_node WHERE assistant_id = :assistantId ORDER BY mention_count DESC, updated_at DESC LIMIT :limit")
    suspend fun listEntities(assistantId: String, limit: Int = 80): List<MemoryEntityNode>

    @Query("SELECT * FROM memory_entity_node WHERE assistant_id = :assistantId AND name = :name LIMIT 1")
    suspend fun findEntity(assistantId: String, name: String): MemoryEntityNode?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntity(node: MemoryEntityNode): Long

    @Update
    suspend fun updateEntity(node: MemoryEntityNode)

    @Query("SELECT * FROM memory_entity_link WHERE memory_id = :memoryId")
    suspend fun linksForMemory(memoryId: Int): List<MemoryEntityLink>

    @Query("SELECT * FROM memory_entity_link WHERE entity_id = :entityId")
    suspend fun linksForEntity(entityId: Long): List<MemoryEntityLink>

    @Query(
        """
        SELECT l.* FROM memory_entity_link l
        INNER JOIN memory_entity_node n ON n.id = l.entity_id
        WHERE n.assistant_id = :assistantId
        """
    )
    suspend fun allLinksForAssistant(assistantId: String): List<MemoryEntityLink>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(link: MemoryEntityLink)

    @Query("DELETE FROM memory_entity_link WHERE memory_id = :memoryId")
    suspend fun deleteLinksForMemory(memoryId: Int)

    @Query(
        """
        SELECT * FROM memory_entity_edge
        WHERE assistant_id = :assistantId
        ORDER BY weight DESC, updated_at DESC
        """
    )
    suspend fun allEdgesForAssistant(assistantId: String): List<MemoryEntityEdge>

    @Query(
        """
        SELECT * FROM memory_entity_edge
        WHERE from_entity_id = :entityId OR to_entity_id = :entityId
        """
    )
    suspend fun edgesForEntity(entityId: Long): List<MemoryEntityEdge>

    @Query(
        """
        SELECT * FROM memory_entity_edge
        WHERE from_entity_id = :fromId AND to_entity_id = :toId AND relation = :relation
        LIMIT 1
        """
    )
    suspend fun findEdge(fromId: Long, toId: Long, relation: String): MemoryEntityEdge?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdge(edge: MemoryEntityEdge)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: MemoryRecallMeta)

    @Query("SELECT * FROM memory_recall_meta WHERE memory_id = :memoryId")
    suspend fun getMeta(memoryId: Int): MemoryRecallMeta?

    @Query("SELECT * FROM memory_recall_meta WHERE memory_id IN (:ids)")
    suspend fun getMetaForIds(ids: List<Int>): List<MemoryRecallMeta>

    @Query("DELETE FROM memory_recall_meta WHERE memory_id = :memoryId")
    suspend fun deleteMeta(memoryId: Int)

    @Query("UPDATE memory_recall_meta SET last_recalled_at = :at WHERE memory_id IN (:ids)")
    suspend fun markRecalled(ids: List<Int>, at: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: MemoryEmbedding)

    @Query("SELECT * FROM memory_embedding WHERE memory_id = :memoryId")
    suspend fun getEmbedding(memoryId: Int): MemoryEmbedding?

    @Query("SELECT * FROM memory_embedding WHERE memory_id IN (:ids)")
    suspend fun getEmbeddings(ids: List<Int>): List<MemoryEmbedding>

    @Query("DELETE FROM memory_embedding WHERE memory_id = :memoryId")
    suspend fun deleteEmbedding(memoryId: Int)
}
