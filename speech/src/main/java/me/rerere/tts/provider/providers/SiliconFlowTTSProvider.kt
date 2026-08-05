package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "SiliconFlowTTS"

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * 硅基流动 TTS：`POST {baseUrl}/audio/speech`，返回音频二进制。
 *
 * 文档: https://docs.siliconflow.com/cn/api-reference/audio/create-speech
 */
class SiliconFlowTTSProvider : TTSProvider<TTSProviderSetting.SiliconFlow> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.SiliconFlow,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", providerSetting.responseFormat)
            put("speed", providerSetting.speed.toDouble())
            put("gain", providerSetting.gain.toDouble())
        }

        Log.i(
            TAG,
            "generateSpeech: model=${providerSetting.model} voice=${providerSetting.voice} format=${providerSetting.responseFormat}",
        )

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl.trimEnd('/')}/audio/speech")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = runCatching { response.body.string() }.getOrNull().orEmpty()
            throw Exception(
                "SiliconFlow TTS failed: HTTP ${response.code} ${response.message}. body=$errorBody",
            )
        }

        val audioBytes = response.body.bytes()
        if (audioBytes.isEmpty()) {
            throw Exception("SiliconFlow TTS returned 0 bytes")
        }

        val audioFormat = when (providerSetting.responseFormat.lowercase()) {
            "mp3" -> AudioFormat.MP3
            "wav" -> AudioFormat.WAV
            "pcm" -> AudioFormat.PCM
            "opus" -> AudioFormat.OPUS
            else -> AudioFormat.MP3
        }

        emit(
            AudioChunk(
                data = audioBytes,
                format = audioFormat,
                isLast = true,
                metadata = mapOf(
                    "provider" to "siliconflow",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice,
                ),
            ),
        )
    }
}
