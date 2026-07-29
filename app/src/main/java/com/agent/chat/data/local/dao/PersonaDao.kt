package com.agent.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agent.chat.data.local.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {

    @Query("SELECT * FROM personas ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PersonaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: PersonaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(personas: List<PersonaEntity>)

    @Update
    suspend fun update(persona: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun deleteById(id: String)
}
