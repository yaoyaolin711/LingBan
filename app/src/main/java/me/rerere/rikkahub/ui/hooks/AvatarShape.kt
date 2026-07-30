package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import kotlin.math.roundToInt

/**
 * Avatar clip shape. InfiniteTransition only while [loading] to avoid
 * continuous recomposition of idle avatars across the app.
 */
@Composable
fun rememberAvatarShape(loading: Boolean): Shape {
    if (!loading) return CircleShape
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_shape")
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
        ),
        label = "avatar_angle",
    )
    return MaterialShapes.Cookie6Sided.toShape(rotateAngle.roundToInt())
}
