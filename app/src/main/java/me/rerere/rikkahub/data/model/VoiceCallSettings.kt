package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

/**
 * Voice-call picker categories.
 *
 * - [Local]: on-device system TTS (no API key)
 * - [Preset]: cloud TTS with fixed system timbres only (no custom / clone voice)
 * - [Custom]: cloud TTS that accepts user voice IDs / reference IDs / cloning
 */
@Serializable
enum class VoiceTier {
    @SerialName("local")
    @JsonNames("free")
    Local,

    @SerialName("preset")
    @JsonNames("standard")
    Preset,

    @SerialName("custom")
    @JsonNames("premium")
    Custom,
}

@Serializable
enum class VoiceTtsBackend {
    @SerialName("system")
    System,

    /** Fixed system voices only (Qwen TTS). */
    @SerialName("qwen")
    Qwen,

    /** Supports custom voice_id / cloning. */
    @SerialName("minimax")
    MiniMax,

    /** Supports custom voice IDs / cloning. */
    @SerialName("elevenlabs")
    ElevenLabs,

    /** Supports custom reference_id (cloned / library voices). */
    @SerialName("fish-audio")
    FishAudio,
}

@Serializable
data class CustomVoiceProfile(
    val id: Uuid = Uuid.random(),
    /** User-facing name, e.g. "妈妈的声音" */
    val name: String,
    /** Must be a custom-capable backend: MiniMax / ElevenLabs / FishAudio */
    val backend: VoiceTtsBackend,
    /** MiniMax voice_id / ElevenLabs voiceId / Fish reference_id */
    val voiceId: String,
    val model: String = "",
)

@Serializable
data class AssistantVoiceCallSettings(
    val tier: VoiceTier = VoiceTier.Local,
    /** Used for Local / Preset tiers (and as fallback). */
    val presetId: String = VoicePresets.DEFAULT_PRESET_ID,
    /** Selected entry from [Settings.customVoices] when [tier] is [VoiceTier.Custom]. */
    val selectedCustomVoiceId: Uuid? = null,
)

data class VoicePreset(
    val id: String,
    val tier: VoiceTier,
    val displayName: String,
    val description: String,
    val backend: VoiceTtsBackend,
    /** Provider-specific voice / voiceId / referenceId */
    val voiceId: String = "",
    val model: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val requiresApiKey: Boolean = false,
    val sampleText: String = "你好，我是你的 AI 伴侣，很高兴能和你通话。",
    /** Whether this preset's backend accepts a user-supplied custom voice id. */
    val supportsCustomVoice: Boolean = false,
)

sealed class VoiceCallTtsResolveResult {
    data class Ready(val provider: TTSProviderSetting) : VoiceCallTtsResolveResult()
    data class NeedsApiKey(
        val backend: VoiceTtsBackend,
        val providerLabel: String,
        val preset: VoicePreset,
    ) : VoiceCallTtsResolveResult()
    data class Unavailable(val message: String) : VoiceCallTtsResolveResult()
}

object VoicePresets {
    const val DEFAULT_PRESET_ID = "free_system_soft"

