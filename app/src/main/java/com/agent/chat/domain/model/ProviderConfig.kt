package com.agent.chat.domain.model

enum class ProviderType {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GOOGLE_GEMINI,
}

data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val supportsVision: Boolean = false,
    val supportsToolCalling: Boolean = true,
) {
    fun maskedApiKey(): String {
        if (apiKey.isBlank()) return "未设置"
        if (apiKey.length <= 8) return "••••"
        return apiKey.take(4) + "••••" + apiKey.takeLast(4)
    }
}
