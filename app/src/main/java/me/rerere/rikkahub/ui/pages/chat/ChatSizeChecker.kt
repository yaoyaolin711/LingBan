package me.rerere.rikkahub.ui.pages.chat

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.utils.formatNumber

// 消息节点数量警告阈值（崩溃/卡顿防护）
const val MESSAGE_NODE_WARNING_THRESHOLD = 768
const val LAST_ASSISTANT_INPUT_TOKEN_WARNING_THRESHOLD = 300_000

/** Soft budget for attention quality; below crash-level token threshold. */
const val SOFT_CONTEXT_TOKEN_BUDGET = 32_000
const val CONTEXT_USAGE_WARN_RATIO = 0.8f

data class ConversationSizeInfo(
    val nodeCount: Int,
    val lastAssistantInputTokens: Int,
    val exceedNodeCountThreshold: Boolean,
    val exceedInputTokenThreshold: Boolean,
    val showWarning: Boolean
)

data class ContextUsageInfo(
    val promptTokens: Int,
    val budget: Int = SOFT_CONTEXT_TOKEN_BUDGET,
    val ratio: Float,
    val isNearFull: Boolean,
    val isFull: Boolean,
    val hasUsage: Boolean,
)

@Composable
fun rememberConversationSizeInfo(conversation: Conversation): ConversationSizeInfo {
    return remember(conversation.messageNodes) {
        val nodeCount = conversation.messageNodes.size
        val lastAssistantInputTokens = lastAssistantPromptTokens(conversation)
        val exceedNodeCountThreshold = nodeCount > MESSAGE_NODE_WARNING_THRESHOLD
        val exceedInputTokenThreshold = lastAssistantInputTokens > LAST_ASSISTANT_INPUT_TOKEN_WARNING_THRESHOLD
        ConversationSizeInfo(
            nodeCount = nodeCount,
            lastAssistantInputTokens = lastAssistantInputTokens,
            exceedNodeCountThreshold = exceedNodeCountThreshold,
            exceedInputTokenThreshold = exceedInputTokenThreshold,
            showWarning = exceedNodeCountThreshold && exceedInputTokenThreshold
        )
    }
}

@Composable
fun rememberContextUsageInfo(conversation: Conversation): ContextUsageInfo {
    return remember(conversation.messageNodes) {
        val promptTokens = lastAssistantPromptTokens(conversation)
        val hasUsage = promptTokens > 0
        val ratio = if (hasUsage) {
            (promptTokens.toFloat() / SOFT_CONTEXT_TOKEN_BUDGET).coerceIn(0f, 1f)
        } else {
            0f
        }
        ContextUsageInfo(
            promptTokens = promptTokens,
            budget = SOFT_CONTEXT_TOKEN_BUDGET,
            ratio = ratio,
            isNearFull = hasUsage && ratio >= CONTEXT_USAGE_WARN_RATIO,
            isFull = hasUsage && promptTokens >= SOFT_CONTEXT_TOKEN_BUDGET,
            hasUsage = hasUsage,
        )
    }
}

private fun lastAssistantPromptTokens(conversation: Conversation): Int {
    return conversation.messageNodes.asReversed()
        .map { it.currentMessage }
        .firstOrNull { it.role == MessageRole.ASSISTANT }
        ?.usage
        ?.promptTokens
        ?: 0
}

@Composable
fun ConversationSizeWarningDialog(
    sizeInfo: ConversationSizeInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = HugeIcons.Alert01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            Text(text = stringResource(R.string.chat_size_dialog_title))
        },
        text = {
            Text(text = stringResource(R.string.chat_size_dialog_content, sizeInfo.nodeCount))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
fun ContextUsageGauge(
    usage: ContextUsageInfo,
    modifier: Modifier = Modifier,
) {
    if (!usage.hasUsage) return

    val color = when {
        usage.isFull -> MaterialTheme.colorScheme.error
        usage.isNearFull -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val tooltipText = stringResource(
        R.string.chat_context_usage_tooltip,
        usage.promptTokens.formatNumber(),
        usage.budget.formatNumber(),
    )

    Tooltip(
        modifier = modifier,
        tooltip = { Text(tooltipText) },
    ) {
        CircularProgressIndicator(
            progress = { usage.ratio },
            modifier = Modifier.size(22.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 2.5.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    }
}

@Composable
fun ContextFullOfferCard(
    onPackAndNewChat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_context_full_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.chat_context_full_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.chat_context_full_later))
                }
                Button(
                    onClick = onPackAndNewChat,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.chat_context_full_pack))
                }
            }
        }
    }
}
