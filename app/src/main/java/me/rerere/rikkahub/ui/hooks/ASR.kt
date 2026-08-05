package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.providers.DashScopeASRController
import me.rerere.asr.providers.MiMoASRController
import me.rerere.asr.providers.OpenAIRealtimeASRController
import me.rerere.asr.providers.SiliconFlowASRController
import me.rerere.asr.providers.StepASRController
import me.rerere.asr.providers.SystemASRController
import me.rerere.asr.providers.VolcengineASRController
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

/**
 * Composable access to the app-scoped ASR state.
 * Provider selection syncs from settings; do not cleanup on Activity dispose
 * so minimized voice calls can keep listening.
 */
@Composable
fun rememberCustomAsrState(): CustomAsrState {
    val asrState = koinInject<CustomAsrState>()
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    DisposableEffect(settings.selectedASRProviderId, settings.asrProviders) {
        asrState.updateProvider(settings.getSelectedASRProvider())
        onDispose { }
    }

    return asrState
}

interface CustomAsrState {
    val state: StateFlow<ASRState>
    /** 当前 ASR 控制器对应的配置，供排障展示。 */
    val activeProvider: ASRProviderSetting?
    fun updateProvider(provider: ASRProviderSetting?)
    fun start(onTranscriptChange: (String) -> Unit)
    fun stop()
    fun cleanup()
}

class CustomAsrStateImpl(
    private val context: Context,
    private val httpClient: OkHttpClient,
) : CustomAsrState {
    private var controller: ASRController? = null
    private var providerSetting: ASRProviderSetting? = null
    private val idleState = MutableStateFlow(ASRState())

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAcceptsDelayedFocusGain(false)
        .build()

    override val state: StateFlow<ASRState>
        get() = controller?.state ?: idleState

    override val activeProvider: ASRProviderSetting?
        get() = providerSetting

    override fun updateProvider(provider: ASRProviderSetting?) {
        controller?.dispose()
        providerSetting = provider
        controller = provider?.let { createController(it) }
        if (controller == null) {
            idleState.value = ASRState()
        }
    }

    override fun start(onTranscriptChange: (String) -> Unit) {
        val ctrl = controller
            ?: error("ASR provider not configured or API Key missing")
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            error("Unable to acquire microphone audio focus")
        }
        ctrl.start(onTranscriptChange)
    }

    override fun stop() {
        controller?.stop()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    override fun cleanup() {
        controller?.dispose()
        controller = null
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    private fun createController(provider: ASRProviderSetting): ASRController? {
        return when (provider) {
            is ASRProviderSetting.System -> {
                SystemASRController(context, provider)
            }

            is ASRProviderSetting.OpenAIRealtime -> {
                if (provider.apiKey.isBlank()) return null
                OpenAIRealtimeASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.DashScope -> {
                if (provider.apiKey.isBlank()) return null
                DashScopeASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.Volcengine -> {
                if (provider.apiKey.isBlank()) return null
                VolcengineASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.MiMo -> {
                if (provider.apiKey.isBlank()) return null
                MiMoASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.Step -> {
                if (provider.apiKey.isBlank()) return null
                StepASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.SiliconFlow -> {
                if (provider.apiKey.isBlank()) return null
                SiliconFlowASRController(context, httpClient, provider)
            }
        }
    }
}
