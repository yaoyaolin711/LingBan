package me.rerere.rikkahub.data.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val HEALTH_PATH = "health"
private const val OCR_PATH = "ocr"

object LanOcrClient : KoinComponent {
    enum class HealthErrorType {
        InvalidUrl,
        HostNotFound,
        Timeout,
        ConnectionRefused,
        HttpError,
        Unknown,
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun healthCheck(baseUrl: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val healthUrl = resolveUrl(baseUrl, HEALTH_PATH)
            val rootUrl = resolveUrl(baseUrl, null)
            val client = get<OkHttpClient>()
            val healthResult = tryRequest(client, healthUrl)
            if (healthResult.isSuccess) return@withContext
            val rootResult = tryRequest(client, rootUrl)
            if (rootResult.isSuccess) return@withContext

            throw healthResult.exceptionOrNull()
                ?: rootResult.exceptionOrNull()
                ?: IllegalStateException("Cannot connect to OCR service")
        }
    }

    fun classifyHealthError(throwable: Throwable?): HealthErrorType {
        if (throwable == null) return HealthErrorType.Unknown
        val root = throwable.cause ?: throwable
        return when {
            throwable.message?.contains("Invalid OCR service URL") == true -> HealthErrorType.InvalidUrl
            root is UnknownHostException -> HealthErrorType.HostNotFound
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
            HealthErrorType.Timeout -> "连接超时，请检查电脑是否休眠或网络是否稳定"
            HealthErrorType.ConnectionRefused -> "电脑服务没启动或端口被拦截，请检查 Docker 和防火墙"
            HealthErrorType.HttpError -> "服务有响应但接口异常，请检查 OCR 服务镜像是否正确"
            HealthErrorType.Unknown -> "未知错误，请复制排障信息发给开发者"
        }
    }

    fun buildDiagnostics(baseUrl: String, throwable: Throwable?): String {
        val type = classifyHealthError(throwable)
        val message = throwable?.message ?: "unknown error"
        return buildString {
            appendLine("=== LAN OCR Diagnostics ===")
            appendLine("Service URL: $baseUrl")
            appendLine("Error Type: $type")
            appendLine("Error Message: $message")
            appendLine("Hint: ${healthHint(type)}")
            appendLine("Checklist:")
            appendLine("1) Computer and phone on same WiFi")
            appendLine("2) Docker service is running")
            appendLine("3) Port is reachable from phone")
            append("4) URL format is http://LAN_IP:PORT")
        }
    }

    private fun tryRequest(client: OkHttpClient, url: String): Result<Unit> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} from $url")
                }
            }
        }
    }

    suspend fun recognize(baseUrl: String, imageFile: File): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            require(imageFile.exists()) { "Image file not found: ${imageFile.absolutePath}" }
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    name = "file",
                    filename = imageFile.name,
                    body = imageFile.asRequestBody("application/octet-stream".toMediaType())
                )
                // Common PaddleOCR wrappers also accept "image" field.
                .addFormDataPart(
                    name = "image",
                    filename = imageFile.name,
                    body = imageFile.asRequestBody("application/octet-stream".toMediaType())
                )
                .build()
            val request = Request.Builder()
                .url(resolveUrl(baseUrl, OCR_PATH))
                .post(requestBody)
                .build()

            get<OkHttpClient>().newCall(request).execute().use { response ->
                val responseText = response.body.string()
                if (!response.isSuccessful) {
                    error("OCR service error ${response.code}: ${responseText.take(200)}")
                }
                extractTextFromResponse(responseText).ifBlank {
                    error("OCR response does not contain recognizable text")
                }
            }
        }
    }

    private fun resolveUrl(baseUrl: String, path: String?): String {
        val base = baseUrl.trim()
        val parsed = base.toHttpUrlOrNull()
            ?: error("Invalid OCR service URL: $base")
        if (path.isNullOrBlank()) {
            return parsed.toString()
        }
        return parsed.newBuilder()
            .addPathSegment(path)
            .build()
            .toString()
    }

    private fun extractTextFromResponse(raw: String): String {
        val body = raw.trim()
        if (body.isEmpty()) return ""

        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
        return when (parsed) {
            null -> body
            is JsonObject -> extractFromObject(parsed).ifBlank { body }
            is JsonArray -> parsed.joinToString("\n") { flattenJsonText(it) }.trim()
            else -> flattenJsonText(parsed)
        }
    }

    private fun extractFromObject(obj: JsonObject): String {
        val keys = listOf(
            "text",
            "result",
            "ocr_text",
            "ocrResult",
            "data",
            "results",
            "rec_texts",
            "lines",
            "content"
        )
        for (key in keys) {
            obj[key]?.let { value ->
                val content = flattenJsonText(value).trim()
                if (content.isNotBlank()) {
                    return content
                }
            }
        }
        return obj.values.joinToString("\n") { flattenJsonText(it) }.trim()
    }

    private fun flattenJsonText(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.content
        is JsonArray -> value.joinToString("\n") { flattenJsonText(it) }
        is JsonObject -> {
            val primary = extractFromObject(value)
            if (primary.isNotBlank()) primary else value.values.joinToString("\n") { flattenJsonText(it) }
        }
    }.trim()
}
