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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.AssistantVoiceCallSettings
import me.rerere.rikkahub.data.model.CustomVoiceProfile
import me.rerere.rikkahub.data.model.VoiceCallTtsResolveResult
import me.rerere.rikkahub.data.model.VoicePreset
import me.rerere.rikkahub.data.model.VoicePresets
import me.rerere.rikkahub.data.model.VoiceTier
import me.rerere.rikkahub.data.model.VoiceTtsBackend
import me.rerere.rikkahub.data.model.customVoicesByBackend
import me.rerere.rikkahub.data.model.resolveVoiceCallTts
import me.rerere.rikkahub.data.model.withAssistantVoiceCall
import me.rerere.rikkahub.data.model.withCustomVoiceProfile
import me.rerere.rikkahub.data.model.withVoiceCallApiKey
import me.rerere.rikkahub.data.model.withoutCustomVoiceProfile
import me.rerere.rikkahub.ui.components.ui.ChipScrollRow
import me.rerere.rikkahub.ui.components.ui.chipUnshrinkable
import me.rerere.rikkahub.ui.theme.SolaceTheme
import kotlin.uuid.Uuid

@Composable
fun VoicePresetPicker(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onPreview: () -> Unit,
    onReadyToCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val assistant = settings.getCurrentAssistant()
    val voiceCall = assistant.voiceCall
    var selectedTier by remember(voiceCall.tier) { mutableStateOf(voiceCall.tier) }
    var apiKeyDraft by remember { mutableStateOf("") }
    var customBackendFilter by remember { mutableStateOf<VoiceTtsBackend?>(null) }
    var editingProfile by remember { mutableStateOf<CustomVoiceProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

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
                VoiceTier.Local -> "本地：系统 TTS，无需 Key，效果因手机而异"
                VoiceTier.Preset -> "预设：仅提供系统音色的模型（如通义 Qwen），不可自定义声线"
                VoiceTier.Custom -> "自定义：按服务分类保存多套声线，随时切换当前使用的那一套"
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
                            label = { Text(backendLabel(backend), maxLines = 1, softWrap = false) },
                        )
                    }
                }

                val voices = settings.customVoicesByBackend(customBackendFilter)
                if (voices.isEmpty()) {
                    Text(
                        text = "还没有自定义声线。点「添加声线」保存一套，之后可在这里切换。",
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
                                subtitle = backendLabel(profile.backend),
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
                                onLongClick = { editingProfile = profile },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showCreateDialog = true }) {
                        Text("添加声线")
                    }
                    val selectedId = voiceCall.selectedCustomVoiceId
                    if (selectedId != null) {
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

        when (val r = resolveVoiceCallTts(settings, assistant.voiceCall)) {
            is VoiceCallTtsResolveResult.NeedsApiKey -> {
                Text(
                    text = "接入 ${r.providerLabel}（粘贴 API Key）",
                    style = typography.labelLarge,
                    color = colors.text,
                )
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("sk-...") },
                )
                TextButton(
                    onClick = {
                        onUpdateSettings(settings.withVoiceCallApiKey(r.backend, apiKeyDraft))
                        apiKeyDraft = ""
                    },
                    enabled = apiKeyDraft.isNotBlank(),
                ) {
                    Text("保存 Key")
                }
            }
            is VoiceCallTtsResolveResult.Ready -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPreview) { Text("试听") }
                    TextButton(onClick = onReadyToCall) { Text("开始通话") }
                }
            }
            is VoiceCallTtsResolveResult.Unavailable -> {
                Text(r.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showCreateDialog) {
        CustomVoiceEditorDialog(
            initial = null,
            templates = VoicePresets.customTemplates(),
            onDismiss = { showCreateDialog = false },
            onSave = { profile ->
                val next = settings.withCustomVoiceProfile(profile)
                onUpdateSettings(
                    next.withAssistantVoiceCall(
                        assistant.id,
                        AssistantVoiceCallSettings(
                            tier = VoiceTier.Custom,
                            presetId = voiceCall.presetId,
                            selectedCustomVoiceId = profile.id,
                        ),
                    )
                )
                showCreateDialog = false
            },
        )
    }

    editingProfile?.let { profile ->
        CustomVoiceEditorDialog(
            initial = profile,
            templates = VoicePresets.customTemplates(),
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                onUpdateSettings(settings.withCustomVoiceProfile(updated))
                editingProfile = null
            },
        )
    }
}

