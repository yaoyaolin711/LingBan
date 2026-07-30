package me.rerere.rikkahub.ui.components.solace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import me.rerere.rikkahub.ui.modifier.rememberSolacePressState
import me.rerere.rikkahub.ui.modifier.solacePressTransform
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SolaceShapesDefault
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * Glassmorphism container — translucent fill + hairline border.
 * No realtime blur (keeps 60fps). Theme / dark mode via [SolaceTheme].
 */
@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = SolaceShapesDefault.glass,
    contentPadding: Dp = 0.dp,
    elevation: Dp? = null,
    color: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val dark = LocalDarkMode.current
    val fill = color ?: colors.glassFill.copy(alpha = if (dark) 0.72f else 0.78f)
    val stroke = BorderStroke(1.dp, colors.glassBorder)

    if (onClick != null) {
        val press = rememberSolacePressState()
        Surface(
            onClick = onClick,
            modifier = modifier.solacePressTransform(press),
            shape = shape,
            color = fill,
            border = stroke,
            tonalElevation = 0.dp,
            shadowElevation = elevation ?: press.elevation,
            interactionSource = press.interactionSource,
        ) {
            Box(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = fill,
            border = stroke,
            tonalElevation = 0.dp,
            shadowElevation = elevation ?: 0.dp,
        ) {
            Box(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * Column-scoped glass container for form / card layouts.
 */
@Composable
fun GlassContainerColumn(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = SolaceShapesDefault.glass,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassContainer(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        contentPadding = 0.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
