package me.rerere.asr.providers

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import me.rerere.asr.SpeechRecognitionSupport
import me.rerere.asr.appendAmplitude
import java.util.Locale

private const val TAG = "SystemASR"

/**
 * 基于 Android [SpeechRecognizer] 的本地/系统 ASR。
 *
 * 无需 API Key；识别能力取决于设备是否提供 RecognitionService。
 * 会依次尝试：默认引擎 → 端上引擎(API31+) → 已声明的 RecognitionService 组件。
 */
class SystemASRController(
    private val context: Context,
    private val provider: ASRProviderSetting.System,
) : ASRController {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognitionAvailable = SpeechRecognitionSupport.isAvailable(context)
    private val _state = MutableStateFlow(ASRState(isAvailable = recognitionAvailable))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private var latestTranscript: String = ""
    private var disposed = false
    /** 当前识别器绑定的组件；null 表示默认系统引擎。 */
    private var boundComponent: ComponentName? = null
    private var finalizeWatchdog: Runnable? = null

    init {
        Log.i(TAG, SpeechRecognitionSupport.describeAvailability(context))
    }

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
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(normalized)) }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.update { it.copy(status = ASRStatus.Stopping) }
            // 部分国产机 onEndOfSpeech 后永不回调 onResults，卡住 Stopping
            scheduleFinalizeIfStuck("end-of-speech")
        }

        override fun onError(error: Int) {
            cancelFinalizeWatchdog()
            // 用户主动 stop / 取消时常见 CLIENT / NO_MATCH，不当作硬错误
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
            Log.w(TAG, "Recognition error $error: $message (component=$boundComponent)")
            setError(message)
        }

        override fun onResults(results: Bundle?) {
            cancelFinalizeWatchdog()
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
                // 有部分结果后长时间不结束 → 主动收尾，避免通话页卡在 Listening
                scheduleFinalizeIfStuck("partial")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        cancelFinalizeWatchdog()
        if (!hasMicPermission()) {
            setError("需要麦克风权限才能进行语音识别")
            return
        }
        if (!SpeechRecognitionSupport.isAvailable(context)) {
            setError(SpeechRecognitionSupport.UNAVAILABLE_MESSAGE)
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
            if (!ensureRecognizer()) {
                setError(SpeechRecognitionSupport.UNAVAILABLE_MESSAGE)
                return@runOnMain
            }
            val recognizer = speechRecognizer
            if (recognizer == null) {
                setError(SpeechRecognitionSupport.UNAVAILABLE_MESSAGE)
                return@runOnMain
            }
            try {
                recognizer.startListening(buildIntent())
            } catch (e: Exception) {
                Log.e(TAG, "startListening failed", e)
                // 当前组件失败时，尝试下一个 RecognitionService
                if (tryNextRecognizerAndStart()) {
                    return@runOnMain
                }
                setError(e.message ?: SpeechRecognitionSupport.UNAVAILABLE_MESSAGE)
            }
        }
    }

    override fun stop() {
        if (!state.value.isRecording) return
        _state.update { it.copy(status = ASRStatus.Stopping) }
        scheduleFinalizeIfStuck("stop", delayMs = 2_500L)
        runOnMain {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "stopListening failed", e)
                cancelFinalizeWatchdog()
                _state.update {
                    it.copy(
                        status = ASRStatus.Idle,
                        transcript = latestTranscript,
                    )
                }
            }
        }
    }

    override fun dispose() {
        disposed = true
        onTranscriptChange = null
        cancelFinalizeWatchdog()
        runOnMain {
            destroyRecognizer()
        }
        _state.update {
            ASRState(isAvailable = SpeechRecognitionSupport.isAvailable(context))
        }
    }

    private fun scheduleFinalizeIfStuck(reason: String, delayMs: Long = 3_000L) {
        cancelFinalizeWatchdog()
        val task = Runnable {
            if (disposed) return@Runnable
            val status = _state.value.status
            if (status == ASRStatus.Listening || status == ASRStatus.Stopping || status == ASRStatus.Connecting) {
                Log.w(
                    TAG,
                    "Force finalize after $reason (status=$status transcript='$latestTranscript')"
                )
                try {
                    speechRecognizer?.stopListening()
                } catch (_: Exception) {
                }
                _state.update {
                    it.copy(
                        status = ASRStatus.Idle,
                        transcript = latestTranscript,
                        errorMessage = null,
                    )
                }
            }
        }
        finalizeWatchdog = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun cancelFinalizeWatchdog() {
        finalizeWatchdog?.let { mainHandler.removeCallbacks(it) }
        finalizeWatchdog = null
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 按优先级创建识别器：默认 → 端上 → 各 RecognitionService。
     * @return 是否创建成功
     */
    private fun ensureRecognizer(): Boolean {
        if (speechRecognizer != null) return true

        val candidates = buildCandidateFactories()
        for ((label, factory) in candidates) {
            try {
                val recognizer = factory() ?: continue
                recognizer.setRecognitionListener(recognitionListener)
                speechRecognizer = recognizer
                Log.i(TAG, "Using recognizer: $label")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create recognizer ($label)", e)
            }
        }
        return false
    }

    private fun tryNextRecognizerAndStart(): Boolean {
        destroyRecognizer()
        val remaining = buildCandidateFactories()
            .dropWhile { it.first == "default" || it.first.startsWith("on-device") }
        for ((label, factory) in remaining) {
            try {
                val recognizer = factory() ?: continue
                recognizer.setRecognitionListener(recognitionListener)
                speechRecognizer = recognizer
                Log.i(TAG, "Fallback recognizer: $label")
                recognizer.startListening(buildIntent())
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Fallback recognizer failed ($label)", e)
                destroyRecognizer()
            }
        }
        return false
    }

    private fun buildCandidateFactories(): List<Pair<String, () -> SpeechRecognizer?>> {
        val list = mutableListOf<Pair<String, () -> SpeechRecognizer?>>()

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            list += "default" to {
                boundComponent = null
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            list += "on-device" to {
                boundComponent = null
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }
        }

        SpeechRecognitionSupport.serviceComponentNames(context).forEach { component ->
            list += "component:${component.flattenToShortString()}" to {
                boundComponent = component
                SpeechRecognizer.createSpeechRecognizer(context, component)
            }
        }

        return list
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroy recognizer failed", e)
        } finally {
            speechRecognizer = null
            boundComponent = null
        }
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
            // 仅在用户明确要求离线时开启；否则很多机型会因无离线包直接失败
            if (provider.preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            val language = provider.language.trim()
            if (language.isNotEmpty() && !language.equals("auto", ignoreCase = true)) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            } else {
                val locale = Locale.getDefault().toLanguageTag()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            }

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
                isAvailable = SpeechRecognitionSupport.isAvailable(context),
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
        SpeechRecognizer.ERROR_AUDIO -> "录音出错，请检查麦克风是否被占用"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
            // 系统常在引擎本身不可用时误报此错误；已授权麦克风时换文案
            if (hasMicPermission()) {
                SpeechRecognitionSupport.ENGINE_PERMISSION_MESSAGE
            } else {
                "需要麦克风权限才能进行语音识别"
            }
        }
        SpeechRecognizer.ERROR_NETWORK -> "语音识别网络错误"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "未识别到有效语音"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别引擎正忙，请稍后重试"
        SpeechRecognizer.ERROR_SERVER -> "语音识别服务端错误"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到说话"
        // API 31+: LANGUAGE_NOT_SUPPORTED=12, LANGUAGE_UNAVAILABLE=13,
        // SERVER_DISCONNECTED=11, TOO_MANY_REQUESTS=10
        12 -> "当前语言不被系统语音引擎支持"
        13 -> "系统未安装该语言的语音识别包"
        11 -> "语音识别服务已断开"
        10 -> "语音识别请求过于频繁"
        else -> "语音识别失败（错误码 $error）"
    }
}