@Composable
private fun CustomVoiceEditorDialog(
    initial: CustomVoiceProfile?,
    templates: List<VoicePreset>,
    onDismiss: () -> Unit,
    onSave: (CustomVoiceProfile) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var backend by remember(initial) {
        mutableStateOf(initial?.backend ?: VoiceTtsBackend.MiniMax)
    }
    var voiceId by remember(initial) { mutableStateOf(initial?.voiceId.orEmpty()) }
    var model by remember(initial) { mutableStateOf(initial?.model.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加自定义声线" else "编辑声线") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("声线名称") },
                    placeholder = { Text("例如：妈妈的声音") },
                )
                Text("服务商", style = MaterialTheme.typography.labelMedium)
                ChipScrollRow {
                    VoicePresets.customCapableBackends().forEach { b ->
                        FilterChip(
                            selected = backend == b,
                            onClick = {
                                backend = b
                                val template = templates.firstOrNull { it.backend == b }
                                if (model.isBlank()) model = template?.model.orEmpty()
                            },
                            modifier = Modifier.chipUnshrinkable(),
                            label = { Text(backendLabel(b), maxLines = 1, softWrap = false) },
                        )
                    }
                }
                OutlinedTextField(
                    value = voiceId,
                    onValueChange = { voiceId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(voiceIdLabel(backend)) },
                    placeholder = { Text(voiceIdHint(backend)) },
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("模型（可选）") },
                    placeholder = {
                        Text(
                            templates.firstOrNull { it.backend == backend }?.model
                                ?: defaultModel(backend)
                        )
                    },
                )
                if (templates.any { it.backend == backend && it.voiceId.isNotBlank() }) {
                    Text("快速填入模板音色", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(templates.filter { it.backend == backend && it.voiceId.isNotBlank() }) { t ->
                            FilterChip(
                                selected = voiceId == t.voiceId,
                                onClick = {
                                    if (name.isBlank()) name = t.displayName
                                    voiceId = t.voiceId
                                    model = t.model
                                },
                                modifier = Modifier.chipUnshrinkable(),
                                label = { Text(t.displayName, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && voiceId.isNotBlank(),
                onClick = {
                    onSave(
                        CustomVoiceProfile(
                            id = initial?.id ?: Uuid.random(),
                            name = name.trim(),
                            backend = backend,
                            voiceId = voiceId.trim(),
                            model = model.trim().ifBlank { defaultModel(backend) },
                        )
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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

private fun backendLabel(backend: VoiceTtsBackend): String = when (backend) {
    VoiceTtsBackend.MiniMax -> "MiniMax"
    VoiceTtsBackend.ElevenLabs -> "ElevenLabs"
    VoiceTtsBackend.FishAudio -> "Fish"
    VoiceTtsBackend.Qwen -> "Qwen"
    VoiceTtsBackend.System -> "系统"
}

private fun voiceIdLabel(backend: VoiceTtsBackend): String = when (backend) {
    VoiceTtsBackend.MiniMax -> "voice_id"
    VoiceTtsBackend.ElevenLabs -> "voiceId"
    VoiceTtsBackend.FishAudio -> "reference_id"
    else -> "音色 ID"
}

private fun voiceIdHint(backend: VoiceTtsBackend): String = when (backend) {
    VoiceTtsBackend.MiniMax -> "官方或复刻音色 ID"
    VoiceTtsBackend.ElevenLabs -> "账号里的自定义音色 ID"
    VoiceTtsBackend.FishAudio -> "Fish Audio reference_id"
    else -> "音色 ID"
}

private fun defaultModel(backend: VoiceTtsBackend): String = when (backend) {
    VoiceTtsBackend.MiniMax -> "speech-2.6-turbo"
    VoiceTtsBackend.ElevenLabs -> "eleven_multilingual_v2"
    VoiceTtsBackend.FishAudio -> "s2.1-pro"
    VoiceTtsBackend.Qwen -> "qwen3-tts-flash"
    VoiceTtsBackend.System -> ""
}
