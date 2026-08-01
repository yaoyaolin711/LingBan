package me.rerere.rikkahub.overlay.pet

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxScopeType
import com.petterp.floatingx.compose.enableComposeSupport
import com.petterp.floatingx.listener.control.IFxAppControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.policy.CompanionEmotionResolver
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.canDrawOverlays
import me.rerere.rikkahub.utils.openOverlayPermissionSettings
import kotlin.uuid.Uuid

private const val TAG = "CompanionPet"
private const val FLOAT_TAG = "companion_avatar"
private const val BUBBLE_CLEAR_MS = 10_000L

private data class PetHostConfig(
    val enabled: Boolean,
    val name: String,
    val assistantId: Uuid,
    val avatar: Avatar,
)

/**
 * 陪伴悬浮头像宿主：开陪伴模式即显示伴侣头像；主动发言用旁侧短气泡。
 */
class CompanionPetHost(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val emotionResolver: CompanionEmotionResolver,
    private val renderer: PetRenderer,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bubbleClearRunnable: Runnable? = null

    private val _state = MutableStateFlow(CompanionPetState())
    val state: StateFlow<CompanionPetState> = _state.asStateFlow()

    @Volatile
    private var control: IFxAppControl? = null

    init {
        appScope.launch(Dispatchers.Default) {
            settingsStore.settingsFlow
                .map { settings ->
                    val assistant = settings.getCurrentAssistant()
                    PetHostConfig(
                        enabled = assistant.enableCompanion,
                        name = assistant.name,
                        assistantId = assistant.id,
                        avatar = resolvePetAvatar(assistant),
                    )
                }
                .distinctUntilChanged()
                .collect { config ->
                    if (!config.enabled) {
                        hide()
                        return@collect
                    }
                    val emotion = runCatching {
                        emotionResolver.resolveForAssistant(config.assistantId)
                    }.getOrDefault(CompanionEmotionState.CALM)
                    show(
                        assistantName = config.name,
                        avatar = config.avatar,
                        emotion = emotion,
                        statusText = config.name.ifBlank { "陪伴中" },
                    )
                }
        }
    }

    fun show(
        assistantName: String = _state.value.assistantName,
        avatar: Avatar = _state.value.avatar,
        emotion: CompanionEmotionState = _state.value.emotion,
        statusText: String = _state.value.statusText,
        conversationId: Uuid? = _state.value.conversationId,
    ) {
        if (!context.canDrawOverlays()) {
            Log.w(TAG, "overlay permission missing; companion avatar not shown")
            return
        }
        _state.value = _state.value.copy(
            visible = true,
            assistantName = assistantName,
            avatar = avatar,
            emotion = emotion,
            statusText = statusText,
            conversationId = conversationId,
        )
        mainHandler.post { ensureFloatInstalled() }
    }

    fun hide() {
        clearBubbleTimer()
        _state.value = _state.value.copy(visible = false, bubbleText = "", conversationId = null)
        mainHandler.post {
            control?.hide()
        }
    }

    fun updateEmotion(emotion: CompanionEmotionState, statusText: String? = null) {
        if (!_state.value.visible) return
        _state.value = _state.value.copy(
            emotion = emotion,
            statusText = statusText ?: _state.value.statusText,
        )
    }

    fun showBubble(
        text: String,
        clearAfterMs: Long = BUBBLE_CLEAR_MS,
        conversationId: Uuid? = null,
    ) {
        if (!_state.value.visible || text.isBlank()) return
        val clipped = text.trim().let { raw ->
            if (raw.length <= COMPANION_BUBBLE_MAX_CHARS) raw
            else raw.take(COMPANION_BUBBLE_MAX_CHARS - 1) + "…"
        }
        _state.value = _state.value.copy(
            bubbleText = clipped,
            conversationId = conversationId ?: _state.value.conversationId,
        )
        clearBubbleTimer()
        val clear = Runnable {
            _state.value = _state.value.copy(bubbleText = "")
            bubbleClearRunnable = null
        }
        bubbleClearRunnable = clear
        mainHandler.postDelayed(clear, clearAfterMs)
    }

    private fun clearBubbleTimer() {
        bubbleClearRunnable?.let { mainHandler.removeCallbacks(it) }
        bubbleClearRunnable = null
    }

    private fun ensureFloatInstalled() {
        if (control != null) {
            control!!.show()
            return
        }
        control = FloatingX.install {
            setContext(context)
            setTag(FLOAT_TAG)
            enableComposeSupport()
            setScopeType(FxScopeType.SYSTEM_AUTO)
            setGravity(FxGravity.LEFT_OR_BOTTOM)
            setLayoutView(createComposeView())
        }.also { it.show() }
    }

    private fun createComposeView(): View {
        return ComposeView(context).apply {
            setContent {
                val petState by _state.collectAsState()
                if (!petState.visible) return@setContent
                RikkahubTheme {
                    renderer.Content(
                        state = petState,
                        onClick = { openApp(petState.conversationId?.toString()) },
                    )
                }
            }
        }
    }

    private fun openApp(conversationId: String? = null) {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!conversationId.isNullOrBlank()) {
                putExtra("conversationId", conversationId)
            }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "open app from companion avatar failed", it) }
    }

    fun requestOverlayIfNeeded(): Boolean {
        if (context.canDrawOverlays()) return true
        context.openOverlayPermissionSettings()
        return false
    }
}
