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
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleAssistant
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.lightRose.copy(alpha = 0.92f * opacity),
                        colors.champagne.copy(alpha = 0.88f * opacity),
                        colors.surface.copy(alpha = 0.95f * opacity),
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(content = content)
    }
}

/**
 * User bubble — champagne translucent glass.
 */
@Composable
fun SolaceUserBubble(
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleUser
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = colors.champagne.copy(alpha = 0.55f * opacity),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
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
                .background(colors.champagne.copy(alpha = 0.55f * opacity))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(content = content)
        }
    }
}
