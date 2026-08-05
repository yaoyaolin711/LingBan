package me.rerere.rikkahub.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown by Health Connect when the user opens permission rationale / usage.
 */
class HealthConnectRationaleActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Health Connect") })
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "为什么需要健康数据？",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Solace 只会读取步数、心率、睡眠和活动摘要，用来让伴侣更懂你的状态" +
                                "（例如睡得不好时语气更轻柔）。数据仅在本机用于对话上下文，不做医疗诊断，也不会上传到独立健康服务器。",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { finish() }) {
                            Text("知道了")
                        }
                    }
                }
            }
        }
    }
}
