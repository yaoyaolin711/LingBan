package me.rerere.rikkahub.ui.pages.voicecall

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.overlay.VoiceCallFloatHost
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.VoiceCallForegroundService
import me.rerere.rikkahub.ui.hooks.CustomAsrState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallCoordinator"

/**
 * Process-scoped owner of an active voice call.
 * Survives VoiceCallPage dispose so the duplex loop can keep running minimized.
 */
class VoiceCallCoordinator(
    private val context: Application,
    private val appScope: AppScope,
    private val chatService: ChatService,
    private val settingsStore: SettingsStore,
    private val asr: CustomAsrState,
    private val tts: CustomTtsState,
    private val floatHost: VoiceCallFloatHost,
) {
    private val _conversationId = MutableStateFlow<Uuid?>(null)
    val conversationId: StateFlow<Uuid?> = _conversationId.asStateFlow()

    private val _minimized = MutableStateFlow(false)
    val minimized: StateFlow<Boolean> = _minimized.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _ui = MutableStateFlow(VoiceCallUiState())
    val ui: StateFlow<VoiceCallUiState> = _ui.asStateFlow()

    @Volatile
    private var session: VoiceCallSession? = null

    private var uiBridgeJob: Job? = null

    val isInCall: Boolean
        get() = _active.value && session != null

    fun currentSession(): VoiceCallSession? = session

    /**
     * Start or resume a call for [conversationId].
     * Returns the live session (existing or newly started).
     */
    fun ensureStarted(conversationId: Uuid): VoiceCallSession {
        val existing = session
        if (existing != null && _conversationId.value == conversationId && _active.value) {
            expand()
            return existing
        }
        if (existing != null) {
            hangUpInternal()
        }

        val settings = settingsStore.settingsFlow.value
        asr.updateProvider(settings.getSelectedASRProvider())
        tts.updateProviderFromSettings(settings.getSelectedTTSProvider())

        val newSession = VoiceCallSession(
            scope = appScope,
            chatService = chatService,
            settingsStore = settingsStore,
            asr = asr,
            tts = tts,
            conversationId = conversationId,
            appContext = context,
        )
        session = newSession
        _conversationId.value = conversationId
        _minimized.value = false
        _active.value = true
        VoiceCallGate.active = true

        uiBridgeJob?.cancel()
        uiBridgeJob = appScope.launch {
            newSession.ui.collect { _ui.value = it }
        }

        newSession.start()
        VoiceCallForegroundService.start(context, conversationId)
        floatHost.bind(this)
        Log.i(TAG, "voice call started: $conversationId")
        return newSession
    }

    fun minimize() {
        if (!_active.value) return
        _minimized.value = true
        floatHost.showMinimized()
        Log.i(TAG, "voice call minimized")
    }

    fun expand() {
        if (!_active.value) return
        _minimized.value = false
        floatHost.hide()
    }

    fun hangUp() {
        hangUpInternal()
    }

    fun finishUtterance() = session?.finishUtterance()
    fun interrupt() = session?.interrupt()
    fun applyVoiceAndListen() = session?.applyVoiceAndListen()

    private fun hangUpInternal() {
        val id = _conversationId.value
        Log.i(TAG, "hangUp conversation=$id")
        uiBridgeJob?.cancel()
        uiBridgeJob = null
        session?.hangUp()
        session = null
        _active.value = false
        _minimized.value = false
        _conversationId.value = null
        _ui.value = VoiceCallUiState(phase = VoiceCallPhase.Ended, statusMessage = "通话已结束")
        VoiceCallGate.active = false
        floatHost.hide()
        VoiceCallForegroundService.stop(context)
    }
}
