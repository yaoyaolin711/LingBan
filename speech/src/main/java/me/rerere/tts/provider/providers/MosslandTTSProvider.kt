package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "MosslandTTSProvider"
private const val DEFAULT_API_BASE = "https://api.mosi.cn"
private const val LEGACY_STUDIO_BASE = "https://studio.mosi.cn"

/**
 * Mossland / MOSI Studio TTS (模思开放平台).
 *
 * Official contract:
 *   POST https://api.mosi.cn/v1/audio/speech
 *   Authorization: Bearer <MOSS_API_KEY>
 *   body: { model, input, voice_id|voice, response_format, delivery_method? }
 *
 * [studio.mosi.cn](https://studio.mosi.cn) is the console host and rejects API keys
 * with 4010; always call [api.mosi.cn](https://api.mosi.cn).
 */
class MosslandTTSProvider : TTSProvider<TTSProviderSetting.Mossland> {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Mossland,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        require(providerSetting.apiKey.isNotBlank()) { "Mossland API Key is required" }
        require(providerSetting.voiceId.isNotBlank()) { "Mossland voice_id is required" }

        val model = normalizeModel(providerSetting.model)
        val voiceId = providerSetting.voiceId.trim()
        val format = providerSetting.format.ifBlank { "mp3" }.lowercase()
        val apiKey = providerSetting.apiKey.trim()
        val base = normalizeBaseUrl(providerSetting.baseUrl)

        // Official quick-start shape. Prefer binary audio so we can play immediately.
        val requestBody = JSONObject().apply {
            put("model", model)
            put("input", request.text)
            put("voice_id", voiceId)
            put("voice", voiceId)
            put("response_format", format)
            put("delivery_method", "audio")
        }.toString()

        val url = "$base/v1/audio/speech"
        Log.i(TAG, "generateSpeech: POST $url model=$model voiceId=$voiceId format=$format")

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, audio/*, */*")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        val bodyBytes = response.body?.bytes() ?: ByteArray(0)
        val contentType = response.header("Content-Type").orEmpty()
        if (!response.isSuccessful) {
            val errorText = bodyBytes.toString(Charsets.UTF_8).take(500)
            Log.e(TAG, "generateSpeech failed: ${response.code} $url :: $errorText")
            throw Exception(formatMosslandError(response.code, errorText))
        }

        var audioData = decodeAudioPayload(bodyBytes, contentType)
        // delivery_method=url may return JSON with a downloadable URL
        if ((audioData.isEmpty() || !looksLikeAudio(audioData)) && contentType.contains("json", ignoreCase = true)) {
            val downloaded = downloadFromUrlPayload(bodyBytes, apiKey)
            if (downloaded != null) {
                audioData = downloaded.first
            }
        }

        if (audioData.isEmpty() || (!looksLikeAudio(audioData) && audioData.size <= 64)) {
            throw Exception("Mossland TTS returned empty audio")
        }

