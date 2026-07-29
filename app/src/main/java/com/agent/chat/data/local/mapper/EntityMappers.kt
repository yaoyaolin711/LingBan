package com.agent.chat.data.local.mapper

import com.agent.chat.data.local.dao.ConversationWithLastMessage
import com.agent.chat.data.local.entity.ConversationEntity
import com.agent.chat.data.local.entity.MemoryEntity
import com.agent.chat.data.local.entity.MessageEntity
import com.agent.chat.data.local.entity.PersonaEntity
import com.agent.chat.data.local.entity.ProviderConfigEntity
import com.agent.chat.data.persona.PersonaExtrasCodec
import com.agent.chat.data.expression.decodeExpressionProfile
import com.agent.chat.data.expression.encodeExpressionProfile
import com.agent.chat.data.relationship.decodeRelationshipProfile
import com.agent.chat.data.relationship.encodeRelationshipProfile
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.MemoryCategory
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
    relationshipProfile = decodeRelationshipProfile(relationshipProfileJson),
    expressionProfile = decodeExpressionProfile(expressionProfileJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessage = lastMessage,
)

fun ConversationWithLastMessage.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    personaId = personaId,
    providerConfigId = providerConfigId,
    relationshipProfile = decodeRelationshipProfile(relationshipProfileJson),
    expressionProfile = decodeExpressionProfile(expressionProfileJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessage = lastMessage,
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    personaId = personaId,
    providerConfigId = providerConfigId,
    relationshipProfileJson = encodeRelationshipProfile(relationshipProfile),
    expressionProfileJson = expressionProfile?.let { encodeExpressionProfile(it) }.orEmpty(),
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
    imageUri = imageUri,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    timestamp = createdAt,
    imageUri = imageUri,
)

fun MemoryEntity.toDomain(): Memory {
    val inferred = MemoryCategory.fromStorage(category).let { stored ->
        if (category.isBlank()) {
            MemoryCategory.infer(content, importance, createdAt)
        } else {
            stored
        }
    }
    return Memory(
        id = id,
        personaId = personaId,
        conversationId = conversationId,
        content = content,
        createdAt = createdAt,
        importance = importance,
        category = inferred,
        blockedFromAi = blockedFromAi,
    )
}

fun Memory.toEntity(): MemoryEntity = MemoryEntity(
    id = id,
    personaId = personaId,
    conversationId = conversationId,
    content = content,
    createdAt = createdAt,
    importance = importance,
    category = category.storageKey,
    blockedFromAi = blockedFromAi,
)

fun PersonaEntity.toDomain(codec: PersonaExtrasCodec): Persona = Persona(
    id = id,
    name = name,
    avatar = avatar,
    systemPrompt = systemPrompt,
    defaultTemperature = defaultTemperature,
    description = description,
    openingLine = openingLine,
    presetMessages = codec.decodePresets(presetMessagesJson),
    lorebookEntries = codec.decodeLorebook(lorebookJson),
    outputRegexes = codec.decodeRegexes(outputRegexesJson),
    profile = codec.decodeProfile(personaProfileJson),
)

fun Persona.toEntity(codec: PersonaExtrasCodec): PersonaEntity = PersonaEntity(
    id = id,
    name = name,
    avatar = avatar,
    systemPrompt = systemPrompt,
    defaultTemperature = defaultTemperature,
    description = description,
    openingLine = openingLine,
    presetMessagesJson = codec.encodePresets(presetMessages),
    lorebookJson = codec.encodeLorebook(lorebookEntries),
    outputRegexesJson = codec.encodeRegexes(outputRegexes),
    personaProfileJson = codec.encodeProfile(profile),
)

fun ProviderConfigEntity.toDomain(apiKey: String): ProviderConfig = ProviderConfig(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    providerType = runCatching { ProviderType.valueOf(providerType) }
        .getOrDefault(ProviderType.OPENAI_COMPATIBLE),
    isEnabled = isEnabled,
    sortOrder = sortOrder,
    supportsVision = supportsVision,
    supportsToolCalling = supportsToolCalling,
)

fun ProviderConfig.toEntity(): ProviderConfigEntity = ProviderConfigEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = "",
    modelName = modelName,
    providerType = providerType.name,
    isEnabled = isEnabled,
    sortOrder = sortOrder,
    supportsVision = supportsVision,
    supportsToolCalling = supportsToolCalling,
)
