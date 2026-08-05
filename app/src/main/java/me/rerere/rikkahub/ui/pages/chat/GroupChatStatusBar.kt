package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.groupchat.GroupChatMode
import me.rerere.rikkahub.data.groupchat.HardGatePolicy
import me.rerere.rikkahub.data.model.Conversation

@Composable
fun GroupChatStatusBar(
    conversation: Conversation,
    onModeChange: (GroupChatMode) -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!conversation.isGroup) return
    val floor = conversation.floorState
    val remaining = floor.remainingChainDepth(HardGatePolicy.DEFAULT)
    val budget = floor.remainingSpeakerBudget(HardGatePolicy.DEFAULT)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.group_chat_members_count,
                    conversation.groupMembers.size,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                conversation.groupMembers.forEach { member ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(member.displayName.ifBlank { member.assistantId.toString().take(6) })
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = conversation.groupMode == GroupChatMode.MENTION_FIRST,
                    onClick = { onModeChange(GroupChatMode.MENTION_FIRST) },
                    label = { Text(stringResource(R.string.group_chat_mode_mention_first)) },
                )
                FilterChip(
                    selected = conversation.groupMode == GroupChatMode.FREE_DISCUSSION,
                    onClick = { onModeChange(GroupChatMode.FREE_DISCUSSION) },
                    label = { Text(stringResource(R.string.group_chat_mode_free_discussion)) },
                )
            }
            Text(
                text = if (floor.active) {
                    stringResource(R.string.group_chat_floor_active, remaining, budget)
                } else {
                    stringResource(R.string.group_chat_floor_idle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (floor.active) {
                TextButton(onClick = onPause) {
                    Text(stringResource(R.string.group_chat_pause))
                }
            }
        }
    }
}
