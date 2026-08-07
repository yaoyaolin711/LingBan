package me.rerere.rikkahub.ui.pages.voicecall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * 通话中阶段指示、打断提示与最近对话摘要。
 */
@Composable
fun VoiceCallLiveStatus(
    ui: VoiceCallUiState,
    modifier: Modifier = Modifier,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val livePhase = ui.phase in setOf(
        VoiceCallPhase.Listening,
        VoiceCallPhase.Thinking,
        VoiceCallPhase.Speaking,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (livePhase) {
            VoiceCallPhaseIndicator(phase = ui.phase, bargeInActive = ui.bargeInActive)
        }

        if (ui.phase == VoiceCallPhase.Speaking && ui.bargeInActive) {
            Text(
                text = "正在说话 · 可直接说话打断",
                style = typography.labelMedium,
                color = colors.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else if (ui.phase == VoiceCallPhase.Listening) {
            Text(
                text = "正在聆听你的声音",
                style = typography.labelMedium,
                color = colors.secondaryText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        if (ui.lastUserText.isNotBlank() || ui.lastAssistantText.isNotBlank()) {
            VoiceCallTranscriptStrip(
                lastUserText = ui.lastUserText,
                lastAssistantText = ui.lastAssistantText,
                highlightPhase = ui.phase,
            )
        }
    }
}

@Composable
private fun VoiceCallPhaseIndicator(
    phase: VoiceCallPhase,
    bargeInActive: Boolean,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhaseChip(
            label = "聆听",
            active = phase == VoiceCallPhase.Listening,
            activeColor = colors.primary,
        )
        PhaseChip(
            label = "思考",
            active = phase == VoiceCallPhase.Thinking,
            activeColor = colors.accent,
        )
        PhaseChip(
            label = if (bargeInActive) "说话·可打断" else "说话",
            active = phase == VoiceCallPhase.Speaking,
            activeColor = if (bargeInActive) colors.primary else colors.lightRose,
        )
    }

    Text(
        text = phaseLabel(phase),
        style = typography.labelSmall,
        color = colors.secondaryText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun PhaseChip(
    label: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (active) 10.dp else 8.dp)
                .clip(CircleShape)
                .background(
                    if (active) activeColor else colors.outlineVariant.copy(alpha = 0.5f),
                ),
        )
        Text(
            text = label,
            style = typography.labelMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) colors.text else colors.secondaryText,
        )
    }
}

@Composable
private fun VoiceCallTranscriptStrip(
    lastUserText: String,
    lastAssistantText: String,
    highlightPhase: VoiceCallPhase,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "最近对话",
            style = typography.labelSmall,
            color = colors.secondaryText,
        )
        if (lastUserText.isNotBlank()) {
            TranscriptLine(
                prefix = "你",
                text = lastUserText,
                emphasized = highlightPhase == VoiceCallPhase.Thinking ||
                    highlightPhase == VoiceCallPhase.Listening,
            )
        }
        if (lastAssistantText.isNotBlank()) {
            TranscriptLine(
                prefix = "AI",
                text = lastAssistantText,
                emphasized = highlightPhase == VoiceCallPhase.Speaking,
            )
        }
    }
}

@Composable
private fun TranscriptLine(
    prefix: String,
    text: String,
    emphasized: Boolean,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = prefix,
            style = typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) colors.primary else colors.secondaryText,
        )
        Text(
            text = text,
            style = typography.bodySmall,
            color = if (emphasized) colors.text else colors.secondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun phaseLabel(phase: VoiceCallPhase): String = when (phase) {
    VoiceCallPhase.Idle -> "准备就绪"
    VoiceCallPhase.Listening -> "聆听中"
    VoiceCallPhase.Thinking -> "思考中"
    VoiceCallPhase.Speaking -> "回复中"
    VoiceCallPhase.NeedsSetup -> "需要配置"
    VoiceCallPhase.Error -> "出错"
    VoiceCallPhase.Ended -> "已结束"
}
