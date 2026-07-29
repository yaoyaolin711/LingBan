package com.agent.chat.data.provider

import com.agent.chat.data.error.ProviderHttpException
import com.agent.chat.data.provider.network.ChatCompletionChunk
import com.agent.chat.data.provider.network.ChatCompletionRequest
import com.agent.chat.data.provider.network.OpenAIApi
import com.agent.chat.data.provider.network.ResponseFormat
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class OpenAICompatibleProvider @Inject constructor(
    private val openAIApi: OpenAIApi,
    moshi: Moshi,
) : AIProvider {

    private val chunkAdapter = moshi.adapter(ChatCompletionChunk::class.java)

    override suspend fun chatStreamEvents(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ): Flow<ChatStreamEvent> {
        require(config.apiKey.isNotBlank()) {
            "API Key 不能为空，请先在设置页配置 Provider"
        }
        require(config.baseUrl.isNotBlank()) { "baseUrl 不能为空" }
        require(config.modelName.isNotBlank()) { "modelName 不能为空" }
        require(messages.isNotEmpty()) { "messages 不能为空" }

        val url = buildCompletionsUrl(config.baseUrl)
        val tools = config.tools?.takeIf { it.isNotEmpty() }
        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = messages,
            stream = true,
            temperature = config.temperature,
            responseFormat = if (config.jsonObjectMode) {
                ResponseFormat(type = "json_object")
            } else {
                null
            },
            tools = tools,
            toolChoice = when {
                tools == null -> null
                config.toolChoice != null -> config.toolChoice
                else -> "auto"
            },
        )

        return flow {
            val response = openAIApi.createChatCompletion(
                url = url,
                authorization = "Bearer ${config.apiKey}",
                body = request,
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                throw ProviderHttpException(
                    code = response.code(),
                    body = errorBody.ifBlank { response.message() },
                )
            }

            val body = response.body()
                ?: throw ProviderHttpException(code = response.code(), body = "响应体为空")

            var lastFinish: String? = null
            body.use { responseBody ->
                val source = responseBody.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue

                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    if (payload.isEmpty()) continue

                    val chunk = try {
                        chunkAdapter.fromJson(payload)
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    val choice = chunk.choices.firstOrNull() ?: continue
                    choice.finishReason?.let { lastFinish = it }

                    val delta = choice.delta ?: continue
                    val content = delta.content
                    if (!content.isNullOrEmpty()) {
                        emit(ChatStreamEvent.ContentDelta(content))
                    }
                    delta.toolCalls.orEmpty().forEach { tc ->
                        emit(
                            ChatStreamEvent.ToolCallDelta(
                                index = tc.index,
                                id = tc.id,
                                name = tc.function?.name,
                                argumentsDelta = tc.function?.arguments,
                            ),
                        )
                    }
                }
            }
            emit(ChatStreamEvent.Finished(lastFinish))
        }.flowOn(Dispatchers.IO)
    }

    private fun buildCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }
}
