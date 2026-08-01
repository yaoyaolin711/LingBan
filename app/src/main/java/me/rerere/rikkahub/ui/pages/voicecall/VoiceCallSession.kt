package me.rerere.rikkahub.ui.pages.voicecall

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.VoiceCallTtsResolveResult
import me.rerere.rikkahub.data.model.resolveVoiceCallDisplay
import me.rerere.rikkahub.data.model.resolveVoiceCallTts
import me.rerere.rikkahub.ui.hooks.CustomAsrState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallSession"

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
 */
class VoiceCallSession(
    private val scope: CoroutineScope,
    private val chatVM: ChatVM,
    private val asr: CustomAsrState,
    private val tts: CustomTtsState,
    private val conversationId: Uuid,
) {
    private val _ui = MutableStateFlow(VoiceCallUiState())
    val ui: StateFlow<VoiceCallUiState> = _ui.asStateFlow()

    private var generationJob: Job? = null
    private var speakWatchJob: Job? = null
    private var asrCollectJob: Job? = null
    private var started = false
    private var awaitingGeneration = false
    private var latestTranscript: String = ""
    private var sawRecording = false
    private var submitting = false

    fun start() {
        if (started) return
        started = true
        VoiceCallGate.active = true

        asrCollectJob = scope.launch {
            asr.state.collect { asrState ->
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

                if (_ui.value.phase != VoiceCallPhase.Listening) return@collect

                if (asrState.isRecording) {
                    sawRecording = true
                } else if (sawRecording &&
                    (asrState.status == ASRStatus.Idle || asrState.status == ASRStatus.Error)
                ) {
                    sawRecording = false
                    val text = latestTranscript.ifBlank { asrState.transcript }.trim()
                    if (text.isNotBlank()) {
                        submitUserText(text)
                    } else if (asrState.status != ASRStatus.Error) {
                        // Auto-ended with empty result — restart listen
                        delay(300)
                        if (_ui.value.phase == VoiceCallPhase.Listening && !asr.state.value.isRecording) {
                            beginListening(restart = true)
                        }
                    }
                }
            }
        }

        generationJob = scope.launch {
            chatVM.generationDoneFlow.collect { doneId ->
                if (doneId != conversationId || !awaitingGeneration) return@collect
                awaitingGeneration = false
                val reply = extractAssistantReply(chatVM.conversation.value)
                if (reply.isBlank()) {
                    setPhase(VoiceCallPhase.Listening, "没听清回复，请继续说")
                    beginListening()
                    return@collect
                }
                speakReply(reply)
            }
        }

        applyVoiceAndListen()
    }

    fun applyVoiceAndListen() {
        val settings = chatVM.settings.value
        val assistant = settings.getCurrentAssistant()
        when (val resolved = resolveVoiceCallTts(settings, assistant.voiceCall)) {
            is VoiceCallTtsResolveResult.Ready -> {
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
        asr.stop()
        scope.launch {
            delay(350)
            val text = latestTranscript.ifBlank { asr.state.value.transcript }.trim()
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
                chatVM.stopGeneration()
                setPhase(VoiceCallPhase.Listening, "已打断，正在聆听…")
                beginListening()
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
        asr.stop()
        tts.stop()
        tts.clearOverride()
        VoiceCallGate.active = false
        setPhase(VoiceCallPhase.Ended, "通话已结束")
    }

    private fun beginListening(restart: Boolean = false) {
        if (!started) return
        latestTranscript = ""
        sawRecording = false
        submitting = false
        _ui.update { it.copy(partialTranscript = "", amplitudes = emptyList()) }
        if (asr.state.value.isRecording && !restart) {
            return
        }
        if (asr.state.value.isRecording) {
            asr.stop()
        }
        try {
            asr.start { transcript ->
                latestTranscript = transcript
                _ui.update {
                    if (it.phase == VoiceCallPhase.Listening) {
                        it.copy(partialTranscript = transcript)
                    } else {
                        it
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ASR start failed", e)
            setPhase(VoiceCallPhase.Error, e.message ?: "无法启动语音识别")
            _ui.update { it.copy(errorMessage = e.message) }
        }
    }

    private fun submitUserText(text: String) {
        if (submitting) return
        if (_ui.value.phase != VoiceCallPhase.Listening &&
            _ui.value.phase != VoiceCallPhase.Idle
        ) {
            // Allow submit only from listening (or after auto-end still in listening transition)
            if (_ui.value.phase == VoiceCallPhase.Thinking ||
                _ui.value.phase == VoiceCallPhase.Speaking
            ) {
                return
            }
        }
        if (text.isBlank()) {
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
        chatVM.handleMessageSend(listOf(UIMessagePart.Text(text)))
    }

    private fun speakReply(reply: String) {
        val settings = chatVM.settings.value
        var textToSpeak = reply
        if (settings.displaySetting.ttsOnlyReadQuoted) {
            textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
        }
        if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
            textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
        }
        if (textToSpeak.isBlank()) {
            setPhase(VoiceCallPhase.Listening, "正在聆听…")
            beginListening()
            return
        }

        submitting = false
        _ui.update { it.copy(lastAssistantText = textToSpeak) }
        setPhase(VoiceCallPhase.Speaking, "正在说话…")
        tts.speak(textToSpeak)

        speakWatchJob?.cancel()
        speakWatchJob = scope.launch {
            withTimeoutOrNull(15_000) {
                tts.isSpeaking.first { it }
            }
            withTimeoutOrNull(180_000) {
                tts.isSpeaking.first { !it }
            }
            if (_ui.value.phase == VoiceCallPhase.Speaking) {
                setPhase(VoiceCallPhase.Listening, "正在聆听…")
                beginListening()
            }
        }
    }

    private fun extractAssistantReply(conversation: Conversation): String {
        val last = conversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
        return last?.toText()?.trim().orEmpty()
    }

    private fun setPhase(phase: VoiceCallPhase, message: String) {
        _ui.update {
            it.copy(
                phase = phase,
                statusMessage = message,
                errorMessage = if (phase == VoiceCallPhase.Error) message else it.errorMessage,
            )
        }
    }
}
