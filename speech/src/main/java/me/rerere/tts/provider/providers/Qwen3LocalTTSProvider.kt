package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "Qwen3LocalTTSProvider"

/**
 * Client for RikkaHub LAN Qwen3-TTS protocol (tools/qwen3-tts-lan).
 *
 * POST {baseUrl}/v1/tts/speech → WAV / PCM audio bytes.
 */
class Qwen3LocalTTSProvider : TTSProvider<TTSProviderSetting.Qwen3Local> {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Qwen3Local,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val base = providerSetting.baseUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "Qwen3 LAN TTS baseUrl is empty" }
        require(base.toHttpUrlOrNull() != null) { "Invalid Qwen3 LAN TTS URL: $base" }

        val body = JSONObject().apply {
            put("input", request.text)
            put("mode", providerSetting.mode.ifBlank { "custom_voice" })
            put("speaker", providerSetting.speaker.ifBlank { "Vivian" })
            put("language", providerSetting.language.ifBlank { "Auto" })
            put("instruct", providerSetting.instruct)
            put("voice_description", providerSetting.voiceDescription)
            put("response_format", providerSetting.responseFormat.ifBlank { "wav" })
            put("speed", providerSetting.speed.toDouble())
        }

        Log.i(TAG, "generateSpeech url=$base/v1/tts/speech body=$body")

        val httpRequest = Request.Builder()
            .url("$base/v1/tts/speech")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "audio/*, application/octet-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty().take(300)
                Log.e(TAG, "LAN TTS failed: ${resp.code} $err")
                throw Exception("Qwen3 LAN TTS failed: ${resp.code} ${resp.message}${if (err.isNotBlank()) " — $err" else ""}")
            }

            val audioData = resp.body?.bytes() ?: ByteArray(0)
            if (audioData.isEmpty()) {
                throw Exception("Qwen3 LAN TTS returned empty audio")
            }

            val sampleRate = resp.header("X-Sample-Rate")?.toIntOrNull() ?: 24000
            val formatHeader = resp.header("X-Response-Format")
                ?: resp.header("Content-Type").orEmpty()
            val format = when {
                formatHeader.contains("pcm", ignoreCase = true) ||
                    formatHeader.contains("L16", ignoreCase = true) -> AudioFormat.PCM
                formatHeader.contains("wav", ignoreCase = true) -> AudioFormat.WAV
                formatHeader.contains("mp3", ignoreCase = true) -> AudioFormat.MP3
                providerSetting.responseFormat.equals("pcm", ignoreCase = true) -> AudioFormat.PCM
                else -> AudioFormat.WAV
            }

            emit(
                AudioChunk(
                    data = audioData,
                    format = format,
                    sampleRate = sampleRate,
                    isLast = true,
                    metadata = mapOf(
                        "provider" to "qwen3-local",
                        "speaker" to providerSetting.speaker,
                        "mode" to providerSetting.mode,
                    )
                )
            )
        }
    }
}
