package com.agent.chat.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.chat.data.memory.MemorySettingsStore
import com.agent.chat.domain.model.Memory
import com.agent.chat.ui.theme.Accent
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.SurfaceMuted
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryManageDialog(
    memories: List<Memory>,
    personaName: String?,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    extractThreshold: Int = MemorySettingsStore.DEFAULT_THRESHOLD,
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "记忆管理",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = if (personaName.isNullOrBlank()) {
                        "查看并删除 AI 记住的用户信息"
                    } else {
                        "「$personaName」记住的用户信息（跨会话共享）"
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (memories.isEmpty()) {
                    Text(
                        text = "暂无记忆。可在对话中让 TA 用记忆工具记下重要信息；对话较多时也会自动补充摘要。" +
                            "（注入上限 ${MemorySettingsStore.PROMPT_MEMORY_MAX_CHARS} 字）。",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(memories, key = { it.id }) { memory ->
                            MemoryListItem(
                                memory = memory,
                                timeLabel = dateFormat.format(Date(memory.createdAt)),
                                onDelete = { onDelete(memory.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Accent)
            }
        },
    )
}

@Composable
private fun MemoryListItem(
    memory: Memory,
    timeLabel: String,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceMuted,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$timeLabel · 重要度 ${memory.importance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除记忆",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
