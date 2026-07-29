package com.agent.chat.data.local.mapper

import com.agent.chat.data.local.dao.ConversationWithLastMessage
import com.agent.chat.data.local.entity.ConversationEntity
import com.agent.chat.data.local.entity.MemoryEntity
import com.agent.chat.data.local.entity.MessageEntity
import com.agent.chat.data.local.entity.PersonaEntity
import com.agent.chat.data.local.entity.ProviderConfigEntity
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.ProviderConfig
import com.agent.chat.domain.model.ProviderType

fun ConversationEntity.toDomain(lastMessage: String = ""): Conversation = Conversation(
    id = id,
    title = title,
    personaId = personaId,
    providerConfigId = providerConfigId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessage = lastMessage,
)

fun ConversationWithLastMessage.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    personaId = personaId,
    providerConfigId = providerConfigId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessage = lastMessage,
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    personaId = personaId,
    providerConfigId = providerConfigId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    role = when (role) {
        MessageRole.USER.name, "user" -> MessageRole.USER
        else -> MessageRole.ASSISTANT
    },
    content = content,
    createdAt = timestamp,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    timestamp = createdAt,
)

fun MemoryEntity.toDomain(): Memory = Memory(
    id = id,
    personaId = personaId,
    conversationId = conversationId,
    content = content,
    createdAt = createdAt,
    importance = importance,
)

fun Memory.toEntity(): MemoryEntity = MemoryEntity(
    id = id,
    personaId = personaId,
    conversationId = conversationId,
    content = content,
    createdAt = createdAt,
    importance = importance,
)

fun PersonaEntity.toDomain(): Persona = Persona(
    id = id,
    name = name,
    avatar = avatar,
    systemPrompt = systemPrompt,
    defaultTemperature = defaultTemperature,
    description = description,
    openingLine = openingLine,
)

fun Persona.toEntity(): PersonaEntity = PersonaEntity(
    id = id,
    name = name,
    avatar = avatar,
    systemPrompt = systemPrompt,
    defaultTemperature = defaultTemperature,
    description = description,
    openingLine = openingLine,
)

fun ProviderConfigEntity.toDomain(apiKey: String): ProviderConfig = ProviderConfig(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    providerType = runCatching { ProviderType.valueOf(providerType) }
        .getOrDefault(ProviderType.OPENAI_COMPATIBLE),
)

fun ProviderConfig.toEntity(): ProviderConfigEntity = ProviderConfigEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = "",
    modelName = modelName,
    providerType = providerType.name,
)