    /**
     * Classification (based on project TTS integrations):
     * - Local: System TTS
     * - Preset (no custom voice): Qwen
     * - Custom (supports voice id / reference / clone): MiniMax, ElevenLabs, Fish Audio
     */
    val all: List<VoicePreset> = listOf(
        // —— Local ——
        VoicePreset(
            id = "free_system_soft",
            tier = VoiceTier.Local,
            displayName = "系统柔和",
            description = "本地 · 系统引擎，略慢语速",
            backend = VoiceTtsBackend.System,
            speechRate = 0.95f,
            pitch = 1.05f,
            requiresApiKey = false,
        ),
        VoicePreset(
            id = "free_system_bright",
            tier = VoiceTier.Local,
            displayName = "系统清亮",
            description = "本地 · 系统引擎，稍高音调",
            backend = VoiceTtsBackend.System,
            speechRate = 1.05f,
            pitch = 1.15f,
            requiresApiKey = false,
        ),
        VoicePreset(
            id = "free_system_calm",
            tier = VoiceTier.Local,
            displayName = "系统沉稳",
            description = "本地 · 系统引擎，偏低音调",
            backend = VoiceTtsBackend.System,
            speechRate = 0.9f,
            pitch = 0.9f,
            requiresApiKey = false,
        ),

        // —— Preset (fixed system timbres only) ——
        VoicePreset(
            id = "std_qwen_cherry",
            tier = VoiceTier.Preset,
            displayName = "Cherry 温柔",
            description = "预设 · 通义 Qwen 系统音色",
            backend = VoiceTtsBackend.Qwen,
            voiceId = "Cherry",
            model = "qwen3-tts-flash",
            requiresApiKey = true,
        ),
        VoicePreset(
            id = "std_qwen_serena",
            tier = VoiceTier.Preset,
            displayName = "Serena 知性",
            description = "预设 · 通义 Qwen 系统音色",
            backend = VoiceTtsBackend.Qwen,
            voiceId = "Serena",
            model = "qwen3-tts-flash",
            requiresApiKey = true,
        ),
        VoicePreset(
            id = "std_qwen_ethan",
            tier = VoiceTier.Preset,
            displayName = "Ethan 清晰",
            description = "预设 · 通义 Qwen 系统音色",
            backend = VoiceTtsBackend.Qwen,
            voiceId = "Ethan",
            model = "qwen3-tts-flash",
            requiresApiKey = true,
        ),
        VoicePreset(
            id = "std_qwen_chelsie",
            tier = VoiceTier.Preset,
            displayName = "Chelsie 活泼",
            description = "预设 · 通义 Qwen 系统音色",
            backend = VoiceTtsBackend.Qwen,
            voiceId = "Chelsie",
            model = "qwen3-tts-flash",
            requiresApiKey = true,
        ),

        // —— Custom (backends that accept custom voice ids) ——
        VoicePreset(
            id = "std_minimax_shaonv",
            tier = VoiceTier.Custom,
            displayName = "少女音",
            description = "自定义 · MiniMax，可改 voice_id",
            backend = VoiceTtsBackend.MiniMax,
            voiceId = "female-shaonv",
            model = "speech-2.6-turbo",
            requiresApiKey = true,
            supportsCustomVoice = true,
        ),
        VoicePreset(
            id = "std_minimax_yujie",
            tier = VoiceTier.Custom,
            displayName = "御姐音",
            description = "自定义 · MiniMax，可改 voice_id",
            backend = VoiceTtsBackend.MiniMax,
            voiceId = "female-yujie",
            model = "speech-2.6-turbo",
            requiresApiKey = true,
            supportsCustomVoice = true,
        ),
        VoicePreset(
            id = "std_minimax_chengshu",
            tier = VoiceTier.Custom,
            displayName = "沉稳男声",
            description = "自定义 · MiniMax，可改 voice_id",
            backend = VoiceTtsBackend.MiniMax,
            voiceId = "male-qn-qingse",
            model = "speech-2.6-turbo",
            requiresApiKey = true,
            supportsCustomVoice = true,
        ),
        VoicePreset(
            id = "prem_minimax_audiobook",
            tier = VoiceTier.Custom,
            displayName = "有声书女声",
            description = "自定义 · MiniMax HD",
            backend = VoiceTtsBackend.MiniMax,
            voiceId = "Chinese (Mandarin)_Gentle_Senior",
            model = "speech-2.6-hd",
            requiresApiKey = true,
            supportsCustomVoice = true,
        ),
        VoicePreset(
            id = "prem_minimax_warm",
            tier = VoiceTier.Custom,
            displayName = "温暖陪伴",
            description = "自定义 · MiniMax HD",
            backend = VoiceTtsBackend.MiniMax,
            voiceId = "Chinese (Mandarin)_Warm_Girl",
            model = "speech-2.6-hd",
            requiresApiKey = true,
            supportsCustomVoice = true,
        ),
        VoicePreset(
            id = "prem_eleven_rachel",
            tier = VoiceTier.Custom,
            displayName = "Rachel 细腻",
            description = "自定义 · ElevenLabs，可改 voiceId",
            backend = VoiceTtsBackend.ElevenLabs,
            voiceId = "21m00Tcm4TlvDq8ikWAM",
            model = "eleven_multilingual_v2",
            requiresApiKey = true,
            supportsCustomVoice = true,
        ),
        VoicePreset(
            id = "prem_eleven_adam",
            tier = VoiceTier.Custom,
            displayName = "Adam 沉稳",
            description = "自定义 · ElevenLabs，可改 voiceId",
            backend = VoiceTtsBackend.ElevenLabs,
            voiceId = "pNInz6obpgDQGcFmaJgB",
            model = "eleven_multilingual_v2",
            requiresApiKey = true,
            supportsCustomVoice = true,
        ),
        VoicePreset(
            id = "custom_fish_reference",
            tier = VoiceTier.Custom,
            displayName = "Fish 自定义",
            description = "自定义 · 填写 Fish Audio reference_id",
            backend = VoiceTtsBackend.FishAudio,
            voiceId = "",
            model = "s2.1-pro",
            requiresApiKey = true,
            supportsCustomVoice = true,
        ),
    )

