package me.rerere.rikkahub.ui.pages.voicecall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.AssistantVoiceCallSettings
import me.rerere.rikkahub.data.model.CustomVoiceProfile
import me.rerere.rikkahub.data.model.VoicePresets
import me.rerere.rikkahub.data.model.VoiceTier
import me.rerere.rikkahub.data.model.VoiceTtsBackend
import me.rerere.rikkahub.data.model.withAssistantVoiceCall
import me.rerere.rikkahub.data.model.withCustomVoiceProfile
import me.rerere.rikkahub.data.model.withVoiceCallApiKey
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ChipScrollRow
import me.rerere.rikkahub.ui.components.ui.GlassCard
import me.rerere.rikkahub.ui.components.ui.chipUnshrinkable
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.SolaceTheme
import me.rerere.rikkahub.utils.plus
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun CustomVoiceEditPage(
    profileId: String = "",
    vm: SettingVM = koinViewModel(),
) {
    val nav = LocalNavController.current
    val toaster = LocalToaster.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assistant = settings.getCurrentAssistant()
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val initial = remember(profileId, settings.customVoices) {
        profileId.takeIf { it.isNotBlank() }
            ?.let { id -> runCatching { Uuid.parse(id) }.getOrNull() }
            ?.let { id -> settings.customVoices.find { it.id == id } }
    }
    val templates = remember { VoicePresets.customTemplates() }

    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var backend by remember(initial) {
        mutableStateOf(initial?.backend ?: VoiceTtsBackend.Mossland)
    }
    var voiceId by remember(initial) { mutableStateOf(initial?.voiceId.orEmpty()) }
    var model by remember(initial) {
        mutableStateOf(initial?.model?.ifBlank { defaultModelForBackend(backend) }.orEmpty())
    }
    var apiKeyDraft by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    val existingKey = remember(settings.ttsProviders, backend) {
        existingApiKey(settings.ttsProviders, backend)
    }
    val hasExistingKey = existingKey.isNotBlank()

    fun save() {
        val trimmedName = name.trim()
        val trimmedVoiceId = voiceId.trim()
        if (trimmedName.isBlank() || trimmedVoiceId.isBlank()) {
            toaster.show("请填写声线名称和音色 ID", type = ToastType.Error)
            return
        }
        val newKey = apiKeyDraft.trim()
        if (!hasExistingKey && newKey.isBlank()) {
            toaster.show("请填写 ${backendLabelFor(backend)} 的 API Key", type = ToastType.Error)
            return
        }

        val profile = CustomVoiceProfile(
            id = initial?.id ?: Uuid.random(),
            name = trimmedName,
            backend = backend,
            voiceId = trimmedVoiceId,
            model = model.trim().ifBlank { defaultModelForBackend(backend) },
        )
        var next = settings.withCustomVoiceProfile(profile)
        if (newKey.isNotBlank()) {
            next = next.withVoiceCallApiKey(backend, newKey)
        }
        next = next.withAssistantVoiceCall(
            assistant.id,
            AssistantVoiceCallSettings(
                tier = VoiceTier.Custom,
                presetId = assistant.voiceCall.presetId,
                selectedCustomVoiceId = profile.id,
            ),
        )
        vm.updateSettings(next)
        toaster.show("已保存自定义声线")
        nav.popBackStack()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (initial == null) "添加自定义声线" else "编辑自定义声线") },
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
                        text = "填写说明",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (backend) {
                            VoiceTtsBackend.Mossland ->
                                "1. 在 studio.mosi.cn 创建 API Key\n" +
                                    "2. 用 POST /v1/audio/voices 创建音色，复制 voice_id\n" +
                                    "3. 模型填 moss-tts；请求发往 api.mosi.cn\n" +
                                    "4. API Key 可随时在本页更换"
                            else ->
                                "填写服务商音色 ID，并配置对应 API Key。Key 可随时更换。"
                        },
                        style = typography.bodySmall,
                        color = colors.secondaryText,
                    )
                }
            }

            item("form") {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("声线名称") },
                            placeholder = { Text("例如：测试 / 妈妈的声音") },
                        )

                        Text("服务商", style = typography.labelLarge, color = colors.text)
                        ChipScrollRow {
                            VoicePresets.customCapableBackends().forEach { b ->
                                FilterChip(
                                    selected = backend == b,
                                    onClick = {
                                        backend = b
                                        if (model.isBlank() ||
                                            model == defaultModelForBackend(VoiceTtsBackend.MiniMax) ||
                                            model == defaultModelForBackend(VoiceTtsBackend.ElevenLabs) ||
                                            model == defaultModelForBackend(VoiceTtsBackend.FishAudio) ||
                                            model == defaultModelForBackend(VoiceTtsBackend.Mossland)
                                        ) {
                                            model = defaultModelForBackend(b)
                                        }
                                    },
                                    modifier = Modifier.chipUnshrinkable(),
                                    label = { Text(backendLabelFor(b), maxLines = 1, softWrap = false) },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = voiceId,
                            onValueChange = { voiceId = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(voiceIdLabelFor(backend)) },
                            placeholder = { Text(voiceIdHintFor(backend)) },
                            supportingText = {
                                Text("请粘贴完整 ID，不要截断")
                            },
                        )

                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("模型") },
                            placeholder = { Text(defaultModelForBackend(backend)) },
                            supportingText = {
                                if (backend == VoiceTtsBackend.Mossland) {
                                    Text("必须填官方 ID：moss-tts（不要填 MOSS-TTS-v1.5-Flash）")
                                }
                            },
                        )

                        Text("API Key", style = typography.labelLarge, color = colors.text)
                        Text(
                            text = if (hasExistingKey) {
                                "已保存 Key（${maskKey(existingKey)}）。留空保留原 Key，输入新值即可更换。"
                            } else {
                                "尚未配置 Key，请填写后保存。"
                            },
                            style = typography.bodySmall,
                            color = colors.secondaryText,
                        )
                        OutlinedTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(if (hasExistingKey) "更换 API Key" else "API Key") },
                            placeholder = { Text(if (hasExistingKey) "输入新 Key 以更换" else "sk-...") },
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

                        if (templates.any { it.backend == backend && it.voiceId.isNotBlank() }) {
                            Text("快速填入模板音色", style = typography.labelMedium, color = colors.text)
                            ChipScrollRow {
                                templates.filter { it.backend == backend && it.voiceId.isNotBlank() }.forEach { t ->
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
                }
            }

            item("actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { save() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = name.isNotBlank() && voiceId.isNotBlank(),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

private fun existingApiKey(
    providers: List<TTSProviderSetting>,
    backend: VoiceTtsBackend,
): String = when (backend) {
    VoiceTtsBackend.MiniMax ->
        providers.filterIsInstance<TTSProviderSetting.MiniMax>().firstOrNull()?.apiKey.orEmpty()
    VoiceTtsBackend.ElevenLabs ->
        providers.filterIsInstance<TTSProviderSetting.ElevenLabs>().firstOrNull()?.apiKey.orEmpty()
    VoiceTtsBackend.FishAudio ->
        providers.filterIsInstance<TTSProviderSetting.FishAudio>().firstOrNull()?.apiKey.orEmpty()
    VoiceTtsBackend.Mossland ->
        providers.filterIsInstance<TTSProviderSetting.Mossland>().firstOrNull()?.apiKey.orEmpty()
    VoiceTtsBackend.Qwen ->
        providers.filterIsInstance<TTSProviderSetting.Qwen>().firstOrNull()?.apiKey.orEmpty()
    VoiceTtsBackend.System -> ""
}

private fun maskKey(key: String): String {
    if (key.length <= 8) return "****"
    return key.take(4) + "****" + key.takeLast(4)
}

internal fun backendLabelFor(backend: VoiceTtsBackend): String = when (backend) {
    VoiceTtsBackend.MiniMax -> "MiniMax"
    VoiceTtsBackend.ElevenLabs -> "ElevenLabs"
    VoiceTtsBackend.FishAudio -> "Fish"
    VoiceTtsBackend.Mossland -> "Mossland"
    VoiceTtsBackend.Qwen -> "Qwen"
    VoiceTtsBackend.System -> "系统"
}

internal fun voiceIdLabelFor(backend: VoiceTtsBackend): String = when (backend) {
    VoiceTtsBackend.MiniMax -> "voice_id"
    VoiceTtsBackend.ElevenLabs -> "voiceId"
    VoiceTtsBackend.FishAudio -> "reference_id"
    VoiceTtsBackend.Mossland -> "voice_id"
    else -> "音色 ID"
}

internal fun voiceIdHintFor(backend: VoiceTtsBackend): String = when (backend) {
    VoiceTtsBackend.MiniMax -> "官方或复刻音色 ID"
    VoiceTtsBackend.ElevenLabs -> "账号里的自定义音色 ID"
    VoiceTtsBackend.FishAudio -> "Fish Audio reference_id"
    VoiceTtsBackend.Mossland -> "例如 dee4a231-c908-4837-821d-c35fde882db0"
    else -> "音色 ID"
}

internal fun defaultModelForBackend(backend: VoiceTtsBackend): String = when (backend) {
    VoiceTtsBackend.MiniMax -> "speech-2.6-turbo"
    VoiceTtsBackend.ElevenLabs -> "eleven_multilingual_v2"
    VoiceTtsBackend.FishAudio -> "s2.1-pro"
    VoiceTtsBackend.Mossland -> "moss-tts"
    VoiceTtsBackend.Qwen -> "qwen3-tts-flash"
    VoiceTtsBackend.System -> ""
}
