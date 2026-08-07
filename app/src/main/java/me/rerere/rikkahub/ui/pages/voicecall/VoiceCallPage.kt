package me.rerere.rikkahub.ui.pages.voicecall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Call
import me.rerere.hugeicons.stroke.CallEnd01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.Settings01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.VoiceCallTtsResolveResult
import me.rerere.rikkahub.data.model.resolveVoiceCallDisplay
import me.rerere.rikkahub.data.model.resolveVoiceCallTts
import me.rerere.rikkahub.ui.components.solace.CompanionAvatar
import me.rerere.rikkahub.ui.components.solace.CompanionAvatarSize
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.theme.SolaceTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

@Composable
fun VoiceCallPage(conversationId: Uuid) {
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(conversationId.toString()) })
    val nav = LocalNavController.current
    val coordinator = koinInject<VoiceCallCoordinator>()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assistant = settings.getCurrentAssistant()
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography

    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)

    val ui by coordinator.ui.collectAsStateWithLifecycle()
    val active by coordinator.active.collectAsStateWithLifecycle()
    val activeConversationId by coordinator.conversationId.collectAsStateWithLifecycle()
    var showDiag by remember { mutableStateOf(false) }

    // True when leaving via minimize/hangup so onDispose does not double-hangUp.
    val intentionalLeave = remember { AtomicBoolean(false) }

    val voiceReady = resolveVoiceCallTts(settings, assistant.voiceCall) is VoiceCallTtsResolveResult.Ready
    val callLiveForThisConversation =
        active && activeConversationId == conversationId &&
            ui.phase != VoiceCallPhase.Ended &&
            ui.phase != VoiceCallPhase.Idle &&
            ui.phase != VoiceCallPhase.NeedsSetup

    fun openVoiceSelection() {
        nav.navigate(Screen.VoiceSelection)
    }

    fun tryStartCall() {
        if (callLiveForThisConversation) {
            coordinator.applyVoiceAndListen()
            return
        }
        if (!asrPermission.allRequiredPermissionsGranted) {
            asrPermission.requestPermissions()
            return
        }
        when (resolveVoiceCallTts(vm.settings.value, vm.settings.value.getCurrentAssistant().voiceCall)) {
            is VoiceCallTtsResolveResult.Ready -> {
                coordinator.ensureStarted(conversationId)
            }
            else -> openVoiceSelection()
        }
    }

    fun minimizeAndExit() {
        if (coordinator.isInCall && activeConversationId == conversationId) {
            intentionalLeave.set(true)
            coordinator.minimize()
            nav.popBackStack()
        } else {
            nav.popBackStack()
        }
    }

    fun hangUpAndExit() {
        intentionalLeave.set(true)
        coordinator.hangUp()
        nav.popBackStack()
    }

    // Back / system gesture: minimize (keep talking) instead of hang up.
    BackHandler {
        if (callLiveForThisConversation) {
            minimizeAndExit()
        } else {
            nav.popBackStack()
        }
    }

    DisposableEffect(conversationId) {
        intentionalLeave.set(false)
        if (activeConversationId == conversationId && active) {
            coordinator.expand()
        }
        onDispose {
            // Leaving without explicit minimize/hangup (e.g. unexpected teardown): hang up.
            if (!intentionalLeave.get() &&
                coordinator.conversationId.value == conversationId &&
                coordinator.active.value &&
                !coordinator.minimized.value
            ) {
                coordinator.hangUp()
            }
        }
    }

    LaunchedEffect(asrPermission.allRequiredPermissionsGranted) {
        if (!asrPermission.allRequiredPermissionsGranted) {
            asrPermission.requestPermissions()
        }
    }

    // Local system voices can start immediately; cloud voices wait for explicit start.
    LaunchedEffect(asrPermission.allRequiredPermissionsGranted, settings, active) {
        if (callLiveForThisConversation) return@LaunchedEffect
        if (active && activeConversationId == conversationId) return@LaunchedEffect
        if (!asrPermission.allRequiredPermissionsGranted) return@LaunchedEffect
        val display = resolveVoiceCallDisplay(settings, assistant.voiceCall)
        if (!display.requiresApiKey &&
            resolveVoiceCallTts(settings, assistant.voiceCall) is VoiceCallTtsResolveResult.Ready
        ) {
            tryStartCall()
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.text,
                    actionIconContentColor = colors.secondaryText,
                ),
                title = {
                    Column {
                        Text(
                            text = assistant.name.ifBlank { "AI Companion" },
                            style = typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                        )
                        val display = resolveVoiceCallDisplay(settings, assistant.voiceCall)
                        Text(
                            text = display.displayName,
                            style = typography.labelMedium,
                            color = colors.secondaryText,
                        )
                    }
                },
                actions = {
                    if (VoiceCallDiag.ENABLED) {
                        VoiceCallDebugFab(visible = true, onOpen = { showDiag = true })
                    }
                    if (callLiveForThisConversation) {
                        IconButton(onClick = { minimizeAndExit() }) {
                            Icon(HugeIcons.ArrowDown01, contentDescription = "缩小到悬浮窗")
                        }
                    }
                    IconButton(onClick = { openVoiceSelection() }) {
                        Icon(HugeIcons.Settings01, contentDescription = "Voice settings")
                    }
                },
            )
        },
    ) { padding ->
        if (showDiag) {
            VoiceCallDebugDialog(
                onDismiss = { showDiag = false },
                onForceSubmit = {
                    VoiceCallDiag.log("UI", "force submit clicked")
                    coordinator.finishUtterance()
                },
                onForceRelisten = {
                    VoiceCallDiag.log("UI", "force relisten clicked")
                    coordinator.applyVoiceAndListen()
                },
                onClearLog = { VoiceCallDiag.clear() },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            CompanionAvatar(
                name = assistant.name.ifBlank { "AI" },
                avatar = assistant.avatar,
                size = CompanionAvatarSize.Hero,
                showHalo = true,
                breath = ui.phase == VoiceCallPhase.Speaking || ui.phase == VoiceCallPhase.Listening,
                showName = false,
                statusLabel = ui.statusMessage.ifBlank { phaseLabel(ui.phase) },
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = phaseLabel(ui.phase),
                style = typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = colors.text,
            )

            if (ui.statusMessage.isNotBlank()) {
                Text(
                    text = ui.statusMessage,
                    style = typography.bodyMedium,
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AmplitudeBars(
                amplitudes = ui.amplitudes,
                active = ui.phase == VoiceCallPhase.Listening || ui.phase == VoiceCallPhase.Speaking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (ui.phase == VoiceCallPhase.Listening ||
                ui.phase == VoiceCallPhase.Thinking ||
                ui.phase == VoiceCallPhase.Speaking
            ) {
                VoiceCallLiveStatus(
                    ui = ui,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            val caption = when {
                ui.partialTranscript.isNotBlank() -> ui.partialTranscript
                ui.lastAssistantText.isNotBlank() && ui.phase == VoiceCallPhase.Speaking -> ui.lastAssistantText
                ui.lastUserText.isNotBlank() && ui.phase == VoiceCallPhase.Thinking -> "You: ${ui.lastUserText}"
                else -> ""
            }
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = typography.bodyLarge,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainer)
                        .padding(16.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            when (ui.phase) {
                VoiceCallPhase.NeedsSetup, VoiceCallPhase.Idle, VoiceCallPhase.Ended, VoiceCallPhase.Error -> {
                    val asrSetupNeeded = ui.errorMessage?.let {
                        me.rerere.asr.SpeechRecognitionSupport.isHardFailure(it)
                    } == true ||
                        ui.statusMessage.contains("系统语音") ||
                        ui.statusMessage.contains("云端 ASR")
                    Text(
                        text = when {
                            asrSetupNeeded -> "当前设备系统语音识别不可用，请配置云端 ASR"
                            !voiceReady -> "先选择并配置通话声线"
                            ui.phase == VoiceCallPhase.Error ->
                                ui.errorMessage ?: ui.statusMessage.ifBlank { "出错了" }
                            else -> "声线已就绪，可以开始通话"
                        },
                        style = typography.bodyMedium,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (asrSetupNeeded) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledIconButton(
                                    onClick = {
                                        if (callLiveForThisConversation) {
                                            intentionalLeave.set(true)
                                            coordinator.hangUp()
                                        }
                                        nav.navigate(Screen.SettingSpeech)
                                    },
                                    modifier = Modifier.size(64.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = colors.surfaceContainerHigh,
                                        contentColor = colors.text,
                                    ),
                                ) {
                                    Icon(
                                        HugeIcons.Mic01,
                                        contentDescription = "配置 ASR",
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                Text(
                                    text = "配置语音识别",
                                    style = typography.labelMedium,
                                    color = colors.secondaryText,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = { openVoiceSelection() },
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = colors.surfaceContainerHigh,
                                    contentColor = colors.text,
                                ),
                            ) {
                                Icon(HugeIcons.Settings01, contentDescription = "选择声线", modifier = Modifier.size(26.dp))
                            }
                            Text(
                                text = "选择声线",
                                style = typography.labelMedium,
                                color = colors.secondaryText,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (voiceReady && !asrSetupNeeded) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledIconButton(
                                    onClick = { tryStartCall() },
                                    modifier = Modifier.size(72.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = colors.primary,
                                        contentColor = colors.onPrimary,
                                    ),
                                ) {
                                    Icon(HugeIcons.Call, contentDescription = "开始通话", modifier = Modifier.size(28.dp))
                                }
                                Text(
                                    text = "开始通话",
                                    style = typography.labelMedium,
                                    color = colors.secondaryText,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = { minimizeAndExit() },
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = colors.surfaceContainerHigh,
                                    contentColor = colors.text,
                                ),
                            ) {
                                Icon(
                                    HugeIcons.ArrowDown01,
                                    contentDescription = "缩小",
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Text(
                                text = "缩小",
                                style = typography.labelMedium,
                                color = colors.secondaryText,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = {
                                    when (ui.phase) {
                                        VoiceCallPhase.Listening -> coordinator.finishUtterance()
                                        VoiceCallPhase.Speaking, VoiceCallPhase.Thinking -> coordinator.interrupt()
                                        else -> Unit
                                    }
                                },
                                modifier = Modifier.size(72.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = colors.surfaceContainerHigh,
                                    contentColor = colors.text,
                                ),
                            ) {
                                Icon(
                                    imageVector = when (ui.phase) {
                                        VoiceCallPhase.Speaking, VoiceCallPhase.Thinking -> HugeIcons.Mic01
                                        else -> HugeIcons.Call
                                    },
                                    contentDescription = "Done / Interrupt",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Text(
                                text = when (ui.phase) {
                                    VoiceCallPhase.Listening -> "Done"
                                    VoiceCallPhase.Speaking, VoiceCallPhase.Thinking -> "Interrupt"
                                    else -> "Mic"
                                },
                                style = typography.labelMedium,
                                color = colors.secondaryText,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = { hangUpAndExit() },
                                modifier = Modifier.size(72.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFFE85D5D),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(
                                    HugeIcons.CallEnd01,
                                    contentDescription = "Hang up",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Text(
                                text = "Hang up",
                                style = typography.labelMedium,
                                color = colors.secondaryText,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmplitudeBars(
    amplitudes: List<Float>,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SolaceTheme.colorScheme
    val bars = if (amplitudes.isEmpty()) List(24) { 0.08f } else amplitudes.takeLast(24)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEach { amp ->
            val h = ((if (active) amp else amp * 0.3f).coerceIn(0.08f, 1f) * 40f).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(CircleShape)
                    .background(if (active) colors.primary else colors.outlineVariant),
            )
        }
    }
}

private fun phaseLabel(phase: VoiceCallPhase): String = when (phase) {
    VoiceCallPhase.Idle -> "Ready"
    VoiceCallPhase.Listening -> "Listening"
    VoiceCallPhase.Thinking -> "Thinking"
    VoiceCallPhase.Speaking -> "Speaking"
    VoiceCallPhase.NeedsSetup -> "Setup needed"
    VoiceCallPhase.Error -> "Error"
    VoiceCallPhase.Ended -> "Ended"
}
