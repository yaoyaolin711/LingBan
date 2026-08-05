package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.data.model.ChatTtsSource
import me.rerere.rikkahub.data.model.resolveChatTtsLabel
import me.rerere.rikkahub.data.model.resolveVoiceCallDisplay
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantVoicePage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val voiceDisplay = resolveVoiceCallDisplay(settings, assistant.voiceCall)
    val chatLabel = resolveChatTtsLabel(settings, assistant)
    val globalTtsName = settings.getSelectedTTSProvider()?.name?.takeIf { it.isNotBlank() } ?: "系统 TTS"

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("声音设置") },
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
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "聊天朗读与语音通话可分别配置，也可共用同一套声线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                Card(
                    colors = CustomColors.cardColorsOnSurfaceContainer,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    FormItem(
                        label = { Text("聊天朗读") },
                        description = { Text("消息下方小喇叭 / 自动朗读使用的声音") },
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Select(
                            options = ChatTtsSource.entries,
                            selectedOption = assistant.chatTtsSource,
                            onOptionSelected = { source ->
                                vm.update(assistant.copy(chatTtsSource = source))
                            },
                            optionToString = {
                                when (it) {
                                    ChatTtsSource.SameAsVoiceCall -> "与语音通话声线相同"
                                    ChatTtsSource.Global -> "使用全局 TTS（设置 → 语音）"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "当前：$chatLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        onClick = {
                            // VoiceSelection edits current assistant — switch first
                            vm.setAsCurrentAssistant()
                            nav.navigate(Screen.VoiceSelection)
                        },
                        headlineContent = { Text("语音通话声线") },
                        supportingContent = {
                            Text("通话 / 「与通话相同」朗读 · 当前：${voiceDisplay.displayName}")
                        },
                        leadingContent = { Icon(HugeIcons.VolumeHigh, null) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingSpeech) },
                        headlineContent = { Text("全局 TTS / ASR 提供商") },
                        supportingContent = {
                            Text("管理 Mossland、系统 TTS 等引擎与 API Key · 当前选中：$globalTtsName")
                        },
                        leadingContent = { Icon(HugeIcons.VolumeHigh, null) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                }
            }

            item {
                FormItem(
                    label = { Text("说明") },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "• 默认「与语音通话声线相同」：在通话里配好 Moss 后，聊天小喇叭也会用同一套声线。\n" +
                            "• 若希望聊天用系统语音、通话用 Moss，请将聊天朗读改为「全局 TTS」，并在设置 → 语音里选中系统 TTS。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
