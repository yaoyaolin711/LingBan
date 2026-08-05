package me.rerere.rikkahub.overlay

import android.app.Application
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxScopeType
import com.petterp.floatingx.compose.enableComposeSupport
import com.petterp.floatingx.listener.control.IFxAppControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CallEnd01
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.pages.voicecall.VoiceCallCoordinator
import me.rerere.rikkahub.ui.pages.voicecall.VoiceCallPhase
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.canDrawOverlays
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallFloat"
private const val FLOAT_TAG = "voice_call_ball"

data class VoiceCallFloatState(
    val visible: Boolean = false,
    val expanded: Boolean = false,
    val conversationId: Uuid? = null,
    val phase: VoiceCallPhase = VoiceCallPhase.Idle,
    val statusText: String = "",
    val assistantName: String = "",
    val avatar: Avatar = Avatar.Dummy,
)

/**
 * WeChat-style floating ball while a voice call is minimized.
 */
class VoiceCallFloatHost(
    private val context: Application,
    private val settingsStore: SettingsStore,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(VoiceCallFloatState())
    val state: StateFlow<VoiceCallFloatState> = _state.asStateFlow()

    @Volatile
    private var control: IFxAppControl? = null

    @Volatile
    private var coordinator: VoiceCallCoordinator? = null

    @Volatile
    private var refreshRunnable: Runnable? = null

    fun bind(coordinator: VoiceCallCoordinator) {
        this.coordinator = coordinator
    }

    fun showMinimized() {
        val c = coordinator ?: return
        publishState(c, expanded = _state.value.expanded)
        mainHandler.post { ensureFloatInstalled() }
        startRefreshLoop()
    }

    fun hide() {
        stopRefreshLoop()
        _state.update { it.copy(visible = false, expanded = false) }
        mainHandler.post { control?.hide() }
    }

    fun toggleExpanded() {
        _state.update { it.copy(expanded = !it.expanded) }
    }

    fun openVoiceCall() {
        val conversationId = _state.value.conversationId ?: coordinator?.conversationId?.value
        coordinator?.expand()
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (conversationId != null) {
                putExtra(EXTRA_VOICE_CALL_CONVERSATION_ID, conversationId.toString())
            }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "openVoiceCall failed", it) }
    }

    fun hangUp() {
        coordinator?.hangUp()
        hide()
    }

    private fun startRefreshLoop() {
        stopRefreshLoop()
        val runnable = object : Runnable {
            override fun run() {
                val coord = coordinator
                if (coord == null || !coord.minimized.value || !coord.active.value) return
                publishState(coord, expanded = _state.value.expanded)
                mainHandler.postDelayed(this, 400L)
            }
        }
        refreshRunnable = runnable
        mainHandler.postDelayed(runnable, 400L)
    }

    private fun stopRefreshLoop() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        refreshRunnable = null
    }

    private fun publishState(coord: VoiceCallCoordinator, expanded: Boolean) {
        val assistant = settingsStore.settingsFlow.value.getCurrentAssistant()
        val ui = coord.ui.value
        _state.value = VoiceCallFloatState(
            visible = true,
            expanded = expanded,
            conversationId = coord.conversationId.value,
            phase = ui.phase,
            statusText = phaseStatusLabel(ui.phase),
            assistantName = assistant.name.ifBlank { "Solace" },
            avatar = assistant.avatar,
        )
    }

    private fun ensureFloatInstalled() {
        if (!context.canDrawOverlays()) {
            Log.i(TAG, "overlay permission missing; SYSTEM_AUTO may fall back to in-app float")
        }
        val current = control
        if (current != null) {
            current.show()
            return
        }
        runCatching {
            control = FloatingX.install {
                setContext(context)
                setTag(FLOAT_TAG)
                enableComposeSupport()
                setScopeType(FxScopeType.SYSTEM_AUTO)
                setGravity(FxGravity.RIGHT_OR_TOP)
                setOffsetXY(-16f, 120f)
                setEnableAnimation(true)
                setEnableEdgeAdsorption(true)
                setEdgeOffset(8f)
                setLayoutView(createComposeView())
            }.also { it.show() }
        }.onFailure {
            Log.e(TAG, "install voice call float failed", it)
        }
    }

    private fun createComposeView(): View {
        return ComposeView(context).apply {
            // Avoid opaque black host rect when dragging the overlay.
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setContent {
                RikkahubTheme {
                    val ballState by state.collectAsState()
                    if (ballState.visible) {
                        VoiceCallFloatContent(
                            state = ballState,
                            onToggleExpand = { toggleExpanded() },
                            onOpen = { openVoiceCall() },
                            onHangUp = { hangUp() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_VOICE_CALL_CONVERSATION_ID = "voiceCallConversationId"
    }
}

@Composable
private fun VoiceCallFloatContent(
    state: VoiceCallFloatState,
    onToggleExpand: () -> Unit,
    onOpen: () -> Unit,
    onHangUp: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "voice_call_pulse")
    val activePhase = state.phase == VoiceCallPhase.Speaking ||
        state.phase == VoiceCallPhase.Listening ||
        state.phase == VoiceCallPhase.Thinking
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (activePhase) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val accent = phaseColor(state.phase)
    // Soft light chip — avoid dark Material Surface elevation (reads as a black box while dragging).
    val chipColor = Color(0xF2FFFFFF)
    val textColor = Color(0xFF1C1B1F)
    val secondaryText = Color(0xFF5C5B60)

    Box(
        modifier = Modifier
            .background(Color.Transparent)
            .padding(6.dp),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(chipColor)
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .scale(scale),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(2.5.dp, accent, CircleShape),
                    )
                    UIAvatar(
                        name = state.assistantName,
                        value = state.avatar,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape),
                        loading = activePhase,
                        onClick = onOpen,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(1.5.dp, Color.White, CircleShape),
                    )
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.assistantName.isNotBlank()) {
                        Text(
                            text = state.assistantName,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.expanded,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "回到通话",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF5C6BC0),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1A5C6BC0))
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE85D5D))
                            .clickable(onClick = onHangUp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            HugeIcons.CallEnd01,
                            contentDescription = "Hang up",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = " 挂断",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun phaseColor(phase: VoiceCallPhase): Color = when (phase) {
    VoiceCallPhase.Listening -> Color(0xFF4CAF50)
    VoiceCallPhase.Thinking -> Color(0xFFFFA726)
    VoiceCallPhase.Speaking -> Color(0xFF5C6BC0)
    VoiceCallPhase.Error -> Color(0xFFE85D5D)
    else -> Color(0xFF90A4AE)
}

/** User-facing status: what the companion is doing right now. */
private fun phaseStatusLabel(phase: VoiceCallPhase): String = when (phase) {
    VoiceCallPhase.Idle -> "准备中"
    VoiceCallPhase.Listening -> "聆听中"
    VoiceCallPhase.Thinking -> "思考中"
    VoiceCallPhase.Speaking -> "回复中"
    VoiceCallPhase.NeedsSetup -> "待配置"
    VoiceCallPhase.Error -> "出错了"
    VoiceCallPhase.Ended -> "已结束"
}
