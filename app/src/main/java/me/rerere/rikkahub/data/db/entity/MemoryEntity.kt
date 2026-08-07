package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MemoryEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("content")
    val content: String = "",
    @ColumnInfo("topic_key")
    val topicKey: String? = null,
    @ColumnInfo("layer", defaultValue = "episode")
    val layer: String = MemoryLayer.EPISODE,
    @ColumnInfo("status", defaultValue = "active")
    val status: String = MemoryStatus.ACTIVE,
    @ColumnInfo("created_at", defaultValue = "0")
    val createdAt: Long = 0L,
    @ColumnInfo("updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo("supersedes_id")
    val supersedesId: Int? = null,
)

object MemoryLayer {
    const val PROFILE = "profile"
    const val EPISODE = "episode"
}

object MemoryStatus {
    const val ACTIVE = "active"
    const val SUPERSEDED = "superseded"
}
