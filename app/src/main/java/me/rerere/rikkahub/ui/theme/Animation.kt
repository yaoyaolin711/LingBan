package me.rerere.rikkahub.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset

/**
 * Solace motion — 以流畅为主：短淡入淡出 + 轻量位移，去掉缩放/长呼吸等花哨动效。
 */
@Immutable
data class SolaceAnimation(
    val easingStandard: Easing = FastOutSlowInEasing,
    val easingEmphasized: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
    val easingDecelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
    val durationFast: Int = 120,
    val durationMedium: Int = 180,
    val durationSlow: Int = 220,
    val durationBreath: Int = 3200,
    val durationOrbit: Int = 14000,
    val durationAurora: Int = 7000,
    /** 页面过渡不再缩放，保留字段兼容调用方 */
    val pageScale: Float = 1f,
    /** 按压缩放关闭，避免列表/按钮每帧 graphicsLayer */
    val pressScale: Float = 1f,
) {
    fun <T> fastTween(): TweenSpec<T> = tween(durationFast, easing = easingStandard)
    fun <T> mediumTween(): TweenSpec<T> = tween(durationMedium, easing = easingEmphasized)
    fun <T> slowTween(): TweenSpec<T> = tween(durationSlow, easing = easingEmphasized)

    fun <T> softSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun offsetTween(duration: Int = durationMedium): FiniteAnimationSpec<IntOffset> =
        tween(duration, easing = easingEmphasized)

    /** 前进：淡入 + 轻微水平滑入 */
    fun pageForward(): ContentTransform {
        val enter: EnterTransition =
            fadeIn(mediumTween()) +
                slideInHorizontally(animationSpec = offsetTween()) { it / 10 }
        val exit: ExitTransition =
            fadeOut(fastTween()) +
                slideOutHorizontally(animationSpec = offsetTween(durationFast)) { -it / 16 }
        return enter togetherWith exit
    }

    /** 返回 */
    fun pagePop(): ContentTransform {
        val enter: EnterTransition =
            fadeIn(mediumTween()) +
                slideInHorizontally(animationSpec = offsetTween()) { -it / 16 }
        val exit: ExitTransition =
            fadeOut(fastTween()) +
                slideOutHorizontally(animationSpec = offsetTween(durationFast)) { it / 10 }
        return enter togetherWith exit
    }

    /** Tab / 内容切换：仅淡入淡出 */
    fun pageFade(): ContentTransform =
        fadeIn(mediumTween()) togetherWith fadeOut(fastTween())
}

val SolaceAnimationDefault = SolaceAnimation()
