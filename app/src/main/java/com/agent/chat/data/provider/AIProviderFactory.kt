package com.agent.chat.data.provider

import com.agent.chat.domain.model.ProviderType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据 [ProviderType] 分发到对应的 [AIProvider] 实现。
 *
 * - OPENAI_COMPATIBLE → [OpenAICompatibleProvider]（适用 OpenAI / DeepSeek / Qwen /
 *   Kimi / Groq / SiliconFlow / Ollama 等所有 OpenAI 兼容端点）
 * - ANTHROPIC          → [AnthropicProvider]（Claude 原生 API）
 * - GOOGLE_GEMINI      → [GoogleGeminiProvider]（Gemini 原生 API）
 */
@Singleton
class AIProviderFactory @Inject constructor(
    private val openAICompatibleProvider: OpenAICompatibleProvider,
    private val anthropicProvider: AnthropicProvider,
    private val googleGeminiProvider: GoogleGeminiProvider,
) : AIProvider {

    fun providerFor(type: ProviderType): AIProvider = when (type) {
        ProviderType.OPENAI_COMPATIBLE -> openAICompatibleProvider
        ProviderType.ANTHROPIC -> anthropicProvider
        ProviderType.GOOGLE_GEMINI -> googleGeminiProvider
    }

    /** 默认走 OpenAI 兼容路径，保持接口兼容性 */
    override suspend fun chatStreamEvents(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ) = openAICompatibleProvider.chatStreamEvents(messages, config)
}
