package com.agent.chat.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.agent.chat.domain.error.AppError
import com.agent.chat.domain.error.userMessage
import com.agent.chat.domain.model.Message
import com.agent.chat.ui.components.MarkdownMessageContent
import com.agent.chat.ui.home.AiOrb
import com.agent.chat.ui.home.AiOrbState
import com.agent.chat.ui.motion.AiLoadingIndicator
import com.agent.chat.ui.theme.Accent
import com.agent.chat.ui.theme.BubbleUser
import com.agent.chat.ui.theme.ErrorSoftBg
import com.agent.chat.ui.theme.ErrorSoftBorder
import com.agent.chat.ui.theme.ErrorSoftText
import com.agent.chat.ui.theme.OutlineSubtle
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessageBubble(
    message: Message,
    enabled: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEditResend: () -> Unit,
    onFavorite: () -> Unit,
    onAddMemory: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            Card(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        enabled = enabled && message.content.isNotBlank(),
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    ),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 6.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp,
                ),
                colors = CardDefaults.cardColors(containerColor = BubbleUser),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = message.content,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            MessageActionMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                isUser = true,
                onCopy = onCopy,
                onRegenerate = onRegenerate,
                onEditResend = onEditResend,
                onFavorite = onFavorite,
                onAddMemory = onAddMemory,
            )
        }
    }
}

/**
 * AI 内容流：无气泡，Avatar → 文本 → 工具状态 → 引用。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiContentStream(
    message: Message,
    isStreaming: Boolean,
    toolCalls: List<ToolCallUiItem>,
    enabled: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onFavorite: () -> Unit,
    onAddMemory: () -> Unit,
    onToggleTool: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled && message.content.isNotBlank(),
                onClick = {},
                onLongClick = { menuExpanded = true },
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AiOrb(
            state = when {
                isStreaming && message.content.isEmpty() -> AiOrbState.Thinking
                isStreaming -> AiOrbState.Speaking
                else -> AiOrbState.Idle
            },
            size = 28.dp,
        )

        Box {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.content.isNotEmpty()) {
                    MarkdownMessageContent(
                        markdown = message.content,
                        textColor = TextPrimary,
                        isUser = false,
                        showStreamingCursor = isStreaming,
                        modifier = Modifier.graphicsLayer {
                            if (isStreaming) alpha = 0.92f + cursorAlpha * 0.08f
                        },
                    )
                }

                if (toolCalls.isNotEmpty()) {
                    ToolCallStream(
                        items = toolCalls,
                        onToggle = onToggleTool,
                    )
                }
            }
            MessageActionMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                isUser = false,
                onCopy = onCopy,
                onRegenerate = onRegenerate,
                onEditResend = {},
                onFavorite = onFavorite,
                onAddMemory = onAddMemory,
            )
        }
    }
}

@Composable
fun ThinkingStreamRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        AiOrb(state = AiOrbState.Thinking, size = 32.dp)
        Text(
            text = "正在思考…",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
fun FailedMessageBlock(
    message: Message,
    error: AppError,
    debugDetail: String?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (message.content.isNotBlank()) {
            MarkdownMessageContent(
                markdown = message.content,
                textColor = TextPrimary,
                isUser = false,
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ErrorSoftBg)
                .border(1.dp, ErrorSoftBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = error.userMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorSoftText,
            )
            if (!debugDetail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = debugDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("重试", color = Accent)
            }
        }
    }
}

@Composable
private fun MessageActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isUser: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEditResend: () -> Unit,
    onFavorite: () -> Unit,
    onAddMemory: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = {
                onDismiss()
                onCopy()
            },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
        )
        DropdownMenuItem(
            text = { Text("重新生成") },
            onClick = {
                onDismiss()
                onRegenerate()
            },
            leadingIcon = { Icon(Icons.Default.Refresh, null) },
        )
        DropdownMenuItem(
            text = { Text("收藏") },
            onClick = {
                onDismiss()
                onFavorite()
            },
            leadingIcon = { Icon(Icons.Default.BookmarkBorder, null) },
        )
        DropdownMenuItem(
            text = { Text("加入记忆") },
            onClick = {
                onDismiss()
                onAddMemory()
            },
            leadingIcon = { Icon(Icons.Default.Psychology, null) },
        )
        if (isUser) {
            DropdownMenuItem(
                text = { Text("编辑重发") },
                onClick = {
                    onDismiss()
                    onEditResend()
                },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
            )
        }
    }
}

@Composable
fun ToolCallStream(
    items: List<ToolCallUiItem>,
    onToggle: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val borderColor = when {
                item.running -> Accent.copy(alpha = 0.35f)
                item.success == false -> ErrorSoftBorder
                else -> OutlineSubtle
            }
            val bg = when {
                item.success == false -> ErrorSoftBg
                else -> SurfaceCard.copy(alpha = 0.7f)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                    .clickable { onToggle(item.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            item.running -> "工具 · ${item.label}…"
                            item.success == false -> "工具 · ${item.label}失败"
                            else -> "工具 · ${item.label}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.success == false) ErrorSoftText else TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.running) {
                        AiLoadingIndicator(
                            size = 18.dp,
                            showLabel = false,
                            state = AiOrbState.Thinking,
                        )
                    }
                }
                if (item.expanded && item.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}
