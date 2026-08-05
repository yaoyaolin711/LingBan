package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.VoiceCallTtsResolveResult
import me.rerere.rikkahub.data.model.resolveChatTts
import me.rerere.rikkahub.ui.pages.voicecall.VoiceCallGate
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Composable access to the app-scoped TTS state.
 * Do not cleanup on Activity dispose so minimized voice calls keep speaking.
 */
@Composable
fun rememberCustomTtsState(): CustomTtsState {
    val ttsState = koinInject<CustomTtsState>()
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    DisposableEffect(settings.selectedTTSProviderId, settings.ttsProviders) {
        ttsState.updateProviderFromSettings(settings.getSelectedTTSProvider())
        onDispose { }
    }

    return ttsState
}

/**
 * Interface defining the public API of our custom TTS state holder.
 */
interface CustomTtsState {
    val isAvailable: StateFlow<Boolean>
    val isSpeaking: StateFlow<Boolean>
    val error: StateFlow<String?>
    val currentChunk: StateFlow<Int>
    val totalChunks: StateFlow<Int>
    val playbackState: StateFlow<PlaybackState>

    fun updateProviderFromSettings(provider: TTSProviderSetting?)
    fun speak(text: String, flushCalled: Boolean = true)

    /**
     * Speak using a one-shot provider (chat朗读).
     * Does not change global TTS selection; restores after playback unless a voice-call override is active.
     */
    fun speakWithProvider(provider: TTSProviderSetting, text: String, flushCalled: Boolean = true)
    fun stop()
    fun pause()
    fun resume()
    fun skipNext()
    fun fastForward(ms: Long = 5_000)
    fun setSpeed(speed: Float)

    /** Temporarily use [provider] for speak(); does not change global TTS selection. */
    fun setOverrideProvider(provider: TTSProviderSetting?)

    /** Clear temporary override and restore the globally selected TTS provider. */
    fun clearOverride()

    fun cleanup()
}

/** Resolve assistant chat TTS and speak; returns resolve result for error UI. */
fun CustomTtsState.speakForChat(
    settings: Settings,
    assistant: Assistant,
    text: String,
): VoiceCallTtsResolveResult {
    val resolved = resolveChatTts(settings, assistant)
    when (resolved) {
        is VoiceCallTtsResolveResult.Ready -> speakWithProvider(resolved.provider, text)
        else -> Unit
    }
    return resolved
}

class CustomTtsStateImpl(
    private val context: Context,
    private val settingsStore: SettingsStore,
) : CustomTtsState, KoinComponent {

    private val ttsManager by inject<TTSManager>()
    private val controller by lazy { TtsController(context, ttsManager) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var restoreJob: Job? = null

    private var settingsProvider: TTSProviderSetting? = null
    private var overrideProvider: TTSProviderSetting? = null

    override val isAvailable: StateFlow<Boolean> get() = controller.isAvailable
    override val isSpeaking: StateFlow<Boolean> get() = controller.isSpeaking
    override val error: StateFlow<String?> get() = controller.error
    override val currentChunk: StateFlow<Int> get() = controller.currentChunk
    override val totalChunks: StateFlow<Int> get() = controller.totalChunks
    override val playbackState: StateFlow<PlaybackState> get() = controller.playbackState

    override fun updateProviderFromSettings(provider: TTSProviderSetting?) {
        settingsProvider = provider
        if (overrideProvider == null) {
            controller.setProvider(provider)
        }
    }

    override fun setOverrideProvider(provider: TTSProviderSetting?) {
        restoreJob?.cancel()
        overrideProvider = provider
        controller.setProvider(provider ?: settingsProvider)
    }

    override fun clearOverride() {
        overrideProvider = null
        controller.setProvider(settingsProvider ?: settingsStore.settingsFlow.value.getSelectedTTSProvider())
    }

    override fun speak(text: String, flushCalled: Boolean) {
        val processed = text.stripMarkdown()
        controller.speak(processed, flushCalled)
    }

    override fun speakWithProvider(provider: TTSProviderSetting, text: String, flushCalled: Boolean) {
        val processed = text.stripMarkdown()
        // Voice call owns the shared controller via override — don't steal it.
        if (overrideProvider != null || VoiceCallGate.active) {
            controller.speak(processed, flushCalled)
            return
        }
        restoreJob?.cancel()
        controller.setProvider(provider)
        controller.speak(processed, flushCalled)
        restoreJob = scope.launch {
            withTimeoutOrNull(3_000) { isSpeaking.first { it } }
            isSpeaking.first { !it }
            if (overrideProvider == null && !VoiceCallGate.active) {
                controller.setProvider(settingsProvider)
            }
        }
    }

    override fun stop() {
        controller.stop()
    }

    override fun pause() {
        controller.pause()
        Log.d("CustomTtsState", "TTS paused")
    }

    override fun resume() {
        controller.resume()
        Log.d("CustomTtsState", "TTS resumed")
    }

    override fun skipNext() {
        controller.skipNext()
    }

    override fun fastForward(ms: Long) {
        controller.fastForward(ms)
    }

    override fun setSpeed(speed: Float) {
        controller.setSpeed(speed)
    }

    override fun cleanup() {
        restoreJob?.cancel()
        overrideProvider = null
        controller.dispose()
    }
}
