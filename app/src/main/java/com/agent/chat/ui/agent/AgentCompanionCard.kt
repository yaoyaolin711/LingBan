package com.agent.chat.ui.agent

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.chat.ui.components.PersonaAvatar
import com.agent.chat.ui.theme.Accent
import com.agent.chat.ui.theme.HomeCardElevation
import com.agent.chat.ui.theme.OutlineSubtle
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.SurfaceMuted
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary
import androidx.compose.material3.Surface
import kotlinx.coroutines.delay

fun agentSharedAvatarKey(personaId: String) = "agent_avatar_$personaId"
fun agentSharedCardKey(personaId: String) = "agent_card_$personaId"

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AgentCompanionCard(
    agent: AgentCardUi,
    index: Int,
    visible: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(28f) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(80L + index * 90L)
            alpha.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            offsetY.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
        }
    }

    val persona = agent.persona
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = offsetY.value
            }
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key = agentSharedCardKey(persona.id)),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceCard,
        shadowElevation = HomeCardElevation,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaAvatar(
                    name = persona.name,
                    avatar = persona.avatar,
                    size = 64.dp,
                    modifier = Modifier.sharedElement(
                        state = rememberSharedContentState(
                            key = agentSharedAvatarKey(persona.id),
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = persona.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (agent.usageCount > 0) {
                            "一起聊过 ${agent.usageCount} 次"
                        } else {
                            "还没开始对话"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = persona.description.ifBlank {
                    persona.openingLine.ifBlank { "一位愿意慢慢了解你的 AI 伙伴" }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary.copy(alpha = 0.9f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (agent.capabilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    agent.capabilities.forEach { tag ->
                        CapabilityTag(text = tag)
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceMuted)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Accent,
        )
    }
}
