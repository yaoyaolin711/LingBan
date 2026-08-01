package me.rerere.rikkahub.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Home01
import me.rerere.rikkahub.ui.components.ui.UIAvatar

@Composable
fun TaskBallContent(
    state: TaskBallState,
    onToggleExpand: () -> Unit,
    onOpenApp: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(8.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable(onClick = onToggleExpand),
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UIAvatar(
                        name = state.assistantName,
                        value = state.avatar,
                        modifier = Modifier.size(52.dp),
                        loading = true,
                        onClick = onToggleExpand,
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(8.dp),
                            strokeWidth = 1.2.dp,
                            color = Color.White,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = state.assistantName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(
                visible = state.expanded,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (state.overlayPermissionNeeded) {
                        TextButton(onClick = onRequestPermission) {
                            Text("开启悬浮窗权限")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onOpenApp) {
                            Icon(HugeIcons.Home01, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("回到应用", modifier = Modifier.padding(start = 4.dp))
                        }
                        TextButton(onClick = onStop) {
                            Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("停止", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
