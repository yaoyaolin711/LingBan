package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.uuid.Uuid

private const val TAG = "VolcengineTTS"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * 火山引擎豆包语音合成 HTTP v1（operation=query，一次性返回 base64 音频）。
 *
 * POST https://openspeech.bytedance.com/api/v1/tts
 * Authorization: Bearer;{accessToken}
 */
class VolcengineTTSProvider : TTSProvider<TTSProviderSetting.Volcengine> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Volcengine,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        require(providerSetting.appId.isNotBlank()) { "Volcengine TTS appId is blank" }
        require(providerSetting.accessToken.isNotBlank()) { "Volcengine TTS accessToken is blank" }
        require(providerSetting.voiceType.isNotBlank()) { "Volcengine TTS voiceType is blank" }

        val requestBody = buildJsonObject {
            put("app", buildJsonObject {
                put("appid", providerSetting.appId)
                put("token", providerSetting.accessToken)
                put("cluster", providerSetting.cluster.ifBlank { "volcano_tts" })
            })
            put("user", buildJsonObject {
                put("uid", "solace")
            })
            put("audio", buildJsonObject {
                put("voice_type", providerSetting.voiceType)
                put("encoding", providerSetting.encoding.ifBlank { "mp3" })
                put("rate", providerSetting.sampleRate)
                put("speed_ratio", providerSetting.speedRatio.toDouble())
            })
            put("request", buildJsonObject {
                put("reqid", Uuid.random().toString())
                put("text", request.text)
                put("text_type", "plain")
                put("operation", "query")
            })
        }

        Log.i(
            TAG,
            "generateSpeech: voice=${providerSetting.voiceType} cluster=${providerSetting.cluster} encoding=${providerSetting.encoding}",
        )

        val httpRequest = Request.Builder()
            .url(providerSetting.baseUrl.trim())
            .addHeader("Authorization", "Bearer;${providerSetting.accessToken}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        val bodyText = response.body.string()
        if (!response.isSuccessful) {
            throw Exception(
                "Volcengine TTS failed: HTTP ${response.code} ${response.message}. body=$bodyText",
            )
        }

        val root = json.parseToJsonElement(bodyText).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 3000) {
            val message = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            throw Exception("Volcengine TTS error code=$code message=$message")
        }

        val dataB64 = root["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (dataB64.isBlank()) {
            throw Exception("Volcengine TTS returned empty data. body=$bodyText")
        }

        val audioBytes = Base64.decode(dataB64, Base64.DEFAULT)
        if (audioBytes.isEmpty()) {
            throw Exception("Volcengine TTS decoded 0 audio bytes")
        }

        val audioFormat = when (providerSetting.encoding.lowercase()) {
            "mp3" -> AudioFormat.MP3
            "wav" -> AudioFormat.WAV
            "pcm" -> AudioFormat.PCM
            "ogg_opus", "opus" -> AudioFormat.OPUS
            else -> AudioFormat.MP3
        }

        emit(
            AudioChunk(
                data = audioBytes,
                format = audioFormat,
                sampleRate = providerSetting.sampleRate,
                isLast = true,
                metadata = mapOf(
                    "provider" to "volcengine",
                    "voice_type" to providerSetting.voiceType,
                ),
            )
        )
    }
}
