package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallQwen3LocalResolveTest {
    @Test
    fun resolve_qwen3_local_when_configured() {
        val settings = Settings(
            ttsLanEnabled = true,
            ttsLanServiceUrl = "http://192.168.1.100:8877",
        )
        val voiceCall = AssistantVoiceCallSettings(
            tier = VoiceTier.Local,
            presetId = "local_qwen3_vivian",
        )
        val result = resolveVoiceCallTts(settings, voiceCall)
        assertTrue(result is VoiceCallTtsResolveResult.Ready)
        val provider = (result as VoiceCallTtsResolveResult.Ready).provider
        assertTrue(provider is TTSProviderSetting.Qwen3Local)
        val local = provider as TTSProviderSetting.Qwen3Local
        assertEquals("http://192.168.1.100:8877", local.baseUrl)
        assertEquals("Vivian", local.speaker)
    }

    @Test
    fun resolve_qwen3_local_falls_back_to_system_when_unconfigured() {
        val settings = Settings(
            ttsLanEnabled = false,
            ttsLanServiceUrl = "",
            ttsLanFallbackToSystem = true,
        )
        val voiceCall = AssistantVoiceCallSettings(
            tier = VoiceTier.Local,
            presetId = "local_qwen3_serena",
        )
        val result = resolveVoiceCallTts(settings, voiceCall)
        assertTrue(result is VoiceCallTtsResolveResult.Ready)
        assertTrue((result as VoiceCallTtsResolveResult.Ready).provider is TTSProviderSetting.SystemTTS)
    }

    @Test
    fun resolve_qwen3_local_unavailable_without_fallback() {
        val settings = Settings(
            ttsLanEnabled = false,
            ttsLanServiceUrl = "",
            ttsLanFallbackToSystem = false,
        )
        val voiceCall = AssistantVoiceCallSettings(
            tier = VoiceTier.Local,
            presetId = "local_qwen3_vivian",
        )
        val result = resolveVoiceCallTts(settings, voiceCall)
        assertTrue(result is VoiceCallTtsResolveResult.Unavailable)
    }

    @Test
    fun withLanTtsConfig_upserts_provider() {
        val updated = Settings().withLanTtsConfig(
            enabled = true,
            serviceUrl = "http://10.0.0.2:8877",
        )
        assertTrue(updated.ttsLanEnabled)
        assertEquals("http://10.0.0.2:8877", updated.ttsLanServiceUrl)
        val local = updated.ttsProviders.filterIsInstance<TTSProviderSetting.Qwen3Local>()
        assertEquals(1, local.size)
        assertEquals("http://10.0.0.2:8877", local.first().baseUrl)
    }
}
