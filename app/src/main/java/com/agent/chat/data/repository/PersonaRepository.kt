package com.agent.chat.data.repository

import com.agent.chat.data.local.dao.PersonaDao
import com.agent.chat.data.local.mapper.toDomain
import com.agent.chat.data.local.mapper.toEntity
import com.agent.chat.data.persona.PersonaExtrasCodec
import com.agent.chat.domain.model.LorebookEntry
import com.agent.chat.domain.model.OutputRegex
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PresetMessage
import com.agent.chat.domain.model.normalized
import com.squareup.moshi.Moshi
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao,
    private val extrasCodec: PersonaExtrasCodec,
    moshi: Moshi,
) {

    private val exportAdapter = moshi.adapter(PersonaExportPayload::class.java)

    fun observePersonas(): Flow<List<Persona>> =
        personaDao.observeAll().map { list -> list.map { it.toDomain(extrasCodec) } }

    suspend fun getPersona(id: String): Persona? =
        personaDao.getById(id)?.toDomain(extrasCodec)

    suspend fun savePersona(persona: Persona) {
        personaDao.upsert(persona.toEntity(extrasCodec))
    }

    suspend fun createPersona(
        name: String,
        systemPrompt: String,
        avatar: String = "",
        defaultTemperature: Float = 0.7f,
        description: String = "",
        openingLine: String = "",
        presetMessages: List<PresetMessage> = emptyList(),
        lorebookEntries: List<LorebookEntry> = emptyList(),
        outputRegexes: List<OutputRegex> = emptyList(),
        profile: PersonaProfile? = null,
    ): Persona {
        val persona = Persona(
            id = "persona_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            avatar = avatar.trim(),
            systemPrompt = systemPrompt.trim(),
            defaultTemperature = defaultTemperature.coerceIn(0f, 2f),
            description = description.trim(),
            openingLine = openingLine.trim(),
            presetMessages = presetMessages,
            lorebookEntries = lorebookEntries,
            outputRegexes = outputRegexes,
            profile = profile?.normalized(),
        )
        personaDao.upsert(persona.toEntity(extrasCodec))
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
                profile = persona.profile?.normalized(),
            ).toEntity(extrasCodec),
        )
    }

    suspend fun deletePersona(id: String) {
        personaDao.deleteById(id)
    }

    suspend fun exportToJson(): String {
        val items = personaDao.getAll().map {
            val persona = it.toDomain(extrasCodec)
            PersonaExportItem(
                id = persona.id,
                name = persona.name,
                avatar = persona.avatar,
                systemPrompt = persona.systemPrompt,
                defaultTemperature = persona.defaultTemperature,
                description = persona.description,
                openingLine = persona.openingLine,
                presetMessages = persona.presetMessages,
                lorebookEntries = persona.lorebookEntries,
                outputRegexes = persona.outputRegexes,
                profile = persona.profile,
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
                presetMessages = item.presetMessages,
                lorebookEntries = item.lorebookEntries,
                outputRegexes = item.outputRegexes,
                profile = item.profile?.normalized(),
            ).toEntity(extrasCodec)
        }
        personaDao.upsertAll(entities)
        return entities.size
    }
}
