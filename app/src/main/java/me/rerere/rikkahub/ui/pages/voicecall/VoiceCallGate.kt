package me.rerere.rikkahub.ui.pages.voicecall

/**
 * Global gate so Chat-page TTSAutoPlay does not double-speak during a voice call.
 */
object VoiceCallGate {
    @Volatile
    var active: Boolean = false
}
