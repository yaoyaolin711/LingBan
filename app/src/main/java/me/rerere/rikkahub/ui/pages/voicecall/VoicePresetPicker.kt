package me.rerere.rikkahub.ui.pages.voicecall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.AssistantVoiceCallSettings
import me.rerere.rikkahub.data.model.VoiceCallTtsResolveResult
import me.rerere.rikkahub.data.model.VoicePresets
import me.rerere.rikkahub.data.model.VoiceTier
import me.rerere.rikkahub.data.model.VoiceTtsBackend
import me.rerere.rikkahub.data.model.customVoicesByBackend
import me.rerere.rikkahub.data.model.resolveVoiceCallTts
import me.rerere.rikkahub.data.model.withAssistantVoiceCall
import me.rerere.rikkahub.data.model.withVoiceCallApiKey
import me.rerere.rikkahub.data.model.withoutCustomVoiceProfile
import me.rerere.rikkahub.ui.components.ui.ChipScrollRow
import me.rerere.rikkahub.ui.components.ui.chipUnshrinkable
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.SolaceTheme
import me.rerere.tts.provider.TTSProviderSetting

@Composable
fun VoicePresetPicker(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onPreview: () -> Unit,
    onReadyToCall: () -> Unit = {},
    showActionButtons: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val nav = LocalNavController.current
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val assistant = settings.getCurrentAssistant()
    val voiceCall = assistant.voiceCall
    var selectedTier by remember(voiceCall.tier) { mutableStateOf(voiceCall.tier) }
    var apiKeyDraft by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var customBackendFilter by remember { mutableStateOf<VoiceTtsBackend?>(null) }

    fun selectTier(tier: VoiceTier) {
        selectedTier = tier
        when (tier) {
            VoiceTier.Custom -> {
                val first = settings.customVoices.firstOrNull()
                onUpdateSettings(
                    settings.withAssistantVoiceCall(
                        assistant.id,
                        AssistantVoiceCallSettings(
                            tier = VoiceTier.Custom,
                            presetId = voiceCall.presetId,
                            selectedCustomVoiceId = first?.id,
                        ),
                    )
                )
            }
            else -> {
                val first = VoicePresets.forTier(tier).firstOrNull() ?: return
                onUpdateSettings(
                    settings.withAssistantVoiceCall(
                        assistant.id,
                        AssistantVoiceCallSettings(
                            tier = tier,
                            presetId = first.id,
                            selectedCustomVoiceId = null,
                        ),
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "声线类型",
            style = typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
        )

        ChipScrollRow {
            TierChip("本地", VoiceTier.Local, selectedTier, ::selectTier)
            TierChip("预设声线", VoiceTier.Preset, selectedTier, ::selectTier)
            TierChip("自定义声线", VoiceTier.Custom, selectedTier, ::selectTier)
        }

        Text(
            text = when (selectedTier) {
                VoiceTier.Local -> "本地：系统 TTS，无需 Key，效果因手机而异。适合快速试用。"
                VoiceTier.Preset -> "预设：仅提供系统音色（如通义 Qwen），不可自定义声线。需要对应服务商 API Key。"
                VoiceTier.Custom -> "自定义：支持 MiniMax / ElevenLabs / Fish / Mossland / 火山引擎。点「添加声线」进入独立填写页。"
            },
            style = typography.bodySmall,
            color = colors.secondaryText,
        )

        when (selectedTier) {
            VoiceTier.Local, VoiceTier.Preset -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(VoicePresets.forTier(selectedTier), key = { it.id }) { preset ->
                        PresetCard(
                            title = preset.displayName,
                            subtitle = preset.description,
                            selected = voiceCall.presetId == preset.id,
                            onClick = {
                                onUpdateSettings(
                                    settings.withAssistantVoiceCall(
                                        assistant.id,
                                        AssistantVoiceCallSettings(
                                            tier = selectedTier,
                                            presetId = preset.id,
                                            selectedCustomVoiceId = null,
                                        ),
                                    )
                                )
                            },
                        )
                    }
                }
            }

            VoiceTier.Custom -> {
                ChipScrollRow {
                    FilterChip(
                        selected = customBackendFilter == null,
                        onClick = { customBackendFilter = null },
                        modifier = Modifier.chipUnshrinkable(),
                        label = { Text("全部", maxLines = 1, softWrap = false) },
                    )
                    VoicePresets.customCapableBackends().forEach { backend ->
                        FilterChip(
                            selected = customBackendFilter == backend,
                            onClick = { customBackendFilter = backend },
                            modifier = Modifier.chipUnshrinkable(),
                            label = { Text(backendLabelFor(backend), maxLines = 1, softWrap = false) },
                        )
                    }
                }

                val voices = settings.customVoicesByBackend(customBackendFilter)
                if (voices.isEmpty()) {
                    Text(
                        text = "还没有自定义声线。点「添加声线」进入填写页保存一套，之后可在这里切换。",
                        style = typography.bodySmall,
                        color = colors.secondaryText,
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(voices, key = { it.id.toString() }) { profile ->
                            PresetCard(
                                title = profile.name,
                                subtitle = backendLabelFor(profile.backend),
                                selected = voiceCall.selectedCustomVoiceId == profile.id,
                                onClick = {
                                    onUpdateSettings(
                                        settings.withAssistantVoiceCall(
                                            assistant.id,
                                            AssistantVoiceCallSettings(
                                                tier = VoiceTier.Custom,
                                                presetId = voiceCall.presetId,
                                                selectedCustomVoiceId = profile.id,
                                            ),
                                        )
                                    )
                                },
                                onLongClick = {
                                    nav.navigate(Screen.CustomVoiceEdit(profileId = profile.id.toString()))
                                },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { nav.navigate(Screen.CustomVoiceEdit()) },
                    ) {
                        Text("添加声线")
                    }
                    val selectedId = voiceCall.selectedCustomVoiceId
                    if (selectedId != null) {
                        TextButton(
                            onClick = {
                                nav.navigate(Screen.CustomVoiceEdit(profileId = selectedId.toString()))
                            },
                        ) {
                            Text("编辑当前")
                        }
                        TextButton(
                            onClick = {
                                val next = settings.withoutCustomVoiceProfile(selectedId)
                                val nextSelected = next.customVoices.firstOrNull()?.id
                                onUpdateSettings(
                                    next.withAssistantVoiceCall(
                                        assistant.id,
                                        AssistantVoiceCallSettings(
                                            tier = VoiceTier.Custom,
                                            presetId = voiceCall.presetId,
                                            selectedCustomVoiceId = nextSelected,
                                        ),
                                    )
                                )
                            },
                        ) {
                            Text("删除当前")
                        }
                    }
                }
            }
        }

        val resolve = resolveVoiceCallTts(settings, assistant.voiceCall)
        val keyBackend = keyBackendForResolve(settings, resolve)
        if (keyBackend != null && keyBackend != VoiceTtsBackend.System) {
            val existingKey = existingApiKeyFor(settings, keyBackend)
            Text(
                text = if (existingKey.isBlank()) {
                    if (keyBackend == VoiceTtsBackend.Volcengine) {
                        "接入 ${backendLabelFor(keyBackend)}（填写 AppID|AccessToken）"
                    } else {
                        "接入 ${backendLabelFor(keyBackend)}（填写 API Key）"
                    }
                } else {
                    "更换 ${backendLabelFor(keyBackend)} 凭证（已保存 ${maskApiKey(existingKey)}）"
                },
                style = typography.labelLarge,
                color = colors.text,
            )
            OutlinedTextField(
                value = apiKeyDraft,
                onValueChange = { apiKeyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        when {
                            keyBackend == VoiceTtsBackend.Volcengine -> "AppID|AccessToken"
                            existingKey.isBlank() -> "sk-..."
                            else -> "输入新 Key 以更换"
                        }
                    )
                },
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) HugeIcons.ViewOff else HugeIcons.View,
                            contentDescription = if (keyVisible) "隐藏" else "显示",
                        )
                    }
                },
            )
            TextButton(
                onClick = {
                    onUpdateSettings(settings.withVoiceCallApiKey(keyBackend, apiKeyDraft))
                    apiKeyDraft = ""
                },
                enabled = apiKeyDraft.isNotBlank(),
            ) {
                Text(if (existingKey.isBlank()) "保存 Key" else "更新 Key")
            }
        }

        when (resolve) {
            is VoiceCallTtsResolveResult.Ready -> {
                if (showActionButtons) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onPreview) { Text("试听") }
                        TextButton(onClick = onReadyToCall) { Text("开始通话") }
                    }
                }
            }
            is VoiceCallTtsResolveResult.Unavailable -> {
                Text(resolve.message, color = MaterialTheme.colorScheme.error)
            }
            is VoiceCallTtsResolveResult.NeedsApiKey -> Unit
        }
    }
}

