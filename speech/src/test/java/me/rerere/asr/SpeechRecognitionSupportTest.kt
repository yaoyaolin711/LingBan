package me.rerere.asr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognitionSupportTest {
    @Test
    fun hard_failure_detects_unavailable_and_engine_messages() {
        assertTrue(SpeechRecognitionSupport.isHardFailure(SpeechRecognitionSupport.UNAVAILABLE_MESSAGE))
        assertTrue(SpeechRecognitionSupport.isHardFailure(SpeechRecognitionSupport.ENGINE_PERMISSION_MESSAGE))
        assertTrue(SpeechRecognitionSupport.isHardFailure("Speech recognition is not available on this device"))
        assertTrue(SpeechRecognitionSupport.isHardFailure("Microphone permission is required"))
        assertTrue(SpeechRecognitionSupport.isHardFailure("需要麦克风权限才能进行语音识别"))
        assertFalse(SpeechRecognitionSupport.isHardFailure("语音识别网络超时"))
        assertFalse(SpeechRecognitionSupport.isHardFailure(null))
        assertFalse(SpeechRecognitionSupport.isHardFailure(""))
    }
}
