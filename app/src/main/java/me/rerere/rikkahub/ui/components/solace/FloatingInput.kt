package me.rerere.rikkahub.ui.components.solace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SolaceInputShape
import me.rerere.rikkahub.ui.theme.SolaceInputShapeIme
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * Floating chat input shell — rounded glass surface, no realtime blur.
 * Hosts arbitrary input chrome (text field, actions) via [content].
 */
@Composable
fun FloatingInput(
    modifier: Modifier = Modifier,
    imeVisible: Boolean = false,
    contentPaddingHorizontal: Dp = 12.dp,
    contentPaddingVertical: Dp = 8.dp,
    elevation: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val dark = LocalDarkMode.current
    val shape = if (imeVisible) SolaceInputShapeIme else SolaceInputShape
    val fillAlpha = if (dark) 0.88f else 0.82f

    GlassContainer(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        elevation = elevation,
        color = colors.surface.copy(alpha = fillAlpha),
        contentPadding = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = contentPaddingHorizontal,
                vertical = contentPaddingVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/** Pill / circular floating action surface used beside [FloatingInput]. */
val FloatingInputPillShape = RoundedCornerShape(50)
