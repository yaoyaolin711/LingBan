package me.rerere.rikkahub.ui.pages.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Home01
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.solace.CompanionAvatar
import me.rerere.rikkahub.ui.components.solace.CompanionAvatarSize
import me.rerere.rikkahub.ui.components.solace.PremiumBarCenter
import me.rerere.rikkahub.ui.components.solace.PremiumBarItem
import me.rerere.rikkahub.ui.components.solace.PremiumBottomBar
import me.rerere.rikkahub.ui.components.solace.RelationshipCard
import me.rerere.rikkahub.ui.components.solace.RelationshipMetric
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.chat.SolaceAmbientBackground
import me.rerere.rikkahub.ui.theme.SolaceTheme
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar

@Composable
fun HomePage(vm: HomeVM = koinViewModel()) {
    val nav = LocalNavController.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography

    val assistantName = uiState.assistant?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.assistant_page_default_assistant)
    val avatar = uiState.assistant?.avatar ?: Avatar.Dummy

    fun openChat(newChat: Boolean = false) {
        val id = when {
            newChat -> vm.newConversationId()
            else -> uiState.recentConversation?.id ?: vm.newConversationId()
        }
        nav.navigate(Screen.Chat(id = id.toString()))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Smooth wash only — Mesh blobs + Material elevation caused square flares
        SolaceAmbientBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 108.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Solace",
                style = typography.labelLarge,
                color = colors.secondaryText.copy(alpha = 0.7f),
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(top = 8.dp),
            )
            Text(
                text = greetingText(),
                style = typography.titleMedium,
                color = colors.text.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(top = 4.dp),
            )

            Spacer(Modifier.weight(0.35f))

            CompanionAvatar(
                name = assistantName,
                avatar = avatar,
                size = CompanionAvatarSize.Hero,
                showName = true,
                showHalo = false,
                breath = false,
                statusLabel = stringResource(R.string.home_page_status_online),
                onClick = { openChat(newChat = false) },
            )

            Spacer(Modifier.height(36.dp))

            HomeRelationshipCard(
                levelKey = uiState.relationshipLevelKey(),
                interactionCount = uiState.interactionCount,
                companionDays = uiState.companionDays,
                onClick = { openChat(newChat = false) },
            )

            Text(
                text = stringResource(R.string.home_page_tap_to_chat),
                style = typography.bodySmall,
                color = colors.secondaryText.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 14.dp),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))
        }

        HomePremiumBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            onHome = { /* already here */ },
            onCompanions = { nav.navigate(Screen.Assistant) },
            onChat = { openChat(newChat = uiState.recentConversation == null) },
            onMemory = {
                val assistantId = uiState.assistant?.id?.toString()
                if (assistantId != null) {
                    nav.navigate(Screen.AssistantMemory(assistantId))
                } else {
                    nav.navigate(Screen.Assistant)
                }
            },
            onProfile = { nav.navigate(Screen.Profile) },
        )
    }
}

@Composable
private fun HomeRelationshipCard(
    levelKey: String,
    interactionCount: Int,
    companionDays: Long,
    onClick: () -> Unit,
) {
    val levelLabel = when (levelKey) {
        "acquaintance" -> stringResource(R.string.home_page_level_acquaintance)
        "familiar" -> stringResource(R.string.home_page_level_familiar)
        "close" -> stringResource(R.string.home_page_level_close)
        "bonded" -> stringResource(R.string.home_page_level_bonded)
        else -> stringResource(R.string.home_page_level_new)
    }
    val levelRoman = remember(levelKey) {
        when (levelKey) {
            "acquaintance" -> "II"
            "familiar" -> "III"
            "close" -> "IV"
            "bonded" -> "V"
            else -> "I"
        }
    }
    RelationshipCard(
        title = stringResource(R.string.home_page_relationship_title),
        levelLabel = levelLabel,
        metrics = listOf(
            RelationshipMetric(levelRoman, stringResource(R.string.home_page_metric_level)),
            RelationshipMetric("$interactionCount", stringResource(R.string.home_page_metric_interactions)),
            RelationshipMetric(
                if (companionDays <= 0) "—" else "$companionDays",
                stringResource(R.string.home_page_metric_days),
            ),
        ),
        onClick = onClick,
    )
}

@Composable
private fun HomePremiumBottomBar(
    modifier: Modifier = Modifier,
    onHome: () -> Unit,
    onCompanions: () -> Unit,
    onChat: () -> Unit,
    onMemory: () -> Unit,
    onProfile: () -> Unit,
) {
    var selectedKey by remember { mutableStateOf("home") }
    val items = remember {
        listOf(
            PremiumBarItem("home", HugeIcons.Home01, ""),
            PremiumBarItem("companions", HugeIcons.LookTop, ""),
            PremiumBarItem("memory", HugeIcons.Brain02, ""),
            PremiumBarItem("profile", HugeIcons.Settings03, ""),
        )
    }
    // Labels need composition for stringResource — rebuild items with labels
    val labeledItems = listOf(
        items[0].copy(label = stringResource(R.string.home_page_nav_space)),
        items[1].copy(label = stringResource(R.string.home_page_agents)),
        items[2].copy(label = stringResource(R.string.home_page_memory)),
        items[3].copy(label = stringResource(R.string.home_page_profile)),
    )

    PremiumBottomBar(
        items = labeledItems,
        selectedKey = selectedKey,
        onItemClick = { item ->
            selectedKey = item.key
            when (item.key) {
                "home" -> onHome()
                "companions" -> onCompanions()
                "memory" -> onMemory()
                "profile" -> onProfile()
            }
        },
        modifier = modifier,
        center = PremiumBarCenter(
            icon = HugeIcons.Message01,
            contentDescription = stringResource(R.string.home_page_new_chat),
        ),
        centerSelected = selectedKey == "chat",
        onCenterClick = {
            selectedKey = "chat"
            onChat()
        },
    )
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
