package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.SolaceShapesDefault
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * AI companion bubble — rose-gold gradient + hairline border (no soft shadow for scroll FPS).
 */
@Composable
fun SolaceAssistantBubble(
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    frosted: Boolean = false,
    customColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleAssistant
    val frostMul = if (frosted) 0.42f else 1f
    val alphaScale = opacity * frostMul
    val border = if (frosted) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.55f * opacity))
    } else {
        null
    }
    val brush = if (customColor != null) {
        val base = customColor
        Brush.linearGradient(
            colors = listOf(
                base.copy(alpha = 0.92f * alphaScale),
                base.copy(alpha = 0.78f * alphaScale),
                colors.surface.copy(alpha = 0.90f * alphaScale),
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                colors.lightRose.copy(alpha = 0.92f * alphaScale),
                colors.champagne.copy(alpha = 0.88f * alphaScale),
                colors.surface.copy(alpha = 0.95f * alphaScale),
            )
        )
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = border,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .background(brush = brush)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(content = content)
        }
    }
}

/**
 * User bubble — champagne translucent glass (optional frosted / custom tint).
 */
@Composable
fun SolaceUserBubble(
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    frosted: Boolean = false,
    customColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleUser
    val frostMul = if (frosted) 0.38f else 0.55f
    val fill = (customColor ?: colors.champagne).copy(alpha = frostMul * opacity)
    val borderColor = if (frosted) {
        Color.White.copy(alpha = 0.55f * opacity)
    } else {
        colors.outlineVariant.copy(alpha = 0.45f)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = fill,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                content = content,
            )
        }
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(fill)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(content = content)
        }
    }
}
