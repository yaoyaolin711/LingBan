package me.rerere.rikkahub.overlay.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle

/**
 * 桌宠旁侧快捷菜单（头像 / 像素模式共用）。
 */
@Composable
fun PetQuickActionsPanel(
    visible: Boolean,
    onOpenChat: () -> Unit,
    onHidePet: () -> Unit,
) {
    val panelBg = Color.Black.copy(alpha = 0.72f)
    val onPanel = Color.White.copy(alpha = 0.95f)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) +
            slideInHorizontally(tween(200)) { it / 4 } +
            scaleIn(initialScale = 0.92f, animationSpec = tween(200)),
        exit = fadeOut(tween(120)),
    ) {
        Column(
            modifier = Modifier
                .padding(start = 6.dp)
                .background(panelBg, RoundedCornerShape(14.dp))
                .padding(vertical = 4.dp),
        ) {
            PetQuickActionRow(
                icon = Lucide.MessageCircle,
                label = "打开对话",
                color = onPanel,
                onClick = onOpenChat,
            )
            PetQuickActionRow(
                icon = Lucide.EyeOff,
                label = "收起桌宠",
                color = onPanel,
                onClick = onHidePet,
            )
        }
    }
}

@Composable
private fun PetQuickActionRow(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