    fun forTier(tier: VoiceTier): List<VoicePreset> = all.filter { it.tier == tier }

    fun find(id: String): VoicePreset? = all.find { it.id == id }

    fun resolve(settings: AssistantVoiceCallSettings): VoicePreset {
        val byId = find(settings.presetId)
        if (byId != null) return byId
        return forTier(settings.tier).firstOrNull()
            ?: find(DEFAULT_PRESET_ID)!!
    }

    /** Built-in starters that help create a [CustomVoiceProfile]. */
    fun customTemplates(): List<VoicePreset> = forTier(VoiceTier.Custom)

    fun customCapableBackends(): List<VoiceTtsBackend> = listOf(
        VoiceTtsBackend.MiniMax,
        VoiceTtsBackend.ElevenLabs,
        VoiceTtsBackend.FishAudio,
    )
}

data class VoiceCallDisplay(
    val displayName: String,
    val sampleText: String,
    val requiresApiKey: Boolean,
)

fun resolveVoiceCallDisplay(
    settings: Settings,
    voiceCall: AssistantVoiceCallSettings,
): VoiceCallDisplay {
    if (voiceCall.tier == VoiceTier.Custom) {
        val profile = settings.customVoices.find { it.id == voiceCall.selectedCustomVoiceId }
        if (profile != null) {
            return VoiceCallDisplay(
                displayName = profile.name,
                sampleText = "你好，我是你的 AI 伴侣，现在用的是「${profile.name}」这套声线。",
                requiresApiKey = true,
            )
        }
        return VoiceCallDisplay(
            displayName = "未选择自定义声线",
            sampleText = "你好，我是你的 AI 伴侣，很高兴能和你通话。",
            requiresApiKey = true,
        )
    }
    val preset = VoicePresets.resolve(voiceCall)
    return VoiceCallDisplay(
        displayName = preset.displayName,
        sampleText = preset.sampleText,
        requiresApiKey = preset.requiresApiKey,
    )
}

fun resolveVoiceCallTts(
    settings: Settings,
    voiceCall: AssistantVoiceCallSettings,
): VoiceCallTtsResolveResult {
    if (voiceCall.tier == VoiceTier.Custom) {
        val profile = settings.customVoices.find { it.id == voiceCall.selectedCustomVoiceId }
            ?: return VoiceCallTtsResolveResult.Unavailable("请先在「自定义声线」里添加并选择一套声线")
        return resolveCustomVoiceProfile(settings, profile)
    }

    val preset = VoicePresets.resolve(voiceCall)
    return resolvePresetVoice(settings, preset, preset.voiceId)
}

