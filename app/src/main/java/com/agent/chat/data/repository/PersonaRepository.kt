package com.agent.chat.data.repository

import com.agent.chat.data.local.dao.PersonaDao
import com.agent.chat.data.local.mapper.toDomain
import com.agent.chat.data.local.mapper.toEntity
import com.agent.chat.domain.model.Persona
import com.squareup.moshi.Moshi
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao,
    moshi: Moshi,
) {

    private val exportAdapter = moshi.adapter(PersonaExportPayload::class.java)

    fun observePersonas(): Flow<List<Persona>> =
        personaDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getPersona(id: String): Persona? =
        personaDao.getById(id)?.toDomain()

    suspend fun savePersona(persona: Persona) {
        personaDao.upsert(persona.toEntity())
    }

    suspend fun createPersona(
        name: String,
        systemPrompt: String,
        avatar: String = "",
        defaultTemperature: Float = 0.7f,
        description: String = "",
        openingLine: String = "",
    ): Persona {
        val persona = Persona(
            id = "persona_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            avatar = avatar.trim(),
            systemPrompt = systemPrompt.trim(),
            defaultTemperature = defaultTemperature.coerceIn(0f, 2f),
            description = description.trim(),
            openingLine = openingLine.trim(),
        )
        personaDao.upsert(persona.toEntity())
        return persona
    }

    suspend fun updatePersona(persona: Persona) {
        personaDao.upsert(
            persona.copy(
                name = persona.name.trim(),
                avatar = persona.avatar.trim(),
                systemPrompt = persona.systemPrompt.trim(),
                defaultTemperature = persona.defaultTemperature.coerceIn(0f, 2f),
                description = persona.description.trim(),
                openingLine = persona.openingLine.trim(),
            ).toEntity(),
        )
    }

    suspend fun deletePersona(id: String) {
        personaDao.deleteById(id)
    }

    suspend fun exportToJson(): String {
        val items = personaDao.getAll().map {
            PersonaExportItem(
                id = it.id,
                name = it.name,
                avatar = it.avatar,
                systemPrompt = it.systemPrompt,
                defaultTemperature = it.defaultTemperature,
                description = it.description,
                openingLine = it.openingLine,
            )
        }
        return exportAdapter.indent("  ").toJson(PersonaExportPayload(personas = items))
    }

    suspend fun importFromJson(json: String): Int {
        val payload = exportAdapter.fromJson(json)
            ?: throw IllegalArgumentException("无法解析人设 JSON")
        if (payload.personas.isEmpty()) return 0

        val entities = payload.personas.map { item ->
            require(item.name.isNotBlank()) { "人设名称不能为空" }
            require(item.systemPrompt.isNotBlank()) { "systemPrompt 不能为空" }
            Persona(
                id = item.id?.takeIf { it.isNotBlank() }
                    ?: "persona_${UUID.randomUUID().toString().take(8)}",
                name = item.name.trim(),
                avatar = item.avatar.trim(),
                systemPrompt = item.systemPrompt.trim(),
                defaultTemperature = item.defaultTemperature.coerceIn(0f, 2f),
                description = item.description.trim(),
                openingLine = item.openingLine.trim(),
            ).toEntity()
        }
        personaDao.upsertAll(entities)
        return entities.size
    }
}
