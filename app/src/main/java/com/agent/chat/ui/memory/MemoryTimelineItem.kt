package com.agent.chat.ui.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.chat.domain.model.Memory
import com.agent.chat.ui.theme.Accent
import com.agent.chat.ui.theme.ErrorSoftText
import com.agent.chat.ui.theme.OutlineSubtle
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.SurfaceMuted
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun MemoryTimelineItem(
    memory: Memory,
    expanded: Boolean,
    index: Int,
    visible: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleBlocked: () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(24f) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(index * 70L)
            alpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            offsetY.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        }
    }

    val dateLabel = remember(memory.createdAt) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(memory.createdAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = offsetY.value
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (memory.blockedFromAi) TextSecondary.copy(alpha = 0.45f) else Accent),
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(2.dp)
                    .weight(1f)
                    .background(OutlineSubtle),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCard)
                .border(1.dp, OutlineSubtle.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = memory.timelineTitle(),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = memory.timelineBody(),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary.copy(alpha = 0.88f),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (memory.blockedFromAi) "已禁止 AI 使用" else "AI 自动学习",
                style = MaterialTheme.typography.bodySmall,
                color = if (memory.blockedFromAi) ErrorSoftText else TextSecondary,
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(220)) + expandVertically(tween(260, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(200)),
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(OutlineSubtle),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = memory.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Outlined.Edit, null, tint = Accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("编辑", color = Accent)
                        }
                        TextButton(onClick = onToggleBlocked) {
                            Icon(
                                Icons.Outlined.Block,
                                null,
                                tint = if (memory.blockedFromAi) Accent else TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (memory.blockedFromAi) "允许使用" else "禁止使用",
                                color = if (memory.blockedFromAi) Accent else TextSecondary,
                            )
                        }
                        TextButton(onClick = onDelete) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                null,
                                tint = ErrorSoftText,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", color = ErrorSoftText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserPortraitCard(
    portrait: UserPortraitUi,
    visible: Boolean,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(16f) }
    LaunchedEffect(visible) {
        if (visible) {
            alpha.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            offsetY.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = offsetY.value
            }
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFF4EC),
                        SurfaceMuted,
                        Color(0xFFFFF8F2),
                    ),
                ),
            )
            .border(1.dp, OutlineSubtle, RoundedCornerShape(24.dp))
            .clickable(onClick = onEditClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "关于你",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "编辑",
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        PortraitField(label = "姓名", value = portrait.name.ifBlank { "还未告诉我" })
        PortraitField(label = "兴趣", value = portrait.interest.ifBlank { "聊天中慢慢了解" })
        PortraitField(label = "职业", value = portrait.occupation.ifBlank { "暂未记录" })
        PortraitField(label = "目标", value = portrait.goal.ifBlank { "想和你一起找到方向" }, last = true)
    }
}

@Composable
private fun PortraitField(
    label: String,
    value: String,
    last: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(40.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
