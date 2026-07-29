package com.agent.chat.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 左缘滑动返回（配合系统返回手势，提供更强的「可返回」反馈）。
 */
@Composable
fun SwipeBackContainer(
    enabled: Boolean = true,
    edgeWidth: Dp = 28.dp,
    threshold: Dp = 96.dp,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val edgePx = with(density) { edgeWidth.toPx() }
    val thresholdPx = with(density) { threshold.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var tracking by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = dragOffset.coerceAtLeast(0f)
                alpha = 1f - (dragOffset / (thresholdPx * 2.5f)).coerceIn(0f, 0.25f)
            },
    ) {
        content()
        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(edgeWidth)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                tracking = if (offset.x <= edgePx) 1f else 0f
                            },
                            onDragEnd = {
                                if (tracking > 0f && dragOffset >= thresholdPx) {
                                    onBack()
                                }
                                dragOffset = 0f
                                tracking = 0f
                            },
                            onDragCancel = {
                                dragOffset = 0f
                                tracking = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (tracking > 0f) {
                                    dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                }
                            },
                        )
                    },
            )
        }
    }
}

/** 列表顶部/底部渐变透明遮罩，强化空间深度。 */
@Composable
fun BoxScope.ScrollEdgeFade(
    top: Boolean = true,
    bottom: Boolean = true,
    height: Dp = 40.dp,
    color: Color,
) {
    if (top) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(height)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0f)),
                    ),
                ),
        )
    }
    if (bottom) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0f), color),
                    ),
                ),
        )
    }
}

/** 根据滚动偏移产生视差位移（像素）。 */
fun parallaxOffset(scrollPx: Float, factor: Float = 0.28f): Float =
    -scrollPx * factor

/** 卡片随滚动轻微上浮。 */
fun floatingCardLift(scrollPx: Float, index: Int, amplitude: Float = 4f): Float {
    val wave = kotlin.math.sin((scrollPx * 0.01f) + index * 0.7f)
    return wave * amplitude
}

@Suppress("unused")
private fun absUnused(v: Float) = abs(v)
