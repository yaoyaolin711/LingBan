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
 * Premium rose-gold card — soft corners, champagne/rose gradient, animated press shadow.
 * Colors come from [SolaceTheme]; adapts for dark mode via [LocalDarkMode].
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
    val press = if (onClick != null) rememberSolacePressState() else null
    val fillAlpha = if (dark) 0.55f else 0.72f
    val gradient = Brush.linearGradient(
        colors = listOf(
            colors.surface.copy(alpha = if (dark) 0.4f else 0.55f),
            colors.champagne.copy(alpha = if (dark) 0.28f else 0.42f),
            colors.lightRose.copy(alpha = if (dark) 0.18f else 0.28f),
        )
    )

    val surfaceModifier = if (press != null) {
        modifier.fillMaxWidth().solacePressTransform(press)
    } else {
        modifier.fillMaxWidth()
    }

    if (onClick != null && press != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = shape,
            color = colors.surface.copy(alpha = fillAlpha),
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = press.elevation,
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
            color = colors.surface.copy(alpha = fillAlpha),
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 6.dp,
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