        emit(
            AudioChunk(
                data = audioData,
                format = detectFormat(audioData, contentType, format),
                sampleRate = 24000,
                isLast = true,
                metadata = mapOf(
                    "provider" to "mossland",
                    "model" to model,
                    "voiceId" to voiceId,
                ),
            )
        )
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when {
            trimmed.isBlank() -> DEFAULT_API_BASE
            trimmed.equals(LEGACY_STUDIO_BASE, ignoreCase = true) -> DEFAULT_API_BASE
            trimmed.equals("$LEGACY_STUDIO_BASE/", ignoreCase = true) -> DEFAULT_API_BASE
            else -> trimmed
        }
    }

    /**
     * Official API ids are `moss-tts` / `moss-ttsd`.
     * Console/HF names like `MOSS-TTS-v1.5-Flash` are not accepted and must be mapped.
     */
    private fun normalizeModel(raw: String): String {
        val m = raw.trim()
        if (m.isBlank()) return "moss-tts"
        if (m.equals("moss-tts", ignoreCase = true)) return "moss-tts"
        if (m.equals("moss-ttsd", ignoreCase = true)) return "moss-ttsd"
        val lower = m.lowercase()
        if (lower.contains("moss") && lower.contains("tts")) {
            return if (lower.contains("ttsd") || lower.contains("speaker")) "moss-ttsd" else "moss-tts"
        }
        return m
    }

    private fun formatMosslandError(code: Int, body: String): String {
        val lower = body.lowercase()
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when {
            code == 401 || body.contains("4010") || body.contains("4011") ||
                lower.contains("invalid credentials") || lower.contains("unauthorized") ->
                "Mossland API Key 无效，请重新填写"
            code == 402 || body.contains("4020") || lower.contains("credit") || lower.contains("balance") ->
                "Mossland 余额不足，请到 studio.mosi.cn 充值"
            body.contains("4029") || lower.contains("rate") ->
                "Mossland 触发限速，请稍后重试"
            body.contains("5002") || lower.contains("voice_id is invalid") ||
                lower.contains("voice is invalid") ||
                (message.contains("voice", ignoreCase = true) && lower.contains("invalid")) ->
                "Mossland voice_id 无效或音色未激活，请确认已通过 POST /v1/audio/voices 创建"
            message.isNotBlank() -> "Mossland TTS 失败 ($code): ${message.take(180)}"
            else -> "Mossland TTS 失败 ($code): ${body.take(180)}"
        }
    }

    private fun downloadFromUrlPayload(bodyBytes: ByteArray, apiKey: String): Pair<ByteArray, String>? {
        return runCatching {
            val json = JSONObject(bodyBytes.toString(Charsets.UTF_8))
            val code = json.optInt("code", 0)
            if (code in 4000..5999) {
                throw Exception(formatMosslandError(code, json.toString()))
            }
            val url = sequenceOf(
                json.optString("url"),
                json.optString("audio_url"),
                json.optString("result_url"),
                json.optJSONObject("data")?.optString("url").orEmpty(),
                json.optJSONObject("data")?.optString("audio_url").orEmpty(),
                json.optJSONObject("result")?.optString("url").orEmpty(),
            ).map { it.trim() }.firstOrNull { it.startsWith("http") }.orEmpty()
            if (url.isBlank()) return null

            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful || bytes.isEmpty()) return null
            bytes to resp.header("Content-Type").orEmpty()
        }.getOrElse { err ->
            if (err.message?.contains("Mossland") == true) throw err
            Log.e(TAG, "downloadFromUrlPayload failed", err)
            null
        }
    }

    private fun decodeAudioPayload(bodyBytes: ByteArray, contentType: String): ByteArray {
        if (bodyBytes.isEmpty()) return bodyBytes
        if (looksLikeAudio(bodyBytes)) return bodyBytes

        val looksJson = contentType.contains("json", ignoreCase = true) ||
            bodyBytes.firstOrNull()?.toInt()?.toChar() == '{'
        if (!looksJson) return bodyBytes

        return runCatching {
            val json = JSONObject(bodyBytes.toString(Charsets.UTF_8))
            val code = json.optInt("code", 0)
            if (code in 4000..5999) {
                throw Exception(formatMosslandError(code, json.toString()))
            }
            if (json.has("error")) {
                throw Exception(formatMosslandError(0, json.toString()))
            }
            val b64 = sequenceOf(
                json.optString("audio"),
                json.optString("audio_base64"),
                json.optString("wav"),
                json.optString("data"),
                json.optString("audio_data"),
                json.optJSONObject("data")?.optString("audio").orEmpty(),
                json.optJSONObject("data")?.optString("audio_base64").orEmpty(),
                json.optJSONObject("result")?.optString("audio").orEmpty(),
            ).map { it.trim() }
                .firstOrNull { candidate ->
                    candidate.isNotBlank() &&
                        !candidate.startsWith("{") &&
                        !candidate.startsWith("http") &&
                        candidate.length > 64
                }
                .orEmpty()

            if (b64.isBlank()) {
                ByteArray(0)
            } else {
                val cleaned = b64
                    .removePrefix("data:audio/wav;base64,")
                    .removePrefix("data:audio/mpeg;base64,")
                    .removePrefix("data:audio/mp3;base64,")
                Base64.decode(cleaned, Base64.DEFAULT)
            }
        }.getOrElse { err ->
            if (err.message?.contains("Mossland") == true) throw err
            Log.e(TAG, "decodeAudioPayload failed", err)
            ByteArray(0)
        }
    }

    private fun looksLikeAudio(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val riff = bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        if (riff) return true
        if (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0) return true
        if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            return true
        }
        if (bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
        ) {
            return true
        }
        return false
    }

    private fun detectFormat(
        audioData: ByteArray,
        contentType: String,
        preferred: String,
    ): AudioFormat {
        if (audioData.size >= 4) {
            if (audioData[0] == 'R'.code.toByte() &&
                audioData[1] == 'I'.code.toByte() &&
                audioData[2] == 'F'.code.toByte() &&
                audioData[3] == 'F'.code.toByte()
            ) {
                return AudioFormat.WAV
            }
            if (audioData[0] == 0xFF.toByte() && (audioData[1].toInt() and 0xE0) == 0xE0) {
                return AudioFormat.MP3
            }
            if (audioData[0] == 'I'.code.toByte() &&
                audioData[1] == 'D'.code.toByte() &&
                audioData[2] == '3'.code.toByte()
            ) {
                return AudioFormat.MP3
            }
            if (audioData[0] == 'O'.code.toByte() && audioData[1] == 'g'.code.toByte()) {
                return AudioFormat.OPUS
            }
        }
        return when {
            contentType.contains("wav", ignoreCase = true) -> AudioFormat.WAV
            contentType.contains("mpeg", ignoreCase = true) ||
                contentType.contains("mp3", ignoreCase = true) -> AudioFormat.MP3
            contentType.contains("ogg", ignoreCase = true) ||
                contentType.contains("opus", ignoreCase = true) -> AudioFormat.OPUS
            preferred.equals("mp3", ignoreCase = true) -> AudioFormat.MP3
            preferred.equals("pcm", ignoreCase = true) -> AudioFormat.PCM
            else -> AudioFormat.MP3
        }
    }
}
