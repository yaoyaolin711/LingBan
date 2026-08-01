package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Composable function to remember and manage custom TTS state.
 * Uses user-configured TTS providers instead of system TTS.
 */
@Composable
fun rememberCustomTtsState(): CustomTtsState {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    val ttsState = remember {
        CustomTtsStateImpl(
            context = context.applicationContext,
            settingsStore = settingsStore
        )
    }

    DisposableEffect(settings.selectedTTSProviderId, settings.ttsProviders) {
        ttsState.updateProviderFromSettings(settings.getSelectedTTSProvider())
        onDispose { }
    }

    DisposableEffect(ttsState) {
        onDispose {
            ttsState.cleanup()
        }
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

    fun speak(text: String, flushCalled: Boolean = true)
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

private class CustomTtsStateImpl(
    private val context: Context,
    private val settingsStore: SettingsStore
) : CustomTtsState, KoinComponent {

    private val ttsManager by inject<TTSManager>()
    private val controller by lazy { TtsController(context, ttsManager) }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    private var settingsProvider: TTSProviderSetting? = null
    private var overrideProvider: TTSProviderSetting? = null

    override val isAvailable: StateFlow<Boolean> get() = controller.isAvailable
    override val isSpeaking: StateFlow<Boolean> get() = controller.isSpeaking
    override val error: StateFlow<String?> get() = controller.error
    override val currentChunk: StateFlow<Int> get() = controller.currentChunk
    override val totalChunks: StateFlow<Int> get() = controller.totalChunks
    override val playbackState: StateFlow<PlaybackState> get() = controller.playbackState

    fun updateProviderFromSettings(provider: TTSProviderSetting?) {
        settingsProvider = provider
        if (overrideProvider == null) {
            controller.setProvider(provider)
        }
    }

    override fun setOverrideProvider(provider: TTSProviderSetting?) {
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
        overrideProvider = null
        controller.dispose()
        currentJob = null
    }
}
