package com.agent.chat.data.error

import android.util.Log
import com.agent.chat.domain.error.AppError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Provider HTTP 失败，携带状态码与响应体供映射与 Debug 日志。 */
class ProviderHttpException(
    val code: Int,
    val body: String,
) : IOException("HTTP $code: ${body.take(500)}")

object AppErrorMapper {

    private val HttpCodeRegex = Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE)

    fun from(throwable: Throwable): AppError {
        var current: Throwable? = throwable
        while (current != null) {
            mapSingle(current)?.let { return it }
            current = current.cause
        }
        return AppError.Unknown
    }

    private fun mapSingle(t: Throwable): AppError? = when (t) {
        is ProviderHttpException -> mapHttp(t.code, t.body)
        is UnknownHostException,
        is ConnectException,
        is NoRouteToHostException,
        -> AppError.NetworkUnavailable
        is SocketTimeoutException -> AppError.Timeout
        is InterruptedIOException -> {
            val msg = t.message.orEmpty().lowercase()
            if (msg.contains("timeout") || msg.contains("timed out")) {
                AppError.Timeout
            } else {
                AppError.NetworkUnavailable
            }
        }
        is SSLException -> AppError.NetworkUnavailable
        is IllegalArgumentException -> {
            val msg = t.message.orEmpty().lowercase()
            when {
                "api key" in msg || "apikey" in msg || "api_key" in msg -> AppError.InvalidApiKey
                "baseurl" in msg || "base url" in msg || "model" in msg -> AppError.InvalidApiKey
                else -> null
            }
        }
        is IllegalStateException -> {
            val msg = t.message.orEmpty().lowercase()
            if ("provider" in msg || "api" in msg || "配置" in msg) {
                AppError.InvalidApiKey
            } else {
                null
            }
        }
        is IOException -> {
            val msg = t.message.orEmpty()
            val codeMatch = HttpCodeRegex.find(msg)
            if (codeMatch != null) {
                val code = codeMatch.groupValues[1].toIntOrNull() ?: return AppError.Unknown
                mapHttp(code, msg)
            } else {
                val lower = msg.lowercase()
                when {
                    "unable to resolve host" in lower ||
                        "failed to connect" in lower ||
                        "network is unreachable" in lower ||
                        "no address associated" in lower -> AppError.NetworkUnavailable
                    "timeout" in lower || "timed out" in lower -> AppError.Timeout
                    else -> null
                }
            }
        }
        else -> null
    }

    fun mapHttp(code: Int, body: String): AppError {
        val lower = body.lowercase()
        if (
            "context_length" in lower ||
            "context length" in lower ||
            "maximum context" in lower ||
            "too many tokens" in lower ||
            "token limit" in lower ||
            "max_tokens" in lower && "exceed" in lower
        ) {
            return AppError.ContextTooLong
        }
        return when (code) {
            401, 403 -> AppError.InvalidApiKey
            408, 504 -> AppError.Timeout
            429 -> AppError.RateLimitExceeded
            413 -> AppError.ContextTooLong
            in 500..599 -> AppError.ServerError(code)
            in 400..499 -> {
                if ("invalid_api_key" in lower || "incorrect api key" in lower || "unauthorized" in lower) {
                    AppError.InvalidApiKey
                } else {
                    AppError.ServerError(code)
                }
            }
            else -> AppError.Unknown
        }
    }
}

object AppErrorLogger {
    private const val TAG = "AppError"

    fun log(throwable: Throwable, appError: AppError) {
        Log.e(TAG, "mapped=${appError::class.simpleName} raw=${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
    }
}
