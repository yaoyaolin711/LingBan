package com.agent.chat.data.repository

import com.agent.chat.data.error.AppErrorException
import com.agent.chat.data.error.AppErrorLogger
import com.agent.chat.data.error.AppErrorMapper
import com.agent.chat.data.local.dao.ProviderConfigDao
import com.agent.chat.data.local.mapper.toDomain
import com.agent.chat.data.local.mapper.toEntity
import com.agent.chat.data.provider.AIProvider
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ModelConfig
import com.agent.chat.data.security.ApiKeySecureStore
import com.agent.chat.domain.model.ProviderConfig
import com.agent.chat.domain.model.ProviderType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class ProviderConfigRepository @Inject constructor(
    private val providerConfigDao: ProviderConfigDao,
    private val apiKeySecureStore: ApiKeySecureStore,
    private val aiProvider: AIProvider,
) {

    fun observeConfigs(): Flow<List<ProviderConfig>> =
        providerConfigDao.observeAll().map { entities ->
            entities.map { it.toDomain(apiKeySecureStore.get(it.id)) }
        }

    suspend fun getConfig(id: String): ProviderConfig? {
        val entity = providerConfigDao.getById(id) ?: return null
        return entity.toDomain(apiKeySecureStore.get(id))
    }

    suspend fun getDefaultConfig(): ProviderConfig? {
        val entity = providerConfigDao.getAll().firstOrNull() ?: return null
        return entity.toDomain(apiKeySecureStore.get(entity.id))
    }

    suspend fun saveConfig(config: ProviderConfig) {
        require(config.name.isNotBlank()) { "名称不能为空" }
        require(config.baseUrl.isNotBlank()) { "Base URL 不能为空" }
        require(config.modelName.isNotBlank()) { "模型名不能为空" }
        require(config.apiKey.isNotBlank()) { "API Key 不能为空" }

        providerConfigDao.upsert(config.toEntity())
        apiKeySecureStore.save(config.id, config.apiKey.trim())
    }

    suspend fun createConfig(
        name: String,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    ): ProviderConfig {
        val config = ProviderConfig(
            id = "provider_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            modelName = modelName.trim(),
            providerType = providerType,
        )
        saveConfig(config)
        return config
    }

    suspend fun updateConfig(config: ProviderConfig) {
        saveConfig(config)
    }

    suspend fun deleteConfig(id: String) {
        providerConfigDao.deleteById(id)
        apiKeySecureStore.delete(id)
    }

    fun toModelConfig(config: ProviderConfig, temperature: Float? = null): ModelConfig =
        ModelConfig(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            modelName = config.modelName,
            temperature = temperature,
        )

    suspend fun testConnection(config: ProviderConfig): Result<String> {
        return try {
            require(config.apiKey.isNotBlank()) { "API Key 不能为空" }
            val modelConfig = toModelConfig(config, temperature = 0f)
            aiProvider.chatStream(
                messages = listOf(
                    ChatMessage(
                        role = ChatMessage.ROLE_USER,
                        content = "ping",
                    ),
                ),
                config = modelConfig,
            ).first()
            Result.success("连接成功：${config.modelName} 已响应")
        } catch (e: Exception) {
            val appError = AppErrorMapper.from(e)
            AppErrorLogger.log(e, appError)
            Result.failure(AppErrorException(appError, e))
        }
    }
}
