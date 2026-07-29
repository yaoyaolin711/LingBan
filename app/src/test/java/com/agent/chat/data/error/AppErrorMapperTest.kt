package com.agent.chat.data.error

import com.agent.chat.data.provider.ChatFunctionDefinition
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ChatToolDefinition
import com.agent.chat.data.provider.network.ChatCompletionRequest
import com.agent.chat.data.provider.network.ChatMessageJsonAdapter
import com.agent.chat.data.provider.network.ObjectJsonAdapter
import com.agent.chat.domain.error.AppError
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorMapperTest {

    @Test
    fun chatCompletionRequestCanBeSerialized() {
        val moshi = Moshi.Builder()
            .add(ChatMessageJsonAdapter.FACTORY)
            .add(ObjectJsonAdapter.FACTORY)
            .add(KotlinJsonAdapterFactory())
            .build()
        val json = moshi.adapter(ChatCompletionRequest::class.java).toJson(
            ChatCompletionRequest(
                model = "deepseek-v4-flash",
                messages = listOf(ChatMessage.user("hello")),
                tools = listOf(
                    ChatToolDefinition(
                        function = ChatFunctionDefinition(
                            name = "get_time",
                            description = "Get current time",
                            parameters = mapOf(
                                "type" to "object",
                                "properties" to emptyMap<String, Any>(),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(json.contains("deepseek-v4-flash"))
        assertTrue(json.contains("get_time"))
    }

    @Test
    fun mapsUnknownHostToNetworkUnavailable() {
        assertEquals(AppError.NetworkUnavailable, AppErrorMapper.from(UnknownHostException("host")))
    }

    @Test
    fun mapsTimeout() {
        assertEquals(AppError.Timeout, AppErrorMapper.from(SocketTimeoutException("timeout")))
    }

    @Test
    fun mapsHttp401ToInvalidApiKey() {
        val error = AppErrorMapper.from(ProviderHttpException(401, """{"error":"invalid_api_key"}"""))
        assertEquals(AppError.InvalidApiKey, error)
    }

    @Test
    fun mapsHttp429ToRateLimit() {
        assertEquals(AppError.RateLimitExceeded, AppErrorMapper.from(ProviderHttpException(429, "rate")))
    }

    @Test
    fun mapsHttp500ToServerError() {
        val error = AppErrorMapper.from(ProviderHttpException(502, "bad gateway"))
        assertTrue(error is AppError.ServerError)
        assertEquals(502, (error as AppError.ServerError).code)
    }

    @Test
    fun mapsContextLengthBody() {
        val error = AppErrorMapper.from(
            ProviderHttpException(400, """{"error":{"message":"context_length_exceeded"}}"""),
        )
        assertEquals(AppError.ContextTooLong, error)
    }

    @Test
    fun mapsBlankApiKeyRequire() {
        val error = AppErrorMapper.from(IllegalArgumentException("API Key 不能为空"))
        assertEquals(AppError.InvalidApiKey, error)
    }
}
