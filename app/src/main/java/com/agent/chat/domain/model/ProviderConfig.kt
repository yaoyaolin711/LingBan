package com.agent.chat.domain.model

enum class ProviderType {
    OPENAI_COMPATIBLE,
}

data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
) {
    fun maskedApiKey(): String {
        if (apiKey.isBlank()) return "未设置"
        if (apiKey.length <= 8) return "••••"
        return apiKey.take(4) + "••••" + apiKey.takeLast(4)
    }
}
