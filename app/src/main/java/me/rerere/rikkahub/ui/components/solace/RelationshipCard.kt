package me.rerere.rikkahub.ui.components.solace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.theme.SolaceTheme

@Immutable
data class RelationshipMetric(
    val value: String,
    val label: String,
)

/**
 * Relationship showcase card — built on [RoseGoldCard] (no duplicated chrome).
 */
@Composable
fun RelationshipCard(
    title: String,
    levelLabel: String,
    metrics: List<RelationshipMetric>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val stableMetrics = remember(metrics) { metrics }

    RoseGoldCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text(
            text = title,
            style = typography.labelLarge,
            color = colors.secondaryText,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = levelLabel,
            style = typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            stableMetrics.forEach { metric ->
                RelationshipMetricItem(
                    value = metric.value,
                    label = metric.label,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RelationshipMetricItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = typography.labelSmall,
            color = colors.secondaryText,
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}
