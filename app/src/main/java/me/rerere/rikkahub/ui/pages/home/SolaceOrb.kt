package me.rerere.rikkahub.ui.pages.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SolaceOrb(
    onClick: () -> Unit,
    size: Dp = 220.dp,
) {
    val infinite = rememberInfiniteTransition(label = "solace_orb")
    val breath by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val core = Color(0xFFFFF8F2)
    val mid = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val deep = MaterialTheme.colorScheme.primary
    val halo = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val particle = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = breath
                scaleY = breath
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(halo, Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(core, mid, deep),
                    center = c,
                    radius = r * 0.62f,
                ),
                radius = r * 0.58f,
                center = c,
            )
            drawCircle(
                color = deep.copy(alpha = 0.35f),
                radius = r * 0.58f,
                center = c,
                style = Stroke(width = r * 0.03f),
            )
            val orbitR = r * 0.78f
            for (i in 0 until 6) {
                val a = phase + i * (2f * PI / 6f).toFloat() +
                    Math.toRadians(spin.toDouble()).toFloat()
                val p = Offset(c.x + cos(a) * orbitR, c.y + sin(a) * orbitR)
                drawCircle(color = particle, radius = r * 0.035f, center = p)
            }
        }
    }
}
