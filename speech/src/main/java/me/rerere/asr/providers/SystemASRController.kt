package me.rerere.asr.providers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import java.util.Locale

private const val TAG = "SystemASR"

/**
 * 基于 Android [SpeechRecognizer] 的本地/系统 ASR。
 *
 * 无需 API Key；识别能力取决于设备是否提供 RecognitionService，
 * 以及是否安装了对应语言的离线语音包（[ASRProviderSetting.System.preferOffline]）。
 */
class SystemASRController(
    private val context: Context,
    private val provider: ASRProviderSetting.System,
) : ASRController {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
    private val _state = MutableStateFlow(ASRState(isAvailable = recognitionAvailable))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private var latestTranscript: String = ""
    private var disposed = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.update {
                it.copy(
                    status = ASRStatus.Listening,
                    errorMessage = null,
                    amplitudes = emptyList(),
                )
            }
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // SpeechRecognizer 给的是大约 -2..10 的 dB，映射到 0..1 用于波形
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(normalized)) }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.update { it.copy(status = ASRStatus.Stopping) }
        }

        override fun onError(error: Int) {
            // 用户主动 stop / 取消时常见 CLIENT / CANCELED，不当作硬错误
            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                _state.update {
                    it.copy(
                        status = ASRStatus.Idle,
                        transcript = latestTranscript,
                        errorMessage = null,
                    )
                }
                return
            }
            val message = errorMessage(error)
            Log.w(TAG, "Recognition error $error: $message")
            setError(message)
        }

        override fun onResults(results: Bundle?) {
            val text = extractBestResult(results)
            if (text.isNotBlank()) {
                latestTranscript = text
                onTranscriptChange?.invoke(text)
            }
            _state.update {
                it.copy(
                    status = ASRStatus.Idle,
                    transcript = latestTranscript,
                    errorMessage = null,
                )
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = extractBestResult(partialResults)
            if (text.isNotBlank()) {
                latestTranscript = text
                onTranscriptChange?.invoke(text)
                _state.update { it.copy(transcript = text) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }
        if (!recognitionAvailable) {
            setError("Speech recognition is not available on this device")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        latestTranscript = ""
        _state.update {
            ASRState(
                status = ASRStatus.Connecting,
                isAvailable = true,
            )
        }

        runOnMain {
            if (disposed) return@runOnMain
            ensureRecognizer()
            val recognizer = speechRecognizer
            if (recognizer == null) {
                setError("Failed to create SpeechRecognizer")
                return@runOnMain
            }
            try {
                recognizer.startListening(buildIntent())
            } catch (e: Exception) {
                Log.e(TAG, "startListening failed", e)
                setError(e.message ?: "Failed to start speech recognition")
            }
        }
    }

    override fun stop() {
        if (!state.value.isRecording) return
        _state.update { it.copy(status = ASRStatus.Stopping) }
        runOnMain {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "stopListening failed", e)
                _state.update { it.copy(status = ASRStatus.Idle) }
            }
        }
    }

    override fun dispose() {
        disposed = true
        onTranscriptChange = null
        runOnMain {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "dispose recognizer failed", e)
            } finally {
                speechRecognizer = null
            }
        }
        _state.update { ASRState(isAvailable = recognitionAvailable) }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(recognitionListener)
        speechRecognizer = recognizer
    }

    private fun buildIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                provider.maxResults.coerceIn(1, 5)
            )
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, provider.preferOffline)

            val language = provider.language.trim()
            if (language.isNotEmpty() && !language.equals("auto", ignoreCase = true)) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            } else {
                val locale = Locale.getDefault().toLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            }

            // 拉长端点静音阈值，方便用户说稍长一点的句子
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }
    }

    private fun extractBestResult(bundle: Bundle?): String {
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return results?.firstOrNull().orEmpty().trim()
    }

    private fun setError(message: String) {
        _state.update {
            ASRState(
                status = ASRStatus.Error,
                isAvailable = recognitionAvailable,
                transcript = latestTranscript,
                errorMessage = message,
            )
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during speech recognition"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
        // API 31+: LANGUAGE_NOT_SUPPORTED=12, LANGUAGE_UNAVAILABLE=13,
        // SERVER_DISCONNECTED=11, TOO_MANY_REQUESTS=10
        12 -> "Language is not supported"
        13 -> "Language is unavailable"
        11 -> "Speech recognition server disconnected"
        10 -> "Too many speech recognition requests"
        else -> "Speech recognition failed ($error)"
    }
}
