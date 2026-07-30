package me.rerere.rikkahub.ui.components.solace

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SolaceTheme

@Immutable
data class PremiumBarItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
)

@Immutable
data class PremiumBarCenter(
    val icon: ImageVector,
    val contentDescription: String,
)

/**
 * Premium floating bottom bar with morphing selection + optional center CTA.
 * Expects an even number of [items] (split left / right around [center]).
 */
@Composable
fun PremiumBottomBar(
    items: List<PremiumBarItem>,
    selectedKey: String,
    onItemClick: (PremiumBarItem) -> Unit,
    modifier: Modifier = Modifier,
    center: PremiumBarCenter? = null,
    centerSelected: Boolean = false,
    onCenterClick: (() -> Unit)? = null,
) {
    val colors = SolaceTheme.colorScheme
    val dark = LocalDarkMode.current
    val shape = RoundedCornerShape(32.dp)
    val mid = items.size / 2
    val left = items.take(mid)
    val right = items.drop(mid)

    GlassContainer(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        elevation = 10.dp,
        color = colors.surface.copy(alpha = if (dark) 0.82f else 0.78f),
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { item ->
                PremiumBarNavItem(
                    icon = item.icon,
                    label = item.label,
                    selected = selectedKey == item.key,
                    onClick = { onItemClick(item) },
                )
            }
            if (center != null && onCenterClick != null) {
                PremiumBarCenterButton(
                    icon = center.icon,
                    contentDescription = center.contentDescription,
                    selected = centerSelected,
                    onClick = onCenterClick,
                )
            }
            right.forEach { item ->
                PremiumBarNavItem(
                    icon = item.icon,
                    label = item.label,
                    selected = selectedKey == item.key,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun PremiumBarNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val anim = SolaceTheme.animation
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 0.18f else 0f,
        animationSpec = anim.fastTween(),
        label = "nav_pill",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = anim.mediumTween(),
        label = "nav_icon_scale",
    )
    val tint = if (selected) colors.primary else colors.secondaryText.copy(alpha = 0.75f)
    val labelColor = if (selected) colors.primary else colors.secondaryText.copy(alpha = 0.65f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.primary.copy(alpha = pillAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        Text(
            text = label,
            style = typography.labelSmall,
            color = labelColor,
            fontSize = 9.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PremiumBarCenterButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SolaceTheme.colorScheme
    val anim = SolaceTheme.animation
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = anim.mediumTween(),
        label = "nav_chat_scale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(colors.primary, colors.accent),
                )
            )
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.onPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}
