package me.rerere.rikkahub.ui.pages.voicecall

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRStatus
import me.rerere.asr.SpeechRecognitionSupport
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.VoiceCallTtsResolveResult
import me.rerere.rikkahub.data.model.resolveVoiceCallDisplay
import me.rerere.rikkahub.data.model.resolveVoiceCallTts
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.hooks.CustomAsrState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallSession"

/** 转写稳定 + 无有效语音，持续这么久 → 自动提交。 */
private const val STABLE_TRANSCRIPT_AUTO_SUBMIT_MS = 2_000L
/** Listening 总时长过长且已有文字 → 强制提交。 */
private const val LISTENING_WITH_TEXT_FORCE_MS = 20_000L
/** Listening 总时长过长且无文字 → 重启聆听。 */
private const val LISTENING_EMPTY_RESTART_MS = 35_000L
/** 判定 UI 侧仍有语音能量的振幅阈值。 */
private const val UI_SPEECH_RMS = 0.08f

enum class VoiceCallPhase {
    Idle,
    Listening,
    Thinking,
    Speaking,
    NeedsSetup,
    Error,
    Ended,
}

data class VoiceCallUiState(
    val phase: VoiceCallPhase = VoiceCallPhase.Idle,
    val partialTranscript: String = "",
    val lastUserText: String = "",
    val lastAssistantText: String = "",
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val amplitudes: List<Float> = emptyList(),
)

/**
 * Half-duplex voice-call loop: Listen → Send → Think → Speak → Listen.
 * Driven by [ChatService] so the loop survives VoiceCallPage dispose / minimize.
 */
