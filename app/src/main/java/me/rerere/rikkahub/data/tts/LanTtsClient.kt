package me.rerere.rikkahub.data.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.net.ConnectException
import java.net.SocketTimeoutException
private const val HEALTH_PATH = "health"
private const val SPEECH_PATH = "v1/tts/speech"

@Serializable
data class LanTtsHealthInfo(
    val status: String = "",
    val speakers: List<String> = emptyList(),
    val sampleRate: Int = 24000,
    val mode: String = "",
    val modelSize: String = "",
)

data class LanTtsRequest(
    val text: String,
    val mode: String = "custom_voice",
    val speaker: String = "Vivian",
    val language: String = "Auto",
    val instruct: String = "",
    val voiceDescription: String = "",
    val responseFormat: String = "wav",
    val speed: Float = 1.0f,
)

data class LanTtsAudio(
    val bytes: ByteArray,
    val sampleRate: Int,
    val contentType: String,
)

object LanTtsClient : KoinComponent {
    enum class HealthErrorType {
        InvalidUrl,
        HostNotFound,
        Timeout,
        ConnectionRefused,
        HttpError,
        Unknown,
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun healthCheck(baseUrl: String): Result<LanTtsHealthInfo> = runCatching {
        withContext(Dispatchers.IO) {
            val healthUrl = resolveUrl(baseUrl, HEALTH_PATH)
            val client = get<OkHttpClient>()
            val request = Request.Builder().url(healthUrl).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} from $healthUrl")
                }
                runCatching { json.decodeFromString<LanTtsHealthInfo>(body) }
                    .getOrElse {
                        LanTtsHealthInfo(status = if (body.isBlank()) "ok" else "unknown")
                    }
            }
        }
    }

    suspend fun synthesize(baseUrl: String, request: LanTtsRequest): Result<LanTtsAudio> = runCatching {
        withContext(Dispatchers.IO) {
            val url = resolveUrl(baseUrl, null, SPEECH_PATH)
            val payload = buildString {
                append('{')
                append("\"input\":").append(jsonQuote(request.text)).append(',')
                append("\"mode\":").append(jsonQuote(request.mode)).append(',')
                append("\"speaker\":").append(jsonQuote(request.speaker)).append(',')
                append("\"language\":").append(jsonQuote(request.language)).append(',')
                append("\"instruct\":").append(jsonQuote(request.instruct)).append(',')
                append("\"voice_description\":").append(jsonQuote(request.voiceDescription)).append(',')
                append("\"response_format\":").append(jsonQuote(request.responseFormat)).append(',')
                append("\"speed\":").append(request.speed)
                append('}')
            }
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            get<OkHttpClient>().newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty().take(200)
                    error("TTS service error ${response.code}: $err")
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) error("TTS service returned empty audio")
                LanTtsAudio(
                    bytes = bytes,
                    sampleRate = response.header("X-Sample-Rate")?.toIntOrNull() ?: 24000,
                    contentType = response.header("Content-Type").orEmpty(),
                )
            }
        }
    }

    fun classifyHealthError(throwable: Throwable?): HealthErrorType {
        if (throwable == null) return HealthErrorType.Unknown
        val root = throwable.cause ?: throwable
        return when {
            throwable.message?.contains("Invalid TTS service URL") == true -> HealthErrorType.InvalidUrl
            root is java.net.UnknownHostException -> HealthErrorType.HostNotFound
            root is SocketTimeoutException -> HealthErrorType.Timeout
            root is ConnectException -> HealthErrorType.ConnectionRefused
            throwable.message?.startsWith("HTTP ") == true -> HealthErrorType.HttpError
            else -> HealthErrorType.Unknown
        }
    }

    fun healthHint(type: HealthErrorType): String {
        return when (type) {
            HealthErrorType.InvalidUrl -> "地址格式不正确，请使用 http://IP:端口"
            HealthErrorType.HostNotFound -> "找不到这台电脑，请确认手机和电脑在同一 WiFi"
            HealthErrorType.Timeout -> "连接超时，请检查电脑是否休眠或模型是否仍在加载"
            HealthErrorType.ConnectionRefused -> "电脑服务没启动或端口被拦截，请检查 Docker 和防火墙（8877）"
            HealthErrorType.HttpError -> "服务有响应但接口异常，请检查 Qwen3-TTS 服务是否正确"
            HealthErrorType.Unknown -> "未知错误，请复制排障信息发给开发者"
        }
    }

    fun buildDiagnostics(baseUrl: String, throwable: Throwable?): String {
        val type = classifyHealthError(throwable)
        val message = throwable?.message ?: "unknown error"
        return buildString {
            appendLine("=== LAN TTS Diagnostics ===")
            appendLine("Service URL: $baseUrl")
            appendLine("Error Type: $type")
            appendLine("Error Message: $message")
            appendLine("Hint: ${healthHint(type)}")
            appendLine("Checklist:")
            appendLine("1) Computer and phone on same WiFi")
            appendLine("2) Docker / qwen3-tts-lan is running")
            appendLine("3) Port 8877 is reachable from phone")
            append("4) URL format is http://LAN_IP:8877")
        }
    }

    private fun resolveUrl(baseUrl: String, firstPath: String?, vararg more: String): String {
        val base = baseUrl.trim()
        val parsed = base.toHttpUrlOrNull()
            ?: error("Invalid TTS service URL: $base")
        val builder = parsed.newBuilder()
        if (!firstPath.isNullOrBlank()) {
            builder.addPathSegment(firstPath)
        }
        more.forEach { segment ->
            segment.split('/').filter { it.isNotBlank() }.forEach { builder.addPathSegment(it) }
        }
        return builder.build().toString()
    }

    private fun jsonQuote(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
