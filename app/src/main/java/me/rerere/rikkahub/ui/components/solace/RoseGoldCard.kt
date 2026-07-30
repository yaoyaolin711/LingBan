package me.rerere.rikkahub.ui.components.solace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.modifier.rememberSolacePressState
import me.rerere.rikkahub.ui.modifier.solacePressTransform
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SolaceShapesDefault
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * Premium rose-gold card — soft corners + champagne/rose wash.
 * No Material shadowElevation (avoids rectangular white flare on translucent fills).
 */
@Composable
fun RoseGoldCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = SolaceShapesDefault.extraLarge,
    contentPadding: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val dark = LocalDarkMode.current
    val press = if (onClick != null) rememberSolacePressState(restingElevation = 0.dp) else null
    // Soft tinted fill — not opaque white
    val fillAlpha = if (dark) 0.48f else 0.58f
    val gradient = Brush.linearGradient(
        colors = listOf(
            colors.champagne.copy(alpha = if (dark) 0.35f else 0.50f),
            colors.lightRose.copy(alpha = if (dark) 0.22f else 0.32f),
            colors.surface.copy(alpha = if (dark) 0.35f else 0.45f),
        )
    )

    val surfaceModifier = if (press != null) {
        modifier.fillMaxWidth().solacePressTransform(press)
    } else {
        modifier.fillMaxWidth()
    }

    val surfaceColor = colors.champagne.copy(alpha = fillAlpha)

    if (onClick != null && press != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = shape,
            color = surfaceColor,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            interactionSource = press.interactionSource,
        ) {
            Box(
                modifier = Modifier
                    .background(gradient)
                    .padding(contentPadding),
            ) {
                Column(content = content)
            }
        }
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = shape,
            color = surfaceColor,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .background(gradient)
                    .padding(contentPadding),
            ) {
                Column(content = content)
            }
        }
    }
}
