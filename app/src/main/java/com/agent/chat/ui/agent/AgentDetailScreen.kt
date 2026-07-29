package com.agent.chat.ui.agent

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.ui.components.PersonaAvatar
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.ErrorSoftText
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AgentDetailScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBackClick: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onManageClick: () -> Unit,
    viewModel: AgentDetailViewModel = hiltViewModel(),
) {
    val colors = AgentThemeColors

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val persona = uiState.persona

    LaunchedEffect(Unit) {
        launch {
            viewModel.openConversationId.collect(onOpenConversation)
        }
        launch {
            viewModel.events.collect { snackbarHostState.showSnackbar(it) }
        }
        launch {
            viewModel.deleted.collect { onBackClick() }
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = "伙伴详情",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { innerPadding ->
        if (persona == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("这位伙伴不在了", color = colors.textSecondary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(
                            key = agentSharedCardKey(persona.id),
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PersonaAvatar(
                    name = persona.name,
                    avatar = persona.avatar,
                    size = 96.dp,
                    modifier = Modifier.sharedElement(
                        state = rememberSharedContentState(
                            key = agentSharedAvatarKey(persona.id),
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = persona.description.ifBlank {
                        persona.openingLine.ifBlank { "一位愿意慢慢了解你的 AI 伙伴" }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            DetailSection(title = "能力") {
                if (uiState.capabilities.isEmpty()) {
                    Text("自由对话", color = colors.textSecondary)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        uiState.capabilities.forEach { CapabilityTag(text = it) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            DetailSection(title = "人格设定") {
                Text(
                    text = persona.systemPrompt.ifBlank { "尚未写下详细人格" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                if (persona.openingLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "开场白",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = persona.openingLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            DetailSection(title = "记忆范围") {
                Text(
                    text = uiState.memoryScope,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "与这位伙伴相关的记忆会跨会话保留，并在对话中温柔地被想起。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            DetailSection(title = "使用次数") {
                Text(
                    text = if (uiState.usageCount > 0) {
                        "一起开启过 ${uiState.usageCount} 段对话"
                    } else {
                        "还没有共同的故事，可以从一次聊天开始"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = viewModel::startChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                Text("开始对话", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onManageClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("高级设定与导入", color = colors.textPrimary)
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = viewModel::deleteAgent,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = ErrorSoftText,
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("与这位伙伴告别", color = ErrorSoftText)
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = AgentThemeColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceMuted.copy(alpha = 0.65f))
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}
