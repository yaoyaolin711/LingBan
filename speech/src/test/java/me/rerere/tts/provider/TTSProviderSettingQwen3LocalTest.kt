package me.rerere.tts.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TTSProviderSettingQwen3LocalTest {
    @Test
    fun qwen3_local_defaults_are_expected() {
        val setting = TTSProviderSetting.Qwen3Local()

        assertEquals("Qwen3 局域网", setting.name)
        assertEquals("", setting.baseUrl)
        assertEquals("custom_voice", setting.mode)
        assertEquals("Vivian", setting.speaker)
        assertEquals("Auto", setting.language)
        assertEquals("wav", setting.responseFormat)
        assertEquals(1.0f, setting.speed, 0.001f)
        assertTrue(setting.fallbackToSystem)
    }

    @Test
    fun qwen3_local_is_registered_in_provider_types() {
        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.Qwen3Local::class))
    }
}
