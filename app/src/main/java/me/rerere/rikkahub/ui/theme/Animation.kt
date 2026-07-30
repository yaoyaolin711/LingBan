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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset

/**
 * Solace motion system — iOS-like smoothness at 60fps.
 * Prefer short tweens over heavy springs; avoid large scale jumps.
 */
@Immutable
data class SolaceAnimation(
    val easingStandard: Easing = FastOutSlowInEasing,
    /** Near-iOS cubic: fast start, soft settle. */
    val easingEmphasized: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
    val easingDecelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
    val durationFast: Int = 160,
    val durationMedium: Int = 240,
    val durationSlow: Int = 320,
    /** Soft breath — long period, tiny amplitude (cheap InfiniteTransition). */
    val durationBreath: Int = 3200,
    val durationOrbit: Int = 14000,
    val durationAurora: Int = 7000,
    val pageScale: Float = 0.96f,
    val pressScale: Float = 0.97f,
) {
    fun <T> fastTween(): TweenSpec<T> = tween(durationFast, easing = easingStandard)
    fun <T> mediumTween(): TweenSpec<T> = tween(durationMedium, easing = easingEmphasized)
    fun <T> slowTween(): TweenSpec<T> = tween(durationSlow, easing = easingEmphasized)

    fun <T> softSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun offsetTween(duration: Int = durationMedium): FiniteAnimationSpec<IntOffset> =
        tween(duration, easing = easingEmphasized)

    /** Push-forward page transition (fade + soft slide + micro scale). */
    fun pageForward(): ContentTransform {
        val enter: EnterTransition =
            fadeIn(mediumTween()) +
                slideInHorizontally(animationSpec = offsetTween()) { it / 5 } +
                scaleIn(initialScale = pageScale, animationSpec = mediumTween())
        val exit: ExitTransition =
            fadeOut(fastTween()) +
                slideOutHorizontally(animationSpec = offsetTween(durationFast)) { -it / 12 } +
                scaleOut(targetScale = pageScale, animationSpec = fastTween())
        return enter togetherWith exit
    }

    /** Pop / back transition. */
    fun pagePop(): ContentTransform {
        val enter: EnterTransition =
            fadeIn(mediumTween()) +
                slideInHorizontally(animationSpec = offsetTween()) { -it / 12 } +
                scaleIn(initialScale = pageScale, animationSpec = mediumTween())
        val exit: ExitTransition =
            fadeOut(fastTween()) +
                slideOutHorizontally(animationSpec = offsetTween(durationFast)) { it / 5 } +
                scaleOut(targetScale = pageScale, animationSpec = fastTween())
        return enter togetherWith exit
    }

    /** Cross-fade for root tabs (Home / Chat shell). */
    fun pageFade(): ContentTransform =
        fadeIn(mediumTween()) + scaleIn(initialScale = 0.98f, animationSpec = mediumTween()) togetherWith
            fadeOut(fastTween()) + scaleOut(targetScale = 0.98f, animationSpec = fastTween())
}

val SolaceAnimationDefault = SolaceAnimation()
