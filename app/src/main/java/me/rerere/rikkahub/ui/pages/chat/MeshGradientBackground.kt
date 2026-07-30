package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SolaceTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Solace romantic aurora — static wash + 2 soft blobs in an isolated leaf
 * so sibling [content] does not recompose every frame.
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = SolaceTheme.colorScheme
    val dark = LocalDarkMode.current
    val baseGradient = remember(colors, dark) {
        if (dark) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to colors.auroraTop,
                    0.35f to colors.auroraMid,
                    1.0f to colors.auroraBottom,
                )
            )
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to colors.auroraBlobPrimary.copy(alpha = 0.65f),
                    0.22f to colors.champagne,
                    0.55f to colors.auroraMid,
                    1.0f to colors.auroraBottom,
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseGradient),
    ) {
        // Leaf composable — InfiniteTransition stays here only
        AuroraBlobLayer(
            primary = colors.auroraBlobPrimary,
            secondary = colors.auroraBlobSecondary,
            primaryAlpha = if (dark) 0.42f else 0.52f,
            secondaryAlpha = if (dark) 0.32f else 0.40f,
            durationMs = SolaceTheme.animation.durationAurora,
        )
        content()
    }
}

@Composable
private fun AuroraBlobLayer(
    primary: Color,
    secondary: Color,
    primaryAlpha: Float,
    secondaryAlpha: Float,
    durationMs: Int,
) {
    val transition = rememberInfiniteTransition(label = "solace_aurora")
    val p1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "p1",
    )
    val p2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween((durationMs * 1.2f).toInt(), easing = LinearEasing)),
        label = "p2",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        drawBlob(
            center = Offset(
                w * 0.48f + sin(p1) * w * 0.28f,
                h * 0.12f + cos(p1 * 1.1f) * h * 0.12f,
            ),
            radius = r * 0.36f,
            color = primary,
            centerAlpha = primaryAlpha,
        )
        drawBlob(
            center = Offset(
                w * 0.22f + sin(p2 + PI.toFloat() * 0.55f) * w * 0.22f,
                h * 0.30f + cos(p2) * h * 0.14f,
            ),
            radius = r * 0.28f,
            color = secondary,
            centerAlpha = secondaryAlpha,
        )
    }
}

private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    color: Color,
    centerAlpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = centerAlpha),
                1f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
