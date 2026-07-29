package com.agent.chat.ui.chat

import com.agent.chat.ui.theme.AgentThemeColors
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.agent.chat.ui.motion.scaleClickable

@Composable
fun FloatingInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    visible: Boolean,
    personaName: String?,
    editing: Boolean,
    onCancelEdit: () -> Unit,
    onPlusClick: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    pendingImageUri: String? = null,
    onClearImageAttachment: () -> Unit = {},
) {
    val colors = AgentThemeColors

    val canSend = enabled && (value.isNotBlank() || pendingImageUri != null)
    val placeholder = when {
        !enabled -> "正在回复…"
        editing -> "编辑后发送…"
        personaName.isNullOrBlank() -> "说点什么…"
        else -> "和${personaName}聊点什么…"
    }
    var focused by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
        ) { it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
        ) { it / 2 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (editing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "编辑后重发",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "取消",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCancelEdit)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            // Image attachment thumbnail
            if (pendingImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp, start = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    ) {
                        AsyncImage(
                            model = Uri.parse(pendingImageUri),
                            contentDescription = "待发送图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        IconButton(
                            onClick = onClearImageAttachment,
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "移除图片",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = if (focused) 12.dp else 4.dp,
                            shape = RoundedCornerShape(28.dp),
                            clip = false,
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surface.copy(alpha = if (focused) 0.96f else 0.98f))
                        .border(
                            width = 1.dp,
                            color = if (focused) colors.accent.copy(alpha = 0.3f) else colors.outline,
                            shape = RoundedCornerShape(28.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    RoundIcon(onClick = onPlusClick, enabled = enabled) {
                        Icon(Icons.Default.Add, contentDescription = "更多", tint = colors.textSecondary)
                    }
                    RoundIcon(onClick = onAttachClick, enabled = enabled) {
                        Icon(Icons.Default.AttachFile, contentDescription = "附件", tint = colors.textSecondary)
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp, max = 120.dp)
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                            .onFocusChanged {
                                focused = it.isFocused
                                onFocusChange(it.isFocused)
                            },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        maxLines = 5,
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textSecondary,
                                    )
                                }
                                inner()
                            }
                        },
                    )

                    RoundIcon(onClick = onVoiceClick, enabled = enabled) {
                        Icon(Icons.Default.Mic, contentDescription = "语音", tint = colors.textSecondary)
                    }

                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (canSend) colors.accent else colors.surfaceMuted),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (canSend) Color.White else colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .scaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
