package me.rerere.rikkahub.ui.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar
import kotlin.uuid.Uuid

@Composable
fun HomePage(vm: HomeVM = koinViewModel()) {
    val nav = LocalNavController.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.12f),
                        scheme.background,
                        scheme.background,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Solace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { nav.navigate(Screen.Profile) }) {
                    Icon(
                        HugeIcons.Settings03,
                        contentDescription = stringResource(R.string.home_page_profile),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            SolaceOrb(
                onClick = {
                    val id = uiState.recentConversation?.id ?: vm.newConversationId()
                    nav.navigate(Screen.Chat(id = id.toString()))
                },
                size = 220.dp,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = greetingText(),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
            )
            val assistantName = uiState.assistant?.name?.takeIf { it.isNotBlank() }
            if (assistantName != null) {
                Text(
                    text = assistantName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = stringResource(R.string.home_page_tap_orb),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(28.dp))

            Surface(
                onClick = {
                    nav.navigate(Screen.Chat(id = Uuid.random().toString()))
                },
                shape = RoundedCornerShape(20.dp),
                color = scheme.primary,
                contentColor = scheme.onPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(HugeIcons.MessageAdd01, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.home_page_new_chat),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            HomeCard(
                title = stringResource(R.string.home_page_recent_chat),
                subtitle = uiState.recentConversation?.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.home_page_no_recent),
                onClick = {
                    val id = uiState.recentConversation?.id ?: vm.newConversationId()
                    nav.navigate(Screen.Chat(id = id.toString()))
                },
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeMiniCard(
                    title = stringResource(R.string.home_page_agents),
                    icon = { Icon(HugeIcons.LookTop, null, tint = scheme.primary) },
                    onClick = { nav.navigate(Screen.Assistant) },
                    cardModifier = Modifier.weight(1f),
                )
                HomeMiniCard(
                    title = stringResource(R.string.home_page_history),
                    icon = { Icon(HugeIcons.Clock02, null, tint = scheme.primary) },
                    onClick = { nav.navigate(Screen.History) },
                    cardModifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeMiniCard(
                    title = stringResource(R.string.home_page_memory),
                    icon = { Icon(HugeIcons.Brain02, null, tint = scheme.primary) },
                    onClick = {
                        val assistantId = uiState.assistant?.id?.toString()
                        if (assistantId != null) {
                            nav.navigate(Screen.AssistantMemory(assistantId))
                        } else {
                            nav.navigate(Screen.Assistant)
                        }
                    },
                    cardModifier = Modifier.weight(1f),
                    subtitle = if (uiState.memoryCount > 0) "${uiState.memoryCount}" else null,
                )
                HomeMiniCard(
                    title = stringResource(R.string.home_page_profile),
                    icon = { Icon(HugeIcons.Settings03, null, tint = scheme.primary) },
                    onClick = { nav.navigate(Screen.Profile) },
                    cardModifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun HomeMiniCard(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    cardModifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerLow,
        modifier = cardModifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun greetingText(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> stringResource(R.string.home_page_greeting_morning)
        hour < 18 -> stringResource(R.string.home_page_greeting_afternoon)
        else -> stringResource(R.string.home_page_greeting_evening)
    }
}
