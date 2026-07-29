package com.agent.chat.data.error

import com.agent.chat.domain.error.AppError
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorMapperTest {

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
