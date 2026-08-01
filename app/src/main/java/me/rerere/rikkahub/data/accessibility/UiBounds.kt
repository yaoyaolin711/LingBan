package me.rerere.rikkahub.data.accessibility

import android.graphics.Rect
import kotlinx.serialization.Serializable

/**
 * Screen-space bounding box for a UI node / observation element.
 */
@Serializable
data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun toCompactString(): String = "$left,$top,$right,$bottom"

    /** Intersection-over-union with another box; 0 when either is empty. */
    fun iou(other: UiBounds): Float {
        if (isEmpty || other.isEmpty) return 0f
        val ix1 = maxOf(left, other.left)
        val iy1 = maxOf(top, other.top)
        val ix2 = minOf(right, other.right)
        val iy2 = minOf(bottom, other.bottom)
        val iw = (ix2 - ix1).coerceAtLeast(0)
        val ih = (iy2 - iy1).coerceAtLeast(0)
        val inter = iw.toLong() * ih
        if (inter <= 0L) return 0f
        val union = width.toLong() * height + other.width.toLong() * other.height - inter
        return if (union <= 0L) 0f else inter.toFloat() / union.toFloat()
    }

    fun containsPoint(x: Int, y: Int): Boolean =
        x in left..right && y in top..bottom

    /** Center-to-center distance in pixels. */
    fun centerDistance(other: UiBounds): Float {
        val dx = (centerX - other.centerX).toFloat()
        val dy = (centerY - other.centerY).toFloat()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        val EMPTY = UiBounds(0, 0, 0, 0)

        fun fromRect(rect: Rect): UiBounds =
            UiBounds(rect.left, rect.top, rect.right, rect.bottom)

        /** Parse `"left,top,right,bottom"` compact bounds. */
        fun parseCompact(raw: String): UiBounds? {
            val parts = raw.split(',')
            if (parts.size != 4) return null
            val l = parts[0].trim().toIntOrNull() ?: return null
            val t = parts[1].trim().toIntOrNull() ?: return null
            val r = parts[2].trim().toIntOrNull() ?: return null
            val b = parts[3].trim().toIntOrNull() ?: return null
            return UiBounds(l, t, r, b)
        }
    }
}
