package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Solace shape system — soft luxury corners (not playful / game-like).
 */
@Immutable
data class SolaceShapes(
    val extraSmall: Shape = RoundedCornerShape(8.dp),
    val small: Shape = RoundedCornerShape(12.dp),
    val medium: Shape = RoundedCornerShape(16.dp),
    val large: Shape = RoundedCornerShape(22.dp),
    val extraLarge: Shape = RoundedCornerShape(28.dp),
    val glass: Shape = RoundedCornerShape(24.dp),
    val glassTile: Shape = RoundedCornerShape(22.dp),
    val bubbleUser: Shape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 8.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp,
    ),
    val bubbleAssistant: Shape = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 22.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp,
    ),
    val pill: Shape = RoundedCornerShape(50),
    val cardGroup: Shape = RoundedCornerShape(24.dp),
)

val SolaceShapesDefault = SolaceShapes()

/** Chat input container (IME collapsed) — floating glass 32dp. */
val SolaceInputShape = RoundedCornerShape(32.dp)

/** Chat input when IME is visible — square bottom corners. */
val SolaceInputShapeIme = RoundedCornerShape(
    topStart = 32.dp,
    topEnd = 32.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp,
)

/** Material3 [Shapes] bridged from Solace tokens. */
val SolaceMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
