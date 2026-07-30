package me.rerere.rikkahub.ui.modifier

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * Tiny breathing scale via a single InfiniteTransition.
 * Amplitude kept small (≈2%) for 60fps on low-end devices.
 */
@Composable
fun Modifier.solaceBreath(
    enabled: Boolean = true,
    label: String = "solace_breath",
): Modifier {
    if (!enabled) return this
    val anim = SolaceTheme.animation
    val infinite = rememberInfiniteTransition(label = label)
    val scale by infinite.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(anim.durationBreath, easing = anim.easingStandard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}_scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