private fun resolveCustomVoiceProfile(
    settings: Settings,
    profile: CustomVoiceProfile,
): VoiceCallTtsResolveResult {
    val synthetic = VoicePreset(
        id = "custom:${profile.id}",
        tier = VoiceTier.Custom,
        displayName = profile.name,
        description = profile.backend.name,
        backend = profile.backend,
        voiceId = profile.voiceId,
        model = profile.model,
        requiresApiKey = true,
        supportsCustomVoice = true,
    )
    return resolvePresetVoice(settings, synthetic, profile.voiceId.trim())
}

private fun resolvePresetVoice(
    settings: Settings,
    preset: VoicePreset,
    voiceId: String,
): VoiceCallTtsResolveResult {
    return when (preset.backend) {
        VoiceTtsBackend.System -> {
            VoiceCallTtsResolveResult.Ready(
                TTSProviderSetting.SystemTTS(
                    id = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200"),
                    name = preset.displayName,
                    speechRate = preset.speechRate,
                    pitch = preset.pitch,
                )
            )
        }

        VoiceTtsBackend.Qwen -> {
            val existing = settings.ttsProviders.filterIsInstance<TTSProviderSetting.Qwen>().firstOrNull()
            val apiKey = existing?.apiKey.orEmpty()
            if (apiKey.isBlank()) {
                VoiceCallTtsResolveResult.NeedsApiKey(VoiceTtsBackend.Qwen, "Qwen / DashScope", preset)
            } else {
                VoiceCallTtsResolveResult.Ready(
                    (existing ?: TTSProviderSetting.Qwen()).copy(
                        name = "VoiceCall · ${preset.displayName}",
                        apiKey = apiKey,
                        model = preset.model.ifBlank { "qwen3-tts-flash" },
                        voice = voiceId.ifBlank { preset.voiceId },
                    )
                )
            }
        }

        VoiceTtsBackend.MiniMax -> {
            val existing = settings.ttsProviders.filterIsInstance<TTSProviderSetting.MiniMax>().firstOrNull()
            val apiKey = existing?.apiKey.orEmpty()
            if (apiKey.isBlank()) {
                VoiceCallTtsResolveResult.NeedsApiKey(VoiceTtsBackend.MiniMax, "MiniMax", preset)
            } else if (voiceId.isBlank()) {
                VoiceCallTtsResolveResult.Unavailable("请填写 MiniMax 自定义 voice_id")
            } else {
                VoiceCallTtsResolveResult.Ready(
                    (existing ?: TTSProviderSetting.MiniMax()).copy(
                        name = "VoiceCall · ${preset.displayName}",
                        apiKey = apiKey,
                        model = preset.model.ifBlank { "speech-2.6-turbo" },
                        voiceId = voiceId,
                    )
                )
            }
        }

        VoiceTtsBackend.ElevenLabs -> {
            val existing = settings.ttsProviders.filterIsInstance<TTSProviderSetting.ElevenLabs>().firstOrNull()
            val apiKey = existing?.apiKey.orEmpty()
            if (apiKey.isBlank()) {
                VoiceCallTtsResolveResult.NeedsApiKey(VoiceTtsBackend.ElevenLabs, "ElevenLabs", preset)
            } else if (voiceId.isBlank()) {
                VoiceCallTtsResolveResult.Unavailable("请填写 ElevenLabs 自定义 voiceId")
            } else {
                VoiceCallTtsResolveResult.Ready(
                    (existing ?: TTSProviderSetting.ElevenLabs()).copy(
                        name = "VoiceCall · ${preset.displayName}",
                        apiKey = apiKey,
                        model = preset.model.ifBlank { "eleven_multilingual_v2" },
                        voiceId = voiceId,
                    )
                )
            }
        }

        VoiceTtsBackend.FishAudio -> {
            val existing = settings.ttsProviders.filterIsInstance<TTSProviderSetting.FishAudio>().firstOrNull()
            val apiKey = existing?.apiKey.orEmpty()
            if (apiKey.isBlank()) {
                VoiceCallTtsResolveResult.NeedsApiKey(VoiceTtsBackend.FishAudio, "Fish Audio", preset)
            } else if (voiceId.isBlank()) {
                VoiceCallTtsResolveResult.Unavailable("请填写 Fish Audio 的 reference_id")
            } else {
                VoiceCallTtsResolveResult.Ready(
                    (existing ?: TTSProviderSetting.FishAudio()).copy(
                        name = "VoiceCall · ${preset.displayName}",
                        apiKey = apiKey,
                        model = preset.model.ifBlank { "s2.1-pro" },
                        referenceId = voiceId,
                    )
                )
            }
        }
    }
}

