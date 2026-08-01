package me.rerere.rikkahub.data.agent.memory

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "agent_long_memory")
data class AgentMemoryEntity(
    @PrimaryKey val key: String,
    val value: String,
    val category: String = "general",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface AgentMemoryDao {
    @Query("SELECT * FROM agent_long_memory WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): AgentMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AgentMemoryEntity)

    @Query("DELETE FROM agent_long_memory WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM agent_long_memory WHERE updatedAt < :before")
    suspend fun pruneOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM agent_long_memory")
    suspend fun count(): Int
}

@Database(entities = [AgentMemoryEntity::class], version = 1, exportSchema = false)
abstract class AgentMemoryDatabase : RoomDatabase() {
    abstract fun agentMemoryDao(): AgentMemoryDao
}
