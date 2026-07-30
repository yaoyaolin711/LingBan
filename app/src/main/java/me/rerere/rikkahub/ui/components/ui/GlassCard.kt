package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.solace.GlassContainer
import me.rerere.rikkahub.ui.components.solace.GlassContainerColumn
import me.rerere.rikkahub.ui.modifier.rememberSolacePressState
import me.rerere.rikkahub.ui.modifier.solacePressTransform
import me.rerere.rikkahub.ui.theme.SolaceShapesDefault
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * VisionOS-inspired frosted glass — delegates to [GlassContainerColumn].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = SolaceShapesDefault.glass,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassContainerColumn(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun GlassTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SolaceShapesDefault.glassTile,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    GlassContainer(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        color = colors.surfaceContainerLow.copy(alpha = 0.85f),
        contentPadding = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SolacePrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SolaceShapesDefault.large,
    content: @Composable () -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val press = rememberSolacePressState()
    Surface(
        onClick = onClick,
        modifier = modifier.solacePressTransform(press),
        shape = shape,
        color = colors.primary,
        contentColor = colors.onPrimary,
        shadowElevation = press.elevation,
        tonalElevation = 0.dp,
        interactionSource = press.interactionSource,
    ) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            content()
        }
    }
}

@Composable
fun solaceGlassBorderColor(): Color = SolaceTheme.colorScheme.glassBorder