class VoiceCallSession(
    private val scope: CoroutineScope,
    private val chatService: ChatService,
    private val settingsStore: SettingsStore,
    private val asr: CustomAsrState,
    private val tts: CustomTtsState,
    private val conversationId: Uuid,
    private val appContext: android.content.Context,
) {
    private val _ui = MutableStateFlow(VoiceCallUiState())
    val ui: StateFlow<VoiceCallUiState> = _ui.asStateFlow()

    private var generationJob: Job? = null
    private var speakWatchJob: Job? = null
    private var asrCollectJob: Job? = null
    private var watchdogJob: Job? = null
    private var diagJob: Job? = null
    private var started = false
    private var awaitingGeneration = false
    private var latestTranscript: String = ""
    private var sawRecording = false
    private var submitting = false
    private var listeningStartedAt = 0L
    private var lastTranscriptChangeAt = 0L
    private var lastStableAutoSubmitAt = 0L
    private var lastLoudAt = 0L

    fun start() {
        if (started) return
        started = true
        VoiceCallGate.active = true
        VoiceCallDiag.clear()
        VoiceCallDiag.log(TAG, "session start conversation=$conversationId")

        chatService.addConversationReference(conversationId)
        scope.launch {
            chatService.initializeConversation(conversationId)
        }

        asrCollectJob = scope.launch {
            var lastLoggedKey = ""
            asr.state.collect { asrState ->
                val peak = asrState.amplitudes.maxOrNull() ?: 0f
                if (peak >= UI_SPEECH_RMS) {
                    lastLoudAt = System.currentTimeMillis()
                }
                _ui.update {
                    it.copy(
                        amplitudes = asrState.amplitudes,
                        partialTranscript = if (it.phase == VoiceCallPhase.Listening) {
                            asrState.transcript.ifBlank { latestTranscript }
                        } else {
                            it.partialTranscript
                        },
                    )
                }

                val key = "${asrState.status}|${asrState.transcript}|${asrState.errorMessage}"
                if (key != lastLoggedKey) {
                    lastLoggedKey = key
                    VoiceCallDiag.log(
                        "ASR",
                        "status=${asrState.status} rec=${asrState.isRecording} " +
                            "text='${asrState.transcript.take(40)}' err=${asrState.errorMessage}"
                    )
                }

                if (_ui.value.phase != VoiceCallPhase.Listening) return@collect

                if (asrState.isRecording) {
                    sawRecording = true
                } else if (sawRecording &&
                    (asrState.status == ASRStatus.Idle || asrState.status == ASRStatus.Error)
                ) {
                    sawRecording = false
                    val text = latestTranscript.ifBlank { asrState.transcript }.trim()
                    VoiceCallDiag.log(TAG, "asr ended → text='$text' status=${asrState.status}")
                    if (text.isNotBlank()) {
                        submitUserText(text)
                    } else {
                        if (asrState.status == ASRStatus.Error) {
                            val err = asrState.errorMessage?.takeIf { it.isNotBlank() }
                                ?: "语音识别失败，正在重试…"
                            Log.w(TAG, "ASR error while listening: $err")
                            if (SpeechRecognitionSupport.isHardFailure(err)) {
                                asr.stop()
                                setPhase(VoiceCallPhase.NeedsSetup, err)
                                _ui.update { it.copy(errorMessage = err) }
                                return@collect
                            }
                            setPhase(VoiceCallPhase.Listening, err)
                            delay(800)
                        } else {
                            delay(300)
                        }
                        if (_ui.value.phase == VoiceCallPhase.Listening && !asr.state.value.isRecording) {
                            beginListening(restart = true)
                        }
                    }
                }
            }
        }

        generationJob = scope.launch {
            chatService.generationDoneFlow.collect { doneId ->
                if (doneId != conversationId || !awaitingGeneration) return@collect
                awaitingGeneration = false
                submitting = false
                val reply = extractAssistantReply(chatService.getConversationFlow(conversationId).value)
                VoiceCallDiag.log(TAG, "generationDone replyLen=${reply.length}")
                if (reply.isBlank()) {
                    setPhase(VoiceCallPhase.Listening, "回复失败，请再说一次")
                    beginListening(restart = true)
                    return@collect
                }
                speakReply(reply)
            }
        }

        startWatchdog()
        startDiagTicker()
        applyVoiceAndListen()
    }

    fun applyVoiceAndListen() {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        when (val resolved = resolveVoiceCallTts(settings, assistant.voiceCall)) {
            is VoiceCallTtsResolveResult.Ready -> {
                val asrProvider = settings.getSelectedASRProvider()
                if (asrProvider is ASRProviderSetting.System && !asr.state.value.isAvailable) {
                    val msg = SpeechRecognitionSupport.UNAVAILABLE_MESSAGE
                    setPhase(VoiceCallPhase.NeedsSetup, msg)
                    _ui.update { it.copy(errorMessage = msg) }
                    VoiceCallDiag.log(TAG, "system asr unavailable")
                    return
                }
                tts.setOverrideProvider(resolved.provider)
                setPhase(VoiceCallPhase.Listening, "正在聆听…")
                beginListening()
            }
            is VoiceCallTtsResolveResult.NeedsApiKey -> {
                setPhase(
                    VoiceCallPhase.NeedsSetup,
                    "请先为「${resolved.providerLabel}」填入 API Key",
                )
            }
            is VoiceCallTtsResolveResult.Unavailable -> {
                setPhase(VoiceCallPhase.Error, resolved.message)
                _ui.update { it.copy(errorMessage = resolved.message) }
            }
        }
    }

    fun previewVoice(settings: Settings) {
        val assistant = settings.getCurrentAssistant()
        val display = resolveVoiceCallDisplay(settings, assistant.voiceCall)
        when (val resolved = resolveVoiceCallTts(settings, assistant.voiceCall)) {
            is VoiceCallTtsResolveResult.Ready -> {
                tts.setOverrideProvider(resolved.provider)
                tts.speak(display.sampleText)
            }
            else -> Unit
        }
    }

    /** User taps mic while listening → finalize utterance and send. */
    fun finishUtterance() {
        if (_ui.value.phase != VoiceCallPhase.Listening) return
        VoiceCallDiag.log(TAG, "finishUtterance manual/auto")
        asr.stop()
        scope.launch {
            delay(350)
            val text = latestTranscript
                .ifBlank { asr.state.value.transcript }
                .ifBlank { _ui.value.partialTranscript }
                .trim()
            submitUserText(text)
        }
    }

    /** Barge-in while speaking / thinking. */
    fun interrupt() {
        when (_ui.value.phase) {
            VoiceCallPhase.Speaking, VoiceCallPhase.Thinking -> {
                awaitingGeneration = false
                submitting = false
                speakWatchJob?.cancel()
                tts.stop()
                scope.launch { chatService.stopGeneration(conversationId) }
                setPhase(VoiceCallPhase.Listening, "已打断，正在聆听…")
                beginListening(restart = true)
            }
            VoiceCallPhase.Listening -> finishUtterance()
            else -> Unit
        }
    }

    fun hangUp() {
        started = false
        awaitingGeneration = false
        submitting = false
        generationJob?.cancel()
        speakWatchJob?.cancel()
        asrCollectJob?.cancel()
        watchdogJob?.cancel()
        diagJob?.cancel()
        asr.stop()
        tts.stop()
        tts.clearOverride()
        VoiceCallGate.active = false
        chatService.removeConversationReference(conversationId)
        setPhase(VoiceCallPhase.Ended, "通话已结束")
        VoiceCallDiag.log(TAG, "hangUp")
    }

    private fun beginListening(restart: Boolean = false) {
        if (!started) return
        scope.launch {
            latestTranscript = ""
            lastTranscriptChangeAt = 0L
            lastLoudAt = 0L
            listeningStartedAt = System.currentTimeMillis()
            sawRecording = asr.state.value.isRecording
            submitting = false
            _ui.update { it.copy(partialTranscript = "", amplitudes = emptyList()) }
            VoiceCallDiag.log(TAG, "beginListening restart=$restart asr=${asr.state.value.status}")
            if (!restart && asr.state.value.status == ASRStatus.Listening) {
                return@launch
            }
            if (asr.state.value.isRecording) {
                asr.stop()
                withTimeoutOrNull(2_000) {
                    asr.state.first { !it.isRecording }
                }
                delay(120)
            }
            if (!started || _ui.value.phase == VoiceCallPhase.Ended) return@launch
            try {
                asr.start { transcript ->
                    if (transcript != latestTranscript) {
                        lastTranscriptChangeAt = System.currentTimeMillis()
                    }
                    latestTranscript = transcript
                    _ui.update {
                        if (it.phase == VoiceCallPhase.Listening) {
                            it.copy(partialTranscript = transcript)
                        } else {
                            it
                        }
                    }
                }
                val immediate = asr.state.value
                if (immediate.status == ASRStatus.Error) {
                    val err = immediate.errorMessage?.takeIf { it.isNotBlank() }
                        ?: "无法启动语音识别"
                    if (SpeechRecognitionSupport.isHardFailure(err)) {
                        setPhase(VoiceCallPhase.NeedsSetup, err)
                        _ui.update { it.copy(errorMessage = err) }
                    } else {
                        setPhase(VoiceCallPhase.Error, err)
                        _ui.update { it.copy(errorMessage = err) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ASR start failed", e)
                VoiceCallDiag.log(TAG, "ASR start exception: ${e.message}")
                val msg = e.message ?: "无法启动语音识别"
                val phase = if (SpeechRecognitionSupport.isHardFailure(msg)) {
                    VoiceCallPhase.NeedsSetup
                } else {
                    VoiceCallPhase.Error
                }
                setPhase(phase, msg)
                _ui.update { it.copy(errorMessage = msg) }
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive && started) {
                delay(400)
                if (_ui.value.phase != VoiceCallPhase.Listening || submitting) continue
                val now = System.currentTimeMillis()
                val text = latestTranscript
                    .ifBlank { asr.state.value.transcript }
                    .ifBlank { _ui.value.partialTranscript }
                    .trim()
                val listeningFor = if (listeningStartedAt > 0) now - listeningStartedAt else 0L
                val stableFor = if (lastTranscriptChangeAt > 0 && text.isNotBlank()) {
                    now - lastTranscriptChangeAt
                } else {
                    0L
                }

                val quietFor = if (lastLoudAt > 0) now - lastLoudAt else listeningFor
                val readyToCommit = text.isNotBlank() &&
                    stableFor >= STABLE_TRANSCRIPT_AUTO_SUBMIT_MS &&
                    quietFor >= STABLE_TRANSCRIPT_AUTO_SUBMIT_MS

                val stall = when {
                    readyToCommit ->
                        "转写稳定且静音 ${stableFor.coerceAtMost(quietFor)}ms，自动提交"
                    text.isNotBlank() && listeningFor >= LISTENING_WITH_TEXT_FORCE_MS ->
                        "有转写但聆听超过 ${listeningFor}ms，强制提交"
                    text.isBlank() && listeningFor >= LISTENING_EMPTY_RESTART_MS ->
                        "无转写聆听超过 ${listeningFor}ms，重启"
                    else -> ""
                }
                if (stall.isNotBlank()) {
                    VoiceCallDiag.updateLive { it.copy(stallHint = stall) }
                }

                // 必须：有转写 + 文案不再变 + 约 2s 无有效语音，才认定说完
                if (readyToCommit && now - lastStableAutoSubmitAt > 1_000L) {
                    lastStableAutoSubmitAt = now
                    VoiceCallDiag.log(
                        TAG,
                        "watchdog auto-submit stable+quiet text='$text' stable=${stableFor}ms quiet=${quietFor}ms"
                    )
                    finishUtterance()
                    continue
                }
                if (text.isNotBlank() && listeningFor >= LISTENING_WITH_TEXT_FORCE_MS) {
                    VoiceCallDiag.log(TAG, "watchdog force-submit text='$text'")
                    finishUtterance()
                    continue
                }
                if (text.isBlank() && listeningFor >= LISTENING_EMPTY_RESTART_MS) {
                    VoiceCallDiag.log(TAG, "watchdog relisten empty")
                    beginListening(restart = true)
                }
            }
        }
    }

    private fun startDiagTicker() {
        if (!VoiceCallDiag.ENABLED) return
        diagJob?.cancel()
        diagJob = scope.launch {
            while (isActive && started) {
                publishDiag()
                delay(500)
            }
        }
    }

    private fun publishDiag() {
        val asrState = asr.state.value
        val provider = asr.activeProvider ?: settingsStore.settingsFlow.value.getSelectedASRProvider()
        val providerLabel = when (provider) {
            is ASRProviderSetting.System -> "System"
            is ASRProviderSetting.OpenAIRealtime -> "OpenAIRealtime"
            is ASRProviderSetting.DashScope -> "DashScope"
            is ASRProviderSetting.Volcengine -> "Volcengine"
            is ASRProviderSetting.MiMo -> "MiMo(seg=${provider.segmentDurationSec}s)"
            is ASRProviderSetting.Step -> "Step(seg=${provider.segmentDurationSec}s)"
            is ASRProviderSetting.SiliconFlow -> "SiliconFlow(seg=${provider.segmentDurationSec}s,silence=2s)"
            null -> "null"
        }
        val micGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val ui = _ui.value
        VoiceCallDiag.updateLive {
            it.copy(
                phase = ui.phase.name,
                statusMessage = ui.statusMessage,
                errorMessage = ui.errorMessage.orEmpty(),
                partialTranscript = ui.partialTranscript,
                latestTranscript = latestTranscript,
                lastUserText = ui.lastUserText,
                asrStatus = asrState.status.name,
                asrRecording = asrState.isRecording,
                asrAvailable = asrState.isAvailable,
                asrError = asrState.errorMessage.orEmpty(),
                asrProvider = providerLabel,
                micGranted = micGranted,
                recognitionAvailable = SpeechRecognitionSupport.isAvailable(appContext),
                recognitionDetail = SpeechRecognitionSupport.describeAvailability(appContext),
                sawRecording = sawRecording,
                submitting = submitting,
                awaitingGeneration = awaitingGeneration,
            )
        }
    }

    private fun submitUserText(text: String) {
        if (submitting) return
        if (_ui.value.phase != VoiceCallPhase.Listening &&
            _ui.value.phase != VoiceCallPhase.Idle
        ) {
            if (_ui.value.phase == VoiceCallPhase.Thinking ||
                _ui.value.phase == VoiceCallPhase.Speaking
            ) {
                return
            }
        }
        if (text.isBlank()) {
            VoiceCallDiag.log(TAG, "submit blank → relisten")
            setPhase(VoiceCallPhase.Listening, "没听清，请再说一次")
            beginListening(restart = true)
            return
        }

        submitting = true
        asr.stop()
        _ui.update {
            it.copy(
                lastUserText = text,
                partialTranscript = text,
            )
        }
        setPhase(VoiceCallPhase.Thinking, "正在思考…")
        awaitingGeneration = true
        VoiceCallDiag.log(TAG, "submitUserText '$text'")
        chatService.sendMessage(conversationId, listOf(UIMessagePart.Text(text)))
    }

    private fun speakReply(reply: String) {
        val settings = settingsStore.settingsFlow.value
        var textToSpeak = reply
        if (settings.displaySetting.ttsOnlyReadQuoted) {
            textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
        }
        if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
            textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
        }
        if (textToSpeak.isBlank()) {
            setPhase(VoiceCallPhase.Listening, "正在聆听…")
            beginListening(restart = true)
            return
        }

        submitting = false
        _ui.update { it.copy(lastAssistantText = textToSpeak) }
        setPhase(VoiceCallPhase.Speaking, "正在说话…")
        VoiceCallDiag.log(TAG, "speakReply len=${textToSpeak.length}")

        speakWatchJob?.cancel()
        speakWatchJob = scope.launch {
            asr.stop()
            delay(80)
            tts.speak(textToSpeak)
            withTimeoutOrNull(15_000) {
                tts.isSpeaking.first { it }
            }
            withTimeoutOrNull(180_000) {
                tts.isSpeaking.first { !it }
            }
            if (_ui.value.phase == VoiceCallPhase.Speaking) {
                setPhase(VoiceCallPhase.Listening, "正在聆听…")
                beginListening(restart = true)
            }
        }
    }

    private fun extractAssistantReply(conversation: Conversation): String {
        val last = conversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
        return last?.toText()?.trim().orEmpty()
    }

    private fun setPhase(phase: VoiceCallPhase, message: String) {
        VoiceCallDiag.log(TAG, "phase=$phase msg=$message")
        _ui.update {
            it.copy(
                phase = phase,
                statusMessage = message,
                errorMessage = if (phase == VoiceCallPhase.Error) message else it.errorMessage,
            )
        }
    }
}
