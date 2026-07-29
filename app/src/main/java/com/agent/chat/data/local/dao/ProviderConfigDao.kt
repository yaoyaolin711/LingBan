package com.agent.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agent.chat.data.local.entity.ProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {

    @Query("SELECT * FROM provider_configs ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ProviderConfigEntity>

    @Query("SELECT * FROM provider_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ProviderConfigEntity)

    @Query("DELETE FROM provider_configs WHERE id = :id")
    suspend fun deleteById(id: String)
}
