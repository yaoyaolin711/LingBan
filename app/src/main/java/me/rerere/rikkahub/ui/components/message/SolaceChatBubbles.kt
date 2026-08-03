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
import me.rerere.rikkahub.data.datastore.BubbleGlassStyle
import me.rerere.rikkahub.ui.theme.SolaceShapesDefault
import me.rerere.rikkahub.ui.theme.SolaceTheme

/** 暗夜磨砂底板色（深炭灰） */
private val DarkFrostBase = Color(0xFF1C1C1E)
/** 暗夜磨砂亮边 */
private val DarkFrostRim = Color.White.copy(alpha = 0.28f)
/** 暗夜磨砂内高光（顶部略亮） */
private val DarkFrostHighlight = Color.White.copy(alpha = 0.10f)

/**
 * AI companion bubble — rose-gold gradient + hairline border (no soft shadow for scroll FPS).
 */
@Composable
fun SolaceAssistantBubble(
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    glassStyle: BubbleGlassStyle = BubbleGlassStyle.NONE,
    customColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleAssistant
    when (glassStyle) {
        BubbleGlassStyle.DARK_FROST -> {
            val base = (customColor ?: DarkFrostBase)
            val brush = Brush.verticalGradient(
                colors = listOf(
                    base.copy(alpha = 0.78f * opacity).compositeOverHighlight(),
                    base.copy(alpha = 0.62f * opacity),
                )
            )
            Surface(
                modifier = modifier,
                shape = shape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, DarkFrostRim.copy(alpha = DarkFrostRim.alpha * opacity)),
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
        BubbleGlassStyle.LIGHT_FROST,
        BubbleGlassStyle.NONE -> {
            val frosted = glassStyle == BubbleGlassStyle.LIGHT_FROST
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
    }
}

/**
 * User bubble — champagne translucent glass (optional frosted / dark frost / custom tint).
 */
@Composable
fun SolaceUserBubble(
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    glassStyle: BubbleGlassStyle = BubbleGlassStyle.NONE,
    customColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleUser
    val (fill, borderColor) = when (glassStyle) {
        BubbleGlassStyle.DARK_FROST -> {
            val base = customColor ?: DarkFrostBase
            base.copy(alpha = 0.68f * opacity) to DarkFrostRim.copy(alpha = DarkFrostRim.alpha * opacity)
        }
        BubbleGlassStyle.LIGHT_FROST -> {
            val frostMul = 0.38f
            (customColor ?: colors.champagne).copy(alpha = frostMul * opacity) to
                Color.White.copy(alpha = 0.55f * opacity)
        }
        BubbleGlassStyle.NONE -> {
            (customColor ?: colors.champagne).copy(alpha = 0.55f * opacity) to
                colors.outlineVariant.copy(alpha = 0.45f)
        }
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
        val boxMod = if (glassStyle == BubbleGlassStyle.DARK_FROST) {
            modifier
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            fill.compositeOverHighlight(),
                            fill,
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        } else {
            modifier
                .clip(shape)
                .background(fill)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        }
        Box(modifier = boxMod) {
            Column(content = content)
        }
    }
}

/** Soft top sheen for dark frost panels. */
private fun Color.compositeOverHighlight(): Color {
    val a = DarkFrostHighlight.alpha
    return Color(
        red = red * (1f - a) + DarkFrostHighlight.red * a,
        green = green * (1f - a) + DarkFrostHighlight.green * a,
        blue = blue * (1f - a) + DarkFrostHighlight.blue * a,
        alpha = alpha,
    )
}
