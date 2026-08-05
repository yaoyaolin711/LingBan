package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SiliconFlowASR"

/** 官方单文件上限 50MB；16kHz/16bit/mono 提前在约 6MB 触发 flush。 */
private const val MAX_SEGMENT_BYTES = 6 * 1024 * 1024

/** 判定为「在说话」的 RMS 阈值（0..1）。略提高，减少气口/环境噪声被当成说话。 */
private const val SPEECH_RMS = 0.08f
/** 说过话后，连续静音这么久 → 当作一句话说完（与通话页「稳定约 2 秒」对齐）。 */
private const val END_UTTERANCE_SILENCE_MS = 2_000L
/** 至少要有这么长的有效语音，才允许静音收尾（避免噪声误触发）。 */
private const val MIN_SPEECH_BEFORE_END_MS = 500L
/**
 * 切段上限：设置里若仍是旧默认 30s，通话会极慢；
 * 运行时钳制到该值。长句中途 flush 只更新文案，不结束 Listening。
 */
private const val MAX_EFFECTIVE_SEGMENT_SEC = 12

/**
 * 硅基流动 ASR：`POST {baseUrl}/audio/transcriptions` multipart。
 *
 * 录音期间可按时间切段预识别；**说过话后连续静音约 2s 才结束本轮**，
 * 避免气口/短停顿被当成说完而提前发送。
 */
class SiliconFlowASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.SiliconFlow,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private var flushJob: Job? = null
    private val stopping = AtomicBoolean(false)

    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()
    private var segmentStartElapsedMs = 0L
    private val completedTranscripts = Collections.synchronizedList(mutableListOf<String>())

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        stopping.set(false)
        synchronized(bufferLock) {
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
        }
        completedTranscripts.clear()
        flushJob = null

        _state.update {
            ASRState(
                status = ASRStatus.Listening,
                isAvailable = true,
            )
        }
        startRecorder()
    }

    override fun stop() {
        if (!stopping.compareAndSet(false, true)) return
        finalizeRecording(reason = "stop")
    }

    override fun dispose() {
        stopping.set(true)
        recorderJob?.cancel()
        flushJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    private fun finalizeRecording(reason: String) {
        Log.i(TAG, "finalizeRecording reason=$reason")
        recorderJob?.cancel()
        releaseRecorder()
        _state.update { it.copy(status = ASRStatus.Stopping) }

        scope.launch(Dispatchers.IO) {
            try {
                flushJob?.join()
                flushSegment()
            } catch (e: Exception) {
                Log.e(TAG, "Final flush failed", e)
                setError(e.message ?: "SiliconFlow ASR final flush failed")
            } finally {
                stopping.set(false)
                _state.update { it.copy(status = ASRStatus.Idle) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                setError("Failed to initialize AudioRecord")
                recorder.release()
                return@launch
            }
            audioRecord = recorder

            var speechSeen = false
            var speechAccumMs = 0L
            var lastLoudElapsed = 0L
            val frameMsApprox = (bufferSize.toLong() * 1000L) / (sampleRate * 2L).coerceAtLeast(1)

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                // 旧配置常为 30s；钳制后仍靠静音收尾，切段只作上限防缓冲过大
                val configuredSec = provider.segmentDurationSec.coerceAtLeast(0)
                val effectiveSec = if (configuredSec == 0) {
                    0
                } else {
                    configuredSec.coerceAtMost(MAX_EFFECTIVE_SEGMENT_SEC)
                }
                val segmentMs = effectiveSec * 1000L
                while (isActive && !stopping.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }

                        val now = SystemClock.elapsedRealtime()
                        if (amplitude >= SPEECH_RMS) {
                            speechSeen = true
                            lastLoudElapsed = now
                            speechAccumMs += frameMsApprox
                        } else if (
                            speechSeen &&
                            speechAccumMs >= MIN_SPEECH_BEFORE_END_MS &&
                            now - lastLoudElapsed >= END_UTTERANCE_SILENCE_MS
                        ) {
                            // 说完静音 → 结束本轮，交给 VoiceCallSession 提交
                            if (stopping.compareAndSet(false, true)) {
                                finalizeRecording(reason = "silence")
                            }
                            return@launch
                        }

                        val shouldFlush = synchronized(bufferLock) {
                            currentBuffer.write(buffer, 0, read)
                            if (segmentMs <= 0) {
                                currentBuffer.size() >= MAX_SEGMENT_BYTES
                            } else {
                                val elapsed = SystemClock.elapsedRealtime() - segmentStartElapsedMs
                                currentBuffer.size() >= MAX_SEGMENT_BYTES || elapsed >= segmentMs
                            }
                        }

                        if (shouldFlush) {
                            triggerFlush()
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                if (isActive && !stopping.get()) {
                    Log.e(TAG, "Audio recording failed", e)
                    setError(e.message ?: "Audio recording failed")
                }
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun triggerFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            runCatching { flushSegment() }
                .onFailure { Log.e(TAG, "Segment flush failed", it) }
        }
    }

    private suspend fun flushSegment() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size() == 0) return
            val bytes = currentBuffer.toByteArray()
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
            bytes
        }

        val wavBytes = pcm16ToWav(
            pcm = pcmBytes,
            sampleRate = provider.sampleRate,
            channels = 1,
            bitsPerSample = 16,
        )

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", provider.model)
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url("${provider.baseUrl.trimEnd('/')}/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .post(body)
            .build()

        val text = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("SiliconFlow ASR HTTP ${resp.code}: $respBody")
                }
                val json = runCatching { JSONObject(respBody) }.getOrElse {
                    throw IOException("SiliconFlow ASR response is not valid JSON: $respBody")
                }
                json.optString("text", "").trim()
            }
        }

        if (text.isNotEmpty()) {
            completedTranscripts.add(text)
            publishTranscript()
        }
    }

    private fun publishTranscript() {
        val transcript = completedTranscripts
            .filter { it.isNotBlank() }
            .joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch { onTranscriptChange?.invoke(transcript) }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message,
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    companion object {
        private fun pcm16ToWav(
            pcm: ByteArray,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int,
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = pcm.size
            val out = ByteArrayOutputStream(44 + dataSize)

            fun writeString(s: String) = s.forEach { out.write(it.code) }
            fun writeInt(v: Int) {
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
                out.write((v shr 16) and 0xFF)
                out.write((v shr 24) and 0xFF)
            }

            fun writeShort(v: Int) {
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
            }

            writeString("RIFF")
            writeInt(36 + dataSize)
            writeString("WAVE")
            writeString("fmt ")
            writeInt(16)
            writeShort(1)
            writeShort(channels)
            writeInt(sampleRate)
            writeInt(byteRate)
            writeShort(blockAlign)
            writeShort(bitsPerSample)
            writeString("data")
            writeInt(dataSize)
            out.write(pcm)
            return out.toByteArray()
        }
    }
}
