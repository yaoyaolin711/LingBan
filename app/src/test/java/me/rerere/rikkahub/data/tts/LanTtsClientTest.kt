package me.rerere.rikkahub.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanTtsClientTest {
    @Test
    fun classify_invalid_url() {
        val type = LanTtsClient.classifyHealthError(
            IllegalStateException("Invalid TTS service URL: blah")
        )
        assertEquals(LanTtsClient.HealthErrorType.InvalidUrl, type)
    }

    @Test
    fun classify_connection_refused() {
        val type = LanTtsClient.classifyHealthError(java.net.ConnectException("Connection refused"))
        assertEquals(LanTtsClient.HealthErrorType.ConnectionRefused, type)
    }

    @Test
    fun health_hint_is_non_blank() {
        LanTtsClient.HealthErrorType.entries.forEach { type ->
            assertTrue(LanTtsClient.healthHint(type).isNotBlank())
        }
    }

    @Test
    fun diagnostics_contains_url() {
        val text = LanTtsClient.buildDiagnostics(
            "http://192.168.1.100:8877",
            java.net.ConnectException("refused")
        )
        assertTrue(text.contains("192.168.1.100:8877"))
        assertTrue(text.contains("LAN TTS Diagnostics"))
    }
}
