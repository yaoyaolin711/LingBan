package me.rerere.rikkahub.ui.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * Lightweight press feedback — scale via graphicsLayer only.
 * Elevation is fixed (no per-frame shadow redraw).
 */
@Composable
fun rememberSolacePressState(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    restingElevation: Dp = 6.dp,
): SolacePressState {
    val pressed by interactionSource.collectIsPressedAsState()
    val anim = SolaceTheme.animation
    val scale by animateFloatAsState(
        targetValue = if (pressed) anim.pressScale else 1f,
        animationSpec = anim.fastTween(),
        label = "solace_press_scale",
    )
    return SolacePressState(
        interactionSource = interactionSource,
        scale = scale,
        elevation = restingElevation,
        pressed = pressed,
    )
}

data class SolacePressState(
    val interactionSource: MutableInteractionSource,
    val scale: Float,
    val elevation: Dp,
    val pressed: Boolean,
)

fun Modifier.solacePressTransform(state: SolacePressState): Modifier =
    this.graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
    }
