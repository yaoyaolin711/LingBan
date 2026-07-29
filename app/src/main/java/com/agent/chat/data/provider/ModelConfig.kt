package com.agent.chat.data.provider

data class ModelConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val temperature: Float? = null,
    /** 若 Provider 支持，请求 response_format=json_object */
    val jsonObjectMode: Boolean = false,
    /** OpenAI tools 定义；为空则不启用工具调用 */
    val tools: List<ChatToolDefinition>? = null,
    /** auto / none / required，默认 auto */
    val toolChoice: String? = null,
)

@com.squareup.moshi.JsonClass(generateAdapter = false)
data class ChatToolDefinition(
    val type: String = "function",
    val function: ChatFunctionDefinition,
)

@com.squareup.moshi.JsonClass(generateAdapter = false)
data class ChatFunctionDefinition(
    val name: String,
    val description: String,
    /** JSON Schema object（仅含 Map/List/String/Number/Boolean） */
    val parameters: Map<String, @JvmSuppressWildcards Any>,
)