fun Settings.withCustomVoiceProfile(profile: CustomVoiceProfile): Settings {
    val existing = customVoices.indexOfFirst { it.id == profile.id }
    val list = customVoices.toMutableList()
    if (existing >= 0) list[existing] = profile else list.add(profile)
    return copy(customVoices = list)
}

fun Settings.withoutCustomVoiceProfile(id: Uuid): Settings {
    return copy(customVoices = customVoices.filterNot { it.id == id })
}

fun Settings.customVoicesByBackend(backend: VoiceTtsBackend? = null): List<CustomVoiceProfile> {
    return if (backend == null) customVoices
    else customVoices.filter { it.backend == backend }
}

/**
 * Upsert API key into Settings.ttsProviders for the given backend.
 * Creates a provider entry if missing.
 */
fun Settings.withVoiceCallApiKey(backend: VoiceTtsBackend, apiKey: String): Settings {
    val key = apiKey.trim()
    if (key.isEmpty()) return this

    val providers = ttsProviders.toMutableList()
    when (backend) {
        VoiceTtsBackend.System -> return this
        VoiceTtsBackend.Qwen -> {
            val idx = providers.indexOfFirst { it is TTSProviderSetting.Qwen }
            if (idx >= 0) {
                providers[idx] = (providers[idx] as TTSProviderSetting.Qwen).copy(apiKey = key)
            } else {
                providers.add(TTSProviderSetting.Qwen(apiKey = key, name = "Qwen TTS"))
            }
        }
        VoiceTtsBackend.MiniMax -> {
            val idx = providers.indexOfFirst { it is TTSProviderSetting.MiniMax }
            if (idx >= 0) {
                providers[idx] = (providers[idx] as TTSProviderSetting.MiniMax).copy(apiKey = key)
            } else {
                providers.add(TTSProviderSetting.MiniMax(apiKey = key, name = "MiniMax TTS"))
            }
        }
        VoiceTtsBackend.ElevenLabs -> {
            val idx = providers.indexOfFirst { it is TTSProviderSetting.ElevenLabs }
            if (idx >= 0) {
                providers[idx] = (providers[idx] as TTSProviderSetting.ElevenLabs).copy(apiKey = key)
            } else {
                providers.add(TTSProviderSetting.ElevenLabs(apiKey = key, name = "ElevenLabs TTS"))
            }
        }
        VoiceTtsBackend.FishAudio -> {
            val idx = providers.indexOfFirst { it is TTSProviderSetting.FishAudio }
            if (idx >= 0) {
                providers[idx] = (providers[idx] as TTSProviderSetting.FishAudio).copy(apiKey = key)
            } else {
                providers.add(TTSProviderSetting.FishAudio(apiKey = key, name = "Fish Audio TTS"))
            }
        }
    }
    return copy(ttsProviders = providers)
}

fun Settings.withAssistantVoiceCall(
    assistantId: Uuid,
    voiceCall: AssistantVoiceCallSettings,
): Settings {
    return copy(
        assistants = assistants.map { assistant ->
            if (assistant.id == assistantId) assistant.copy(voiceCall = voiceCall) else assistant
        }
    )
}
