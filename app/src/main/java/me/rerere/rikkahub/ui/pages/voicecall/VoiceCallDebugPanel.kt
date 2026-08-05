package me.rerere.rikkahub.ui.pages.voicecall

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.theme.SolaceTheme

@Composable
fun VoiceCallDebugFab(
    visible: Boolean,
    onOpen: () -> Unit,
) {
    if (!visible || !VoiceCallDiag.ENABLED) return
    FilledTonalButton(onClick = onOpen) {
        Text("排障")
    }
}

@Composable
fun VoiceCallDebugDialog(
    onDismiss: () -> Unit,
    onForceSubmit: () -> Unit,
    onForceRelisten: () -> Unit,
    onClearLog: () -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val snap by VoiceCallDiag.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语音通话排障") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = snap.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                )
                DiagBlock(
                    """
                    phase=${snap.phase}
                    status=${snap.statusMessage}
                    error=${snap.errorMessage}
                    partial="${snap.partialTranscript}"
                    latest="${snap.latestTranscript}"
                    lastUser="${snap.lastUserText}"
                    asr=${snap.asrStatus} recording=${snap.asrRecording} avail=${snap.asrAvailable}
                    asrErr=${snap.asrError}
                    provider=${snap.asrProvider}
                    mic=${snap.micGranted} recog=${snap.recognitionAvailable}
                    recogDetail=${snap.recognitionDetail}
                    sawRec=${snap.sawRecording} submitting=${snap.submitting} awaiting=${snap.awaitingGeneration}
                    stall=${snap.stallHint}
                    """.trimIndent()
                )
                Text("最近事件", style = MaterialTheme.typography.labelMedium)
                DiagBlock(snap.events.takeLast(30).joinToString("\n").ifBlank { "(暂无)" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onForceSubmit) { Text("强制发送") }
                    FilledTonalButton(onClick = onForceRelisten) { Text("强制重听") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("voicecall-diag", VoiceCallDiag.dumpText()))
                    }) { Text("复制全部日志") }
                    TextButton(onClick = onClearLog) { Text("清空") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DiagBlock(text: String) {
    val colors = SolaceTheme.colorScheme
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = colors.text,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(8.dp),
    )
    Spacer(modifier = Modifier.height(2.dp))
}
