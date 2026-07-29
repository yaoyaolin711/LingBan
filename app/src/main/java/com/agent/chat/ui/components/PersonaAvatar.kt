package com.agent.chat.ui.components

import com.agent.chat.ui.theme.AgentThemeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun PersonaAvatar(
    name: String,
    avatar: String,
    size: Dp = 48.dp,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AgentThemeColors

    val context = LocalContext.current
    val isUrl = remember(avatar) {
        avatar.startsWith("http://") || avatar.startsWith("https://")
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (highlighted) colors.accent.copy(alpha = 0.12f) else colors.surfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (isUrl) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatar)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = name,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = avatar.ifBlank { name.take(1).ifBlank { "A" } },
                color = if (highlighted) colors.accent else colors.textPrimary,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
