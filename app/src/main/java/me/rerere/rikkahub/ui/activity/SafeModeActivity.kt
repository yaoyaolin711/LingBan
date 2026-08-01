package me.rerere.rikkahub.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.utils.CrashHandler
import org.koin.core.context.GlobalContext

/**
 * Minimal recovery UI that must work even when Koin/Theme DI is broken.
 */
class SafeModeActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = CrashHandler.getStackTrace(this)
            ?: "No crash stack captured.\nCheck:\n${CrashHandler.crashFile(this).absolutePath}"
        CrashHandler.clearCrashed(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SafeModeContent(
                    stackTrace = stackTrace,
                    koinReady = GlobalContext.getOrNull() != null,
                    onEnterApp = {
                        startActivity(Intent(this@SafeModeActivity, RouteActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafeModeContent(
    stackTrace: String,
    koinReady: Boolean,
    onEnterApp: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("安全模式 / Safe Mode") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (koinReady) {
                    "应用上次启动崩溃。请复制下方堆栈发给开发者；确认后可尝试重新进入。"
                } else {
                    "依赖注入启动失败（Koin 未就绪）。请复制下方堆栈发给开发者。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (koinReady) {
                OutlinedButton(
                    onClick = onEnterApp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新进入应用")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("崩溃报告", style = MaterialTheme.typography.titleSmall)
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("crash", stackTrace))
                        Toast.makeText(context, "已复制崩溃堆栈", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("复制")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()
                Text(
                    text = stackTrace,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
