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
import me.rerere.rikkahub.ui.theme.SolaceColorScheme
import me.rerere.rikkahub.ui.theme.SolaceShapesDefault
import me.rerere.rikkahub.ui.theme.SolaceTheme

/** 暗夜磨砂：近黑炭底板，厚重雾面 */
private val DarkFrostBase = Color(0xFF0E0E10)
private val DarkFrostLift = Color(0xFF2C2C32)
/** 暗夜磨砂亮边：更实，强调厚玻璃轮廓 */
private val DarkFrostRim = Color.White.copy(alpha = 0.42f)
/** 暗夜顶部强雾面高光 */
private val DarkFrostSheen = Color.White.copy(alpha = 0.26f)

/** 清透磨砂描边：细亮玻璃边 */
private val LightFrostRim = Color.White.copy(alpha = 0.70f)

private fun solidBrush(color: Color): Brush =
    Brush.linearGradient(listOf(color, color))

private fun lifted(color: Color, factor: Float = 1.15f): Color = color.copy(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
)

private fun darkFrostBrush(
    fillStyle: BubbleFillStyle,
    opacity: Float,
    customColor: Color?,
): Brush {
    val base = customColor ?: DarkFrostBase
    val lift = if (customColor != null) lifted(customColor) else DarkFrostLift
    return when (fillStyle) {
        BubbleFillStyle.SOLID -> solidBrush(base.copy(alpha = 0.96f * opacity))
        BubbleFillStyle.GRADIENT -> Brush.verticalGradient(
            colors = listOf(
                DarkFrostSheen.copy(alpha = 0.30f * opacity),
                lift.copy(alpha = 0.94f * opacity),
                base.copy(alpha = 0.96f * opacity),
                base.copy(alpha = 0.98f * opacity),
            )
        )
    }
}

private fun lightFrostBrush(
    fillStyle: BubbleFillStyle,
    opacity: Float,
    customColor: Color?,
    colors: SolaceColorScheme,
    fallbackTint: Color,
): Brush {
    val alphaScale = opacity * 0.26f
    val tint = customColor ?: fallbackTint
    return when (fillStyle) {
        BubbleFillStyle.SOLID -> solidBrush(tint.copy(alpha = 0.34f * opacity))
        BubbleFillStyle.GRADIENT -> if (customColor != null) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f * opacity),
                    customColor.copy(alpha = 0.70f * alphaScale),
                    colors.surface.copy(alpha = 0.55f * alphaScale),
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.58f * opacity),
                    colors.lightRose.copy(alpha = 0.72f * alphaScale),
                    colors.champagne.copy(alpha = 0.55f * alphaScale),
                    colors.surface.copy(alpha = 0.48f * alphaScale),
                )
            )
        }
    }
}

private fun themeDefaultBrush(
    fillStyle: BubbleFillStyle,
    opacity: Float,
    customColor: Color?,
    colors: SolaceColorScheme,
    solidFallback: Color,
    useChampagneGradient: Boolean,
): Brush {
    val solid = (customColor ?: solidFallback).copy(alpha = opacity)
    return when (fillStyle) {
        BubbleFillStyle.SOLID -> solidBrush(solid)
        BubbleFillStyle.GRADIENT -> if (customColor != null) {
            val base = customColor.copy(alpha = opacity)
            Brush.linearGradient(
                colors = listOf(
                    base,
                    lifted(base, 0.94f).copy(alpha = opacity),
                    colors.surface.copy(alpha = opacity * if (useChampagneGradient) 0.92f else 1f),
                )
            )
        } else if (useChampagneGradient) {
            Brush.linearGradient(
                colors = listOf(
                    solidFallback.copy(alpha = opacity),
                    lifted(solidFallback, 0.94f).copy(alpha = opacity),
                    colors.surface.copy(alpha = opacity * 0.92f),
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    colors.lightRose.copy(alpha = opacity),
                    colors.champagne.copy(alpha = opacity),
                    colors.surface.copy(alpha = opacity),
                )
            )
        }
    }
}

private fun borderFor(
    glassStyle: BubbleGlassStyle,
    showBorder: Boolean,
    opacity: Float,
    outlineVariant: Color,
): BorderStroke? {
    if (!showBorder) return null
    return when (glassStyle) {
        BubbleGlassStyle.DARK_FROST -> BorderStroke(
            1.5.dp,
            DarkFrostRim.copy(alpha = DarkFrostRim.alpha * opacity),
        )
        BubbleGlassStyle.LIGHT_FROST -> BorderStroke(
            1.dp,
            LightFrostRim.copy(alpha = LightFrostRim.alpha * opacity),
        )
        BubbleGlassStyle.NONE -> BorderStroke(
            1.dp,
            outlineVariant.copy(alpha = if (opacity >= 1f) 0.35f else 0.45f * opacity),
        )
    }
}

/**
 * AI companion bubble — rose-gold gradient + optional border (no soft shadow for scroll FPS).
 */
@Composable
fun SolaceAssistantBubble(
    modifier: Modifier = Modifier,
    opacity: Float = 1f,
    glassStyle: BubbleGlassStyle = BubbleGlassStyle.NONE,
    fillStyle: BubbleFillStyle = BubbleFillStyle.GRADIENT,
    customColor: Color? = null,
    showBorder: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleAssistant
    val opacityClamped = opacity.coerceIn(0f, 1f)
    val brush = when (glassStyle) {
        BubbleGlassStyle.DARK_FROST -> darkFrostBrush(fillStyle, opacityClamped, customColor)
        BubbleGlassStyle.LIGHT_FROST -> lightFrostBrush(
            fillStyle = fillStyle,
            opacity = opacityClamped,
            customColor = customColor,
            colors = colors,
            fallbackTint = colors.lightRose,
        )
        BubbleGlassStyle.NONE -> themeDefaultBrush(
            fillStyle = fillStyle,
            opacity = opacityClamped,
            customColor = customColor,
            colors = colors,
            solidFallback = colors.lightRose,
            useChampagneGradient = false,
        )
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = borderFor(glassStyle, showBorder, opacityClamped, colors.outlineVariant),
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
    showBorder: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val shape = SolaceShapesDefault.bubbleUser
    val opacityClamped = opacity.coerceIn(0f, 1f)
    val defaultUserBase = customColor ?: colors.champagne
    val fillBrush = when (glassStyle) {
        BubbleGlassStyle.DARK_FROST -> darkFrostBrush(fillStyle, opacityClamped, customColor)
        BubbleGlassStyle.LIGHT_FROST -> lightFrostBrush(
            fillStyle = fillStyle,
            opacity = opacityClamped,
            customColor = customColor,
            colors = colors,
            fallbackTint = defaultUserBase,
        )
        BubbleGlassStyle.NONE -> themeDefaultBrush(
            fillStyle = fillStyle,
            opacity = opacityClamped,
            customColor = customColor,
            colors = colors,
            solidFallback = defaultUserBase,
            useChampagneGradient = true,
        )
    }

    val contentModifier = Modifier
        .clip(shape)
        .background(brush = fillBrush)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 12.dp)

    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = borderFor(glassStyle, showBorder, opacityClamped, colors.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = contentModifier) {
            Column(content = content)
        }
    }
}
