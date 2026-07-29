package com.agent.chat.ui.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent.chat.ui.home.AiOrb
import com.agent.chat.ui.home.AiOrbState
import com.agent.chat.ui.theme.AgentThemeColors

/**
 * AI 专属 Loading：以 Orb 呼吸/思考态代替传统 Spinner。
 * 纯 Compose Canvas，目标 60fps；后续可用 Lottie/Rive 资源替换内核而不改 API。
 */
@Composable
fun AiLoadingIndicator(
    modifier: Modifier = Modifier,
    label: String = "灵伴思考中…",
    size: Dp = 56.dp,
    state: AiOrbState = AiOrbState.Thinking,
    showLabel: Boolean = true,
) {
    val colors = AgentThemeColors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AiOrb(
            state = state,
            size = size,
        )
        if (showLabel && label.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