private fun keyBackendForResolve(
    settings: Settings,
    resolve: VoiceCallTtsResolveResult,
): VoiceTtsBackend? {
    return when (resolve) {
        is VoiceCallTtsResolveResult.NeedsApiKey -> resolve.backend
        is VoiceCallTtsResolveResult.Ready -> {
            val assistant = settings.getCurrentAssistant()
            when (assistant.voiceCall.tier) {
                VoiceTier.Custom -> {
                    settings.customVoices
                        .find { it.id == assistant.voiceCall.selectedCustomVoiceId }
                        ?.backend
                }
                VoiceTier.Preset -> VoicePresets.resolve(assistant.voiceCall).backend
                VoiceTier.Local -> null
            }
        }
        is VoiceCallTtsResolveResult.Unavailable -> {
            val assistant = settings.getCurrentAssistant()
            if (assistant.voiceCall.tier == VoiceTier.Custom) {
                settings.customVoices
                    .find { it.id == assistant.voiceCall.selectedCustomVoiceId }
                    ?.backend
            } else null
        }
    }
}

private fun existingApiKeyFor(settings: Settings, backend: VoiceTtsBackend): String =
    when (backend) {
        VoiceTtsBackend.MiniMax ->
            settings.ttsProviders.filterIsInstance<TTSProviderSetting.MiniMax>().firstOrNull()?.apiKey.orEmpty()
        VoiceTtsBackend.ElevenLabs ->
            settings.ttsProviders.filterIsInstance<TTSProviderSetting.ElevenLabs>().firstOrNull()?.apiKey.orEmpty()
        VoiceTtsBackend.FishAudio ->
            settings.ttsProviders.filterIsInstance<TTSProviderSetting.FishAudio>().firstOrNull()?.apiKey.orEmpty()
        VoiceTtsBackend.Mossland ->
            settings.ttsProviders.filterIsInstance<TTSProviderSetting.Mossland>().firstOrNull()?.apiKey.orEmpty()
        VoiceTtsBackend.Volcengine -> {
            val v = settings.ttsProviders.filterIsInstance<TTSProviderSetting.Volcengine>().firstOrNull()
            when {
                v == null -> ""
                v.appId.isNotBlank() && v.accessToken.isNotBlank() -> "${v.appId}|${v.accessToken}"
                else -> v.accessToken
            }
        }
        VoiceTtsBackend.Qwen ->
            settings.ttsProviders.filterIsInstance<TTSProviderSetting.Qwen>().firstOrNull()?.apiKey.orEmpty()
        VoiceTtsBackend.System -> ""
    }

private fun maskApiKey(key: String): String {
    if (key.length <= 8) return "****"
    return key.take(4) + "****" + key.takeLast(4)
}

@Composable
private fun TierChip(
    label: String,
    tier: VoiceTier,
    selected: VoiceTier,
    onSelect: (VoiceTier) -> Unit,
) {
    FilterChip(
        selected = selected == tier,
        onClick = { onSelect(tier) },
        modifier = Modifier.chipUnshrinkable(),
        label = { Text(label, maxLines = 1, softWrap = false) },
    )
}

@Composable
private fun PresetCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = SolaceTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .size(width = 132.dp, height = 96.dp)
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.18f) else colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primary else colors.outline.copy(alpha = 0.4f),
                shape = shape,
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = SolaceTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = colors.text,
            maxLines = 2,
        )
        Text(
            text = subtitle,
            style = SolaceTheme.typography.labelSmall,
            color = colors.secondaryText,
            maxLines = 2,
        )
    }
}
