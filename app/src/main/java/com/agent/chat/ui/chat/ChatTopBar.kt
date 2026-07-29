package com.agent.chat.ui.chat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.chat.ui.home.AiOrb
import com.agent.chat.ui.home.AiOrbState
import com.agent.chat.ui.motion.SharedKeys
import com.agent.chat.ui.motion.scaleClickable
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.OnlineGreen
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ChatTopBar(
    animatedVisibilityScope: AnimatedVisibilityScope,
    displayName: String,
    presence: ChatPresenceStatus,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onClearContext: () -> Unit,
    onSwitchPersona: () -> Unit,
    onManageMemory: () -> Unit,
    onExport: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val orbState = when (presence) {
        ChatPresenceStatus.Idle -> AiOrbState.Idle
        ChatPresenceStatus.Thinking -> AiOrbState.Thinking
        ChatPresenceStatus.Typing -> AiOrbState.Speaking
    }
    val statusColor = when (presence) {
        ChatPresenceStatus.Idle -> OnlineGreen
        else -> TextSecondary
    }
    val colors = AgentThemeColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.92f))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = TextPrimary,
            modifier = Modifier
                .padding(10.dp)
                .scaleClickable(onClick = onBackClick),
        )

        AiOrb(
            state = orbState,
            size = 40.dp,
            modifier = Modifier.sharedElement(
                state = rememberSharedContentState(SharedKeys.AI_ORB),
                animatedVisibilityScope = animatedVisibilityScope,
            ),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = presence.label(),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = TextSecondary)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("搜索消息") },
                    onClick = {
                        menuExpanded = false
                        onToggleSearch()
                    },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                )
                DropdownMenuItem(
                    text = { Text("切换伙伴") },
                    onClick = {
                        menuExpanded = false
                        onSwitchPersona()
                    },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                )
                DropdownMenuItem(
                    text = { Text("记忆管理") },
                    onClick = {
                        menuExpanded = false
                        onManageMemory()
                    },
                    leadingIcon = { Icon(Icons.Default.Psychology, null) },
                )
                DropdownMenuItem(
                    text = { Text("清空上下文") },
                    onClick = {
                        menuExpanded = false
                        onClearContext()
                    },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                )
                DropdownMenuItem(
                    text = { Text("导出会话") },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                )
            }
        }
    }
}
