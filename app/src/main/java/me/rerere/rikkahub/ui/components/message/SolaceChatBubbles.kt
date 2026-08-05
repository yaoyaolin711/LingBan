package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import me.rerere.rikkahub.data.datastore.BubbleFillStyle
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
    fillStyle: BubbleFillStyle = BubbleFillStyle.GRADIENT,
    customColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleAssistant
    val opacityClamped = opacity.coerceIn(0f, 1f)
    when (glassStyle) {
        BubbleGlassStyle.DARK_FROST -> {
            val base = (customColor ?: DarkFrostBase)
            val brush = Brush.verticalGradient(
                colors = listOf(
                    base.copy(alpha = 0.78f * opacityClamped).compositeOverHighlight(),
                    base.copy(alpha = 0.62f * opacityClamped),
                )
            )
            Surface(
                modifier = modifier,
                shape = shape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, DarkFrostRim.copy(alpha = DarkFrostRim.alpha * opacityClamped)),
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
        BubbleGlassStyle.LIGHT_FROST -> {
            val alphaScale = opacityClamped * 0.42f
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
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f * opacityClamped)),
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
        BubbleGlassStyle.NONE -> {
            // opacity=100% → 完全不透明；低于 100% 再按比例变透
            val brush = when (fillStyle) {
                BubbleFillStyle.SOLID -> Brush.linearGradient(
                    colors = listOf(
                        (customColor ?: colors.lightRose).copy(alpha = opacityClamped),
                        (customColor ?: colors.lightRose).copy(alpha = opacityClamped),
                    )
                )
                BubbleFillStyle.GRADIENT -> if (customColor != null) {
                    val base = customColor.copy(alpha = opacityClamped)
                    Brush.linearGradient(
                        colors = listOf(
                            base,
                            base.copy(
                                red = (base.red * 0.92f).coerceIn(0f, 1f),
                                green = (base.green * 0.92f).coerceIn(0f, 1f),
                                blue = (base.blue * 0.92f).coerceIn(0f, 1f),
                            ),
                            colors.surface.copy(alpha = opacityClamped),
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            colors.lightRose.copy(alpha = opacityClamped),
                            colors.champagne.copy(alpha = opacityClamped),
                            colors.surface.copy(alpha = opacityClamped),
                        )
                    )
                }
            }
            Surface(
                modifier = modifier,
                shape = shape,
                color = Color.Transparent,
                border = null,
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
 * User bubble — champagne glass (optional frosted / dark frost / custom tint).
 * Theme-default (NONE) at opacity=100% is fully opaque.
 */
@Composable
fun SolaceUserBubble(
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    glassStyle: BubbleGlassStyle = BubbleGlassStyle.NONE,
    fillStyle: BubbleFillStyle = BubbleFillStyle.GRADIENT,
    customColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleUser
    val opacityClamped = opacity.coerceIn(0f, 1f)
    val defaultUserBase = customColor ?: colors.champagne
    val (fill, borderColor) = when (glassStyle) {
        BubbleGlassStyle.DARK_FROST -> {
            val base = customColor ?: DarkFrostBase
            base.copy(alpha = 0.68f * opacityClamped) to DarkFrostRim.copy(alpha = DarkFrostRim.alpha * opacityClamped)
        }
        BubbleGlassStyle.LIGHT_FROST -> {
            val frostMul = 0.38f
            (customColor ?: colors.champagne).copy(alpha = frostMul * opacityClamped) to
                Color.White.copy(alpha = 0.55f * opacityClamped)
        }
        BubbleGlassStyle.NONE -> {
            // 主题默认：不透明度设置直接映射到底色 alpha，100% 完全不透
            defaultUserBase.copy(alpha = opacityClamped) to
                colors.outlineVariant.copy(alpha = if (opacityClamped >= 1f) 0.35f else 0.45f * opacityClamped)
        }
    }
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            defaultUserBase.copy(alpha = opacityClamped),
            defaultUserBase.copy(
                red = (defaultUserBase.red * 0.94f).coerceIn(0f, 1f),
                green = (defaultUserBase.green * 0.94f).coerceIn(0f, 1f),
                blue = (defaultUserBase.blue * 0.94f).coerceIn(0f, 1f),
                alpha = opacityClamped,
            ),
            colors.surface.copy(alpha = opacityClamped * 0.92f),
        )
    )
    if (onClick != null) {
        val clickableModifier = when {
            glassStyle == BubbleGlassStyle.DARK_FROST -> {
                Modifier
                    .clip(shape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                fill.compositeOverHighlight(),
                                fill,
                            )
                        )
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            }
            glassStyle == BubbleGlassStyle.NONE && fillStyle == BubbleFillStyle.GRADIENT -> {
                Modifier
                    .clip(shape)
                    .background(gradientBrush)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            }
            else -> {
                Modifier
                    .clip(shape)
                    .background(fill)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            }
        }
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Box(modifier = clickableModifier) {
                Column(content = content)
            }
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
        } else if (glassStyle == BubbleGlassStyle.NONE && fillStyle == BubbleFillStyle.GRADIENT) {
            modifier
                .clip(shape)
                .background(gradientBrush)
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
