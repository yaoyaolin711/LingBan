package me.rerere.rikkahub.ui.pages.voicecall

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 语音通话临时排障开关。修好后改为 false 或删除相关 UI 即可。
 */
object VoiceCallDiag {
    const val ENABLED = true

    private const val MAX_EVENTS = 80
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val events = CopyOnWriteArrayList<String>()
    private val _snapshot = MutableStateFlow(VoiceCallDiagSnapshot())
    val snapshot: StateFlow<VoiceCallDiagSnapshot> = _snapshot.asStateFlow()

    fun clear() {
        events.clear()
        _snapshot.update { it.copy(events = emptyList(), note = "日志已清空") }
    }

    fun log(tag: String, message: String) {
        if (!ENABLED) return
        val line = "${timeFmt.format(Date())} [$tag] $message"
        android.util.Log.i("VoiceCallDiag", line)
        events.add(line)
        while (events.size > MAX_EVENTS) {
            events.removeAt(0)
        }
        _snapshot.update { it.copy(events = events.toList()) }
    }

    fun updateLive(block: (VoiceCallDiagSnapshot) -> VoiceCallDiagSnapshot) {
        if (!ENABLED) return
        _snapshot.update(block)
    }

    fun dumpText(): String {
        val s = _snapshot.value
        return buildString {
            appendLine("=== Solace VoiceCall Diag ===")
            appendLine("phase=${s.phase}")
            appendLine("statusMsg=${s.statusMessage}")
            appendLine("error=${s.errorMessage}")
            appendLine("partial=${s.partialTranscript}")
            appendLine("latest=${s.latestTranscript}")
            appendLine("lastUser=${s.lastUserText}")
            appendLine("asrStatus=${s.asrStatus} recording=${s.asrRecording} available=${s.asrAvailable}")
            appendLine("asrError=${s.asrError}")
            appendLine("asrProvider=${s.asrProvider}")
            appendLine("micGranted=${s.micGranted}")
            appendLine("recognitionAvailable=${s.recognitionAvailable}")
            appendLine("recognitionDetail=${s.recognitionDetail}")
            appendLine("sawRecording=${s.sawRecording} submitting=${s.submitting} awaitingGen=${s.awaitingGeneration}")
            appendLine("stallHint=${s.stallHint}")
            appendLine("note=${s.note}")
            appendLine("--- events ---")
            s.events.forEach { appendLine(it) }
        }
    }
}

data class VoiceCallDiagSnapshot(
    val phase: String = "",
    val statusMessage: String = "",
    val errorMessage: String = "",
    val partialTranscript: String = "",
    val latestTranscript: String = "",
    val lastUserText: String = "",
    val asrStatus: String = "",
    val asrRecording: Boolean = false,
    val asrAvailable: Boolean = false,
    val asrError: String = "",
    val asrProvider: String = "",
    val micGranted: Boolean = false,
    val recognitionAvailable: Boolean = false,
    val recognitionDetail: String = "",
    val sawRecording: Boolean = false,
    val submitting: Boolean = false,
    val awaitingGeneration: Boolean = false,
    val stallHint: String = "",
    val note: String = "排障：说完后需「转写稳定 + 静音」各约2秒才自动发送；抢话可点强制发送。",
    val events: List<String> = emptyList(),
)
