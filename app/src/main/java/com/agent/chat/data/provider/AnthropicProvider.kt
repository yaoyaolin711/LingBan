package com.agent.chat.data.provider

import com.agent.chat.data.error.ProviderHttpException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic (Claude) 原生 API 实现。
 *
 * Anthropic 的流式格式与 OpenAI 不同：
 *   - 请求路径：https://api.anthropic.com/v1/messages
 *   - 认证头：x-api-key / anthropic-version
 *   - SSE 事件类型：content_block_delta（text_delta）/ message_delta / message_stop
 *
 * 支持的模型前缀：claude-3-5-*, claude-3-*, claude-opus-*, claude-sonnet-*, claude-haiku-*
 */
@Singleton
class AnthropicProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) : AIProvider {

    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter by lazy { moshi.adapter<Map<String, Any>>(mapType) }

    override suspend fun chatStreamEvents(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ): Flow<ChatStreamEvent> {
        require(config.apiKey.isNotBlank()) { "Anthropic API Key 不能为空" }
        require(config.modelName.isNotBlank()) { "模型名不能为空" }
        require(messages.isNotEmpty()) { "messages 不能为空" }

        // 将系统消息提取出来，Anthropic API system 是顶级字段
        val systemContent = messages.firstOrNull { it.role == ChatMessage.ROLE_SYSTEM }?.content
        val conversationMessages = messages.filter { it.role != ChatMessage.ROLE_SYSTEM }

        val requestBody = buildMap<String, Any> {
            put("model", config.modelName)
            put("max_tokens", 8192)
            put("stream", true)
            if (!systemContent.isNullOrBlank()) put("system", systemContent)
            put("messages", conversationMessages.map { msg ->
                buildMap {
                    put("role", if (msg.role == ChatMessage.ROLE_ASSISTANT) "assistant" else "user")
                    put("content", msg.content ?: "")
                }
            })
            config.temperature?.let { put("temperature", it) }
        }

        val json = mapAdapter.toJson(requestBody)
        val baseUrl = config.baseUrl.trimEnd('/')
        val url = if (baseUrl.endsWith("/messages")) baseUrl
        else "$baseUrl/messages"

        return flow {
            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .addHeader("accept", "text/event-stream")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw ProviderHttpException(code = response.code, body = body.ifBlank { response.message })
            }

            val body = response.body ?: throw ProviderHttpException(code = response.code, body = "响应体为空")
            var lastFinish: String? = null

            body.use { rb ->
                val source = rb.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty() || line.startsWith(":")) continue
                    if (line.startsWith("event:")) continue  // event type line
                    if (!line.startsWith("data:")) continue

                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]" || payload.isEmpty()) continue

                    val parsed = try {
                        @Suppress("UNCHECKED_CAST")
                        mapAdapter.fromJson(payload) as? Map<String, Any>
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    when (parsed["type"] as? String) {
                        "content_block_delta" -> {
                            @Suppress("UNCHECKED_CAST")
                            val delta = parsed["delta"] as? Map<String, Any>
                            val text = delta?.get("text") as? String
                            if (!text.isNullOrEmpty()) {
                                emit(ChatStreamEvent.ContentDelta(text))
                            }
                        }
                        "message_delta" -> {
                            @Suppress("UNCHECKED_CAST")
                            val delta = parsed["delta"] as? Map<String, Any>
                            lastFinish = delta?.get("stop_reason") as? String
                        }
                        "message_stop" -> {
                            // stream ended
                        }
                        else -> { /* ping / content_block_start etc. */ }
                    }
                }
            }
            emit(ChatStreamEvent.Finished(lastFinish))
        }.buffer(16).flowOn(Dispatchers.IO)
    }
}
