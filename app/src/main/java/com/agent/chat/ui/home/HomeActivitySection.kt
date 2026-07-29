package com.agent.chat.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.Persona
import com.agent.chat.ui.motion.floatingCardLift
import com.agent.chat.ui.theme.AgentThemeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class HomeActivityUi(
    val recentConversation: Conversation?,
    val recentMemory: Memory?,
    val recommendedPersona: Persona?,
)

@Composable
fun HomeActivitySection(
    activity: HomeActivityUi,
    visible: Boolean,
    onRecentChatClick: () -> Unit,
    onRecentMemoryClick: () -> Unit,
    onRecommendedAgentClick: () -> Unit,
    modifier: Modifier = Modifier,
    scrollPx: Float = 0f,
) {
    val colors = AgentThemeColors
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = (screenWidth - 40.dp) * 0.86f

    val titleAlpha = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            titleAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "最近活动",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer { alpha = titleAlpha.value },
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        ) {
            itemsIndexed(
                items = listOf(
                    HomeCardKind.Chat,
                    HomeCardKind.Memory,
                    HomeCardKind.Agent,
                ),
                key = { _, kind -> kind.name },
            ) { index, kind ->
                StaggeredCard(
                    visible = visible,
                    delayMillis = 160 + index * 100,
                    modifier = Modifier
                        .width(cardWidth)
                        .graphicsLayer {
                            translationY = floatingCardLift(scrollPx, index)
                        },
                ) {
                    when (kind) {
                        HomeCardKind.Chat -> SoftActivityCard(
                            title = "最近聊天",
                            subtitle = activity.recentConversation?.let {
                                it.lastMessage.ifBlank { it.title }.ifBlank { "继续刚才的对话" }
                            } ?: "还没有对话，点此开始",
                            icon = HomeActivityIcons.Chat,
                            onClick = onRecentChatClick,
                        )
                        HomeCardKind.Memory -> SoftActivityCard(
                            title = "最近记忆",
                            subtitle = activity.recentMemory?.content?.take(48)
                                ?: "聊天后，我会慢慢记住关于你的事",
                            icon = HomeActivityIcons.Memory,
                            onClick = onRecentMemoryClick,
                        )
                        HomeCardKind.Agent -> SoftActivityCard(
                            title = "推荐 Agent",
                            subtitle = activity.recommendedPersona?.let {
                                it.description.ifBlank { it.name }
                            } ?: "去 Agent Center 选择一位伙伴",
                            icon = HomeActivityIcons.Agent,
                            onClick = onRecommendedAgentClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredCard(
    visible: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(28f) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(delayMillis.toLong())
            launch {
                alpha.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            }
            offsetY.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
        }
    }

    Column(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = offsetY.value
        },
    ) {
        content()
    }
}

private enum class HomeCardKind {
    Chat,
    Memory,
    Agent,
}
