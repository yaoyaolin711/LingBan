package me.rerere.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ASRProviderSettingSystemTest {
    @Test
    fun system_defaults_are_expected() {
        val setting = ASRProviderSetting.System()

        assertEquals("System ASR", setting.name)
        assertEquals("", setting.language)
        assertFalse(setting.preferOffline)
        assertEquals(1, setting.maxResults)
    }

    @Test
    fun system_is_registered_in_provider_types() {
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.System::class))
    }

    @Test
    fun system_copy_provider_preserves_extra_fields() {
        val original = ASRProviderSetting.System(
            language = "zh-CN",
            preferOffline = true,
            maxResults = 3,
        )
        val copied = original.copyProvider(id = original.id, name = "renamed")

        assertTrue(copied is ASRProviderSetting.System)
        val system = copied as ASRProviderSetting.System
        assertEquals("renamed", system.name)
        assertEquals("zh-CN", system.language)
        assertTrue(system.preferOffline)
        assertEquals(3, system.maxResults)
    }
}
