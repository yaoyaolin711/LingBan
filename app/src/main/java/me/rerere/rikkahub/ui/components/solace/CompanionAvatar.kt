package me.rerere.rikkahub.ui.components.solace

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SolaceTheme

enum class CompanionAvatarSize(
    val frame: Dp,
    val avatar: Dp,
) {
    Compact(frame = 96.dp, avatar = 56.dp),
    Medium(frame = 168.dp, avatar = 96.dp),
    Hero(frame = 200.dp, avatar = 112.dp),
}

/**
 * AI companion avatar with optional rose-gold halo + breath (single InfiniteTransition).
 */
@Composable
fun CompanionAvatar(
    name: String,
    avatar: Avatar,
    modifier: Modifier = Modifier,
    size: CompanionAvatarSize = CompanionAvatarSize.Medium,
    showHalo: Boolean = true,
    breath: Boolean = true,
    showName: Boolean = false,
    statusLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val anim = SolaceTheme.animation
    val dark = LocalDarkMode.current
    val animatePresence = showHalo || breath || statusLabel != null

    val pulse = if (animatePresence) {
        val infinite = rememberInfiniteTransition(label = "companion_avatar")
        val value by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(anim.durationBreath, easing = anim.easingStandard),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        value
    } else {
        0.5f
    }

    val haloAlpha = if (dark) 0.28f + pulse * 0.20f else 0.38f + pulse * 0.17f
    val breathScale = if (breath) 0.985f + pulse * 0.03f else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.frame)
                .graphicsLayer {
                    scaleX = breathScale
                    scaleY = breathScale
                }
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else Modifier
                ),
        ) {
            if (showHalo) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val frame = this.size
                    val c = Offset(frame.width / 2f, frame.height / 2f)
                    val r = frame.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to colors.primary.copy(alpha = haloAlpha * 0.45f),
                                0.55f to colors.accent.copy(alpha = haloAlpha * 0.2f),
                                1f to Color.Transparent,
                            ),
                            center = c,
                            radius = r,
                        ),
                        radius = r,
                        center = c,
                    )
                }
            }
            UIAvatar(
                name = name,
                value = avatar,
                modifier = Modifier.size(size.avatar),
                onClick = onClick,
            )
        }

        if (showName) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = name,
                style = typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (statusLabel != null) {
            Spacer(Modifier.height(10.dp))
            CompanionOnlineChip(label = statusLabel, pulse = pulse)
        }
    }
}

@Composable
fun CompanionOnlineChip(
    label: String,
    modifier: Modifier = Modifier,
    pulse: Float = 1f,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val dotAlpha = 0.55f + pulse * 0.45f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { alpha = dotAlpha }
                .clip(CircleShape)
                .background(colors.primary),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = typography.labelMedium,
            color = colors.secondaryText,
        )
    }
}
