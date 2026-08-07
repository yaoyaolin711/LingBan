package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_entity_node",
    indices = [
        Index(value = ["assistant_id", "name"], unique = true),
    ],
)
data class MemoryEntityNode(
    @PrimaryKey(true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("type")
    val type: String = MemoryEntityType.OTHER,
    @ColumnInfo("mention_count")
    val mentionCount: Int = 1,
    @ColumnInfo("updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "memory_entity_link",
    primaryKeys = ["memory_id", "entity_id"],
    indices = [
        Index(value = ["entity_id"]),
        Index(value = ["memory_id"]),
    ],
)
data class MemoryEntityLink(
    @ColumnInfo("memory_id")
    val memoryId: Int,
    @ColumnInfo("entity_id")
    val entityId: Long,
    @ColumnInfo("role")
    val role: String = "about",
)

/**
 * Undirected entity↔entity edge (store with from_entity_id < to_entity_id).
 * Built by rule indexer from co-occurrence; optional LLM enricher may add more later.
 */
@Entity(
    tableName = "memory_entity_edge",
    primaryKeys = ["from_entity_id", "to_entity_id", "relation"],
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["from_entity_id"]),
        Index(value = ["to_entity_id"]),
    ],
)
data class MemoryEntityEdge(
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("from_entity_id")
    val fromEntityId: Long,
    @ColumnInfo("to_entity_id")
    val toEntityId: Long,
    @ColumnInfo("relation")
    val relation: String = MemoryRelationType.CO_OCCURS,
    @ColumnInfo("weight")
    val weight: Int = 1,
    @ColumnInfo("updated_at")
    val updatedAt: Long = 0L,
)

@Entity(tableName = "memory_recall_meta")
data class MemoryRecallMeta(
    @PrimaryKey
    @ColumnInfo("memory_id")
    val memoryId: Int,
    @ColumnInfo("summary_short")
    val summaryShort: String = "",
    @ColumnInfo("observed_at")
    val observedAt: Long? = null,
    @ColumnInfo("emotion_tags")
    val emotionTags: String = "",
    @ColumnInfo("importance")
    val importance: Int = 0,
    @ColumnInfo("last_recalled_at")
    val lastRecalledAt: Long = 0L,
)

/** Optional local vector cache for semantic recall boost (P3). */
@Entity(tableName = "memory_embedding")
data class MemoryEmbedding(
    @PrimaryKey
    @ColumnInfo("memory_id")
    val memoryId: Int,
    @ColumnInfo("content_hash")
    val contentHash: String,
    @ColumnInfo("dims")
    val dims: Int,
    @ColumnInfo("vector_json")
    val vectorJson: String,
    @ColumnInfo("updated_at")
    val updatedAt: Long = 0L,
)

object MemoryEntityType {
    const val PERSON = "person"
    const val PLACE = "place"
    const val EVENT = "event"
    const val PREFERENCE = "preference"
    const val OTHER = "other"
}

object MemoryRelationType {
    const val CO_OCCURS = "co_occurs"
}

object MemoryEmotionTag {
    const val WARM = "warm"
    const val STRESS = "stress"
    const val SHARED = "shared"
    const val CARE = "care"
    const val CASUAL = "casual"
}
