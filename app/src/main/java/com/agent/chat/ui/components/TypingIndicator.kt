package com.agent.chat.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agent.chat.ui.theme.BubbleElevation
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.TextSecondary

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = BubbleElevation),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val scale by transition.animateFloat(
                    initialValue = 0.75f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 700,
                            delayMillis = index * 160,
                            easing = FastOutSlowInEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot_$index",
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(TextSecondary.copy(alpha = 0.55f)),
                )
            }
        }
    }
}
