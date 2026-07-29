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
 * Google Gemini 原生 API 实现。
 *
 * Gemini REST streaming 格式：
 *   - 请求路径：https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent
 *   - 认证：?key=API_KEY 查询参数（或 Authorization: Bearer）
 *   - 响应：SSE 流，每帧为 JSON 对象 { candidates: [{ content: { parts: [{text}] } }] }
 *
 * 支持的模型：gemini-1.5-pro-*, gemini-1.5-flash-*, gemini-2.0-*, gemini-pro-*
 */
@Singleton
class GoogleGeminiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) : AIProvider {

    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter by lazy { moshi.adapter<Map<String, Any>>(mapType) }

    override suspend fun chatStreamEvents(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ): Flow<ChatStreamEvent> {
        require(config.apiKey.isNotBlank()) { "Gemini API Key 不能为空" }
        require(config.modelName.isNotBlank()) { "模型名不能为空" }
        require(messages.isNotEmpty()) { "messages 不能为空" }

        // 将系统消息转为 systemInstruction 字段
        val systemContent = messages.firstOrNull { it.role == ChatMessage.ROLE_SYSTEM }?.content
        val conversationMessages = messages.filter { it.role != ChatMessage.ROLE_SYSTEM }

        // Gemini 用 "model" 表示 assistant 角色
        val geminiContents = conversationMessages.map { msg ->
            buildMap<String, Any> {
                put("role", if (msg.role == ChatMessage.ROLE_ASSISTANT) "model" else "user")
                put("parts", listOf(mapOf("text" to (msg.content ?: ""))))
            }
        }

        val requestBody = buildMap<String, Any> {
            put("contents", geminiContents)
            if (!systemContent.isNullOrBlank()) {
                put("systemInstruction", mapOf("parts" to listOf(mapOf("text" to systemContent))))
            }
            put("generationConfig", buildMap {
                config.temperature?.let { put("temperature", it) }
            })
        }

        val json = mapAdapter.toJson(requestBody)

        // Build URL: support both configured base URL and default googleapis
        val base = config.baseUrl.trimEnd('/')
        val url = if (base.contains("generativelanguage.googleapis.com")) {
            val modelPath = if (base.contains("/models/")) base
            else "$base/models/${config.modelName}"
            val endpoint = if (modelPath.endsWith(":streamGenerateContent")) modelPath
            else "$modelPath:streamGenerateContent"
            "$endpoint?key=${config.apiKey}"
        } else {
            // Custom base URL (e.g. Vertex AI proxy)
            "$base/models/${config.modelName}:streamGenerateContent?key=${config.apiKey}"
        }

        return flow {
            val request = Request.Builder()
                .url(url)
                .addHeader("content-type", "application/json")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw ProviderHttpException(code = response.code, body = body.ifBlank { response.message })
            }

            val body = response.body ?: throw ProviderHttpException(code = response.code, body = "响应体为空")

            body.use { rb ->
                val source = rb.source()
                val buf = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    // Gemini SSE: data lines contain JSON objects or arrays
                    if (line.startsWith("data:")) {
                        buf.append(line.removePrefix("data:").trim())
                    } else if (line.isEmpty() && buf.isNotEmpty()) {
                        val payload = buf.toString().trim()
                        buf.clear()
                        if (payload == "[DONE]") break
                        parseGeminiChunk(payload)?.let { emit(it) }
                    } else if (line.isNotEmpty() && !line.startsWith(":")) {
                        // Inline JSON (no data: prefix in some Gemini versions)
                        buf.append(line)
                    }
                }
                if (buf.isNotEmpty()) {
                    parseGeminiChunk(buf.toString().trim())?.let { emit(it) }
                }
            }
            emit(ChatStreamEvent.Finished(null))
        }.buffer(16).flowOn(Dispatchers.IO)
    }

    private fun parseGeminiChunk(json: String): ChatStreamEvent? {
        if (json.isBlank()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val parsed = mapAdapter.fromJson(json) as? Map<String, Any> ?: return null
            @Suppress("UNCHECKED_CAST")
            val candidates = parsed["candidates"] as? List<Map<String, Any>> ?: return null
            val first = candidates.firstOrNull() ?: return null
            @Suppress("UNCHECKED_CAST")
            val content = first["content"] as? Map<String, Any> ?: return null
            @Suppress("UNCHECKED_CAST")
            val parts = content["parts"] as? List<Map<String, Any>> ?: return null
            val text = parts.mapNotNull { it["text"] as? String }.joinToString("")
            if (text.isNotEmpty()) ChatStreamEvent.ContentDelta(text) else null
        } catch (_: Exception) {
            null
        }
    }
}
