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
import androidx.compose.runtime.rememberCoroutineScope
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
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.theme.SolaceTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun VoiceCallPage(conversationId: Uuid) {
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(conversationId.toString()) })
    val nav = LocalNavController.current
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assistant = settings.getCurrentAssistant()
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography

    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)

    val session = remember(conversationId) {
        VoiceCallSession(
            scope = scope,
            chatVM = vm,
            asr = asr,
            tts = tts,
            conversationId = conversationId,
        )
    }
    val ui by session.ui.collectAsStateWithLifecycle()

    var callStarted by remember { mutableStateOf(false) }
    val voiceReady = resolveVoiceCallTts(settings, assistant.voiceCall) is VoiceCallTtsResolveResult.Ready

    fun openVoiceSelection() {
        nav.navigate(Screen.VoiceSelection)
    }

    fun tryStartCall() {
        if (callStarted) {
            session.applyVoiceAndListen()
            return
        }
        if (!asrPermission.allRequiredPermissionsGranted) {
            asrPermission.requestPermissions()
            return
        }
        when (resolveVoiceCallTts(vm.settings.value, vm.settings.value.getCurrentAssistant().voiceCall)) {
            is VoiceCallTtsResolveResult.Ready -> {
                callStarted = true
                session.start()
            }
            else -> openVoiceSelection()
        }
    }

    fun hangUpAndExit() {
        session.hangUp()
        nav.popBackStack()
    }

    BackHandler { hangUpAndExit() }

    DisposableEffect(Unit) {
        onDispose {
            session.hangUp()
        }
    }

    LaunchedEffect(asrPermission.allRequiredPermissionsGranted) {
        if (!asrPermission.allRequiredPermissionsGranted) {
            asrPermission.requestPermissions()
        }
    }

    // Local system voices can start immediately; cloud voices wait for explicit start.
    LaunchedEffect(asrPermission.allRequiredPermissionsGranted, settings) {
        if (callStarted) return@LaunchedEffect
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
                    IconButton(onClick = { openVoiceSelection() }) {
                        Icon(HugeIcons.Settings01, contentDescription = "Voice settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            CompanionAvatar(
                name = assistant.name.ifBlank { "AI" },
                avatar = assistant.avatar,
                size = CompanionAvatarSize.Hero,
                showHalo = true,
                breath = ui.phase == VoiceCallPhase.Speaking || ui.phase == VoiceCallPhase.Listening,
                showName = false,
                statusLabel = ui.statusMessage.ifBlank { phaseLabel(ui.phase) },
            )

            Spacer(Modifier.height(20.dp))

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

            Spacer(Modifier.height(16.dp))

            AmplitudeBars(
                amplitudes = ui.amplitudes,
                active = ui.phase == VoiceCallPhase.Listening || ui.phase == VoiceCallPhase.Speaking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )

            Spacer(Modifier.height(12.dp))

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
                VoiceCallPhase.NeedsSetup, VoiceCallPhase.Idle -> {
                    Text(
                        text = if (!voiceReady) {
                            "先选择并配置通话声线"
                        } else {
                            "声线已就绪，可以开始通话"
                        },
                        style = typography.bodyMedium,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                        if (voiceReady) {
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
                    Spacer(Modifier.height(32.dp))
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
                                onClick = {
                                    when (ui.phase) {
                                        VoiceCallPhase.Listening -> session.finishUtterance()
                                        VoiceCallPhase.Speaking, VoiceCallPhase.Thinking -> session.interrupt()
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
