package me.rerere.rikkahub.ui.pages.voicecall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.ChatTtsSource
import me.rerere.rikkahub.data.model.VoiceCallTtsResolveResult
import me.rerere.rikkahub.data.model.VoiceTier
import me.rerere.rikkahub.data.model.resolveVoiceCallDisplay
import me.rerere.rikkahub.data.model.resolveVoiceCallTts
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.GlassCard
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.SolaceTheme
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoiceSelectionPage(vm: SettingVM = koinViewModel()) {
    val nav = LocalNavController.current
    val tts = LocalTTSState.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assistant = settings.getCurrentAssistant()
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val display = resolveVoiceCallDisplay(settings, assistant.voiceCall)
    val resolve = resolveVoiceCallTts(settings, assistant.voiceCall)

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.clearOverride()
        }
    }

    LaunchedEffect(Unit) {
        tts.error.collectLatest { err ->
            if (!err.isNullOrBlank()) {
                toaster.show(err, type = ToastType.Error)
            }
        }
    }

    fun preview() {
        val latest = vm.settings.value
        val currentAssistant = latest.getCurrentAssistant()
        val sample = resolveVoiceCallDisplay(latest, currentAssistant.voiceCall).sampleText
        when (val r = resolveVoiceCallTts(latest, currentAssistant.voiceCall)) {
            is VoiceCallTtsResolveResult.Ready -> {
                scope.launch {
                    toaster.show("正在试听…")
                    tts.setOverrideProvider(r.provider)
                    tts.speak(sample)
                }
            }
            is VoiceCallTtsResolveResult.NeedsApiKey -> {
                toaster.show("请先填写 ${r.providerLabel} API Key", type = ToastType.Warning)
            }
            is VoiceCallTtsResolveResult.Unavailable -> {
                toaster.show(r.message, type = ToastType.Error)
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("选择通话声线") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("guide") {
                GlassCard {
                    Text(
                        text = "操作指引",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (assistant.voiceCall.tier) {
                            VoiceTier.Local ->
                                "① 选择「本地」→ ② 点选系统声线 → ③ 可试听 → ④ 点「使用此声线」返回通话"
                            VoiceTier.Preset ->
                                "① 选择「预设声线」→ ② 点选 Qwen 等系统音色 → ③ 若提示填 Key 则粘贴保存 → ④ 试听后使用"
                            VoiceTier.Custom ->
                                "① 选择「自定义声线」→ ② 点「添加声线」进入填写页 → ③ 选 Mossland 等服务商并填完整 voice_id + Key → ④ 保存后回到本页选中 → ⑤ 试听"
                        },
                        style = typography.bodySmall,
                        color = colors.secondaryText,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "当前：${display.displayName}",
                        style = typography.labelLarge,
                        color = colors.primary,
                    )
                    if (assistant.chatTtsSource == ChatTtsSource.SameAsVoiceCall) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "提示：此助手的聊天小喇叭已设为「与通话声线相同」，改这里也会影响聊天朗读。",
                            style = typography.bodySmall,
                            color = colors.secondaryText,
                        )
                    }
                }
            }

            item("picker") {
                GlassCard {
                    Text(
                        text = "声线库",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(12.dp))
                    VoicePresetPicker(
                        settings = settings,
                        onUpdateSettings = { vm.updateSettings(it) },
                        onPreview = { preview() },
                        showActionButtons = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item("actions") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (resolve) {
                        is VoiceCallTtsResolveResult.Ready -> {
                            OutlinedButton(
                                onClick = { preview() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("试听当前声线")
                            }
                            Button(
                                onClick = { nav.popBackStack() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("使用此声线")
                            }
                        }
                        is VoiceCallTtsResolveResult.NeedsApiKey -> {
                            Text(
                                text = "还差一步：在上方填写 ${resolve.providerLabel} 的 API Key 并保存",
                                style = typography.bodySmall,
                                color = colors.secondaryText,
                            )
                        }
                        is VoiceCallTtsResolveResult.Unavailable -> {
                            Text(
                                text = resolve.message,
                                style = typography.bodySmall,
                                color = colors.secondaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}
