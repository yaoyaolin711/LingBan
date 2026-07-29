package com.agent.chat.ui.conversation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.R
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.ProviderConfig
import com.agent.chat.ui.components.PersonaPickerList
import com.agent.chat.ui.theme.Accent
import com.agent.chat.ui.theme.AgentChatTheme
import com.agent.chat.ui.theme.AppBg
import com.agent.chat.ui.theme.CardElevation
import com.agent.chat.ui.theme.OutlineSubtle
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.SurfaceMuted
import com.agent.chat.ui.theme.SurfaceSelected
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit,
    onNewConversationCreated: (String) -> Unit,
    onPersonaManageClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.createdConversationId.collect { id ->
            onNewConversationCreated(id)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.statusMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    ConversationListContent(
        conversations = uiState.conversations,
        searchQuery = uiState.searchQuery,
        snackbarHostState = snackbarHostState,
        onConversationClick = onConversationClick,
        onNewConversationClick = viewModel::openCreateDialog,
        onPersonaManageClick = onPersonaManageClick,
        onSettingsClick = onSettingsClick,
        onSearchQueryChange = viewModel::onSearchQueryChange,
    )

    if (uiState.showCreateDialog) {
        CreateConversationDialog(
            personas = uiState.personas,
            providers = uiState.providers,
            selectedPersonaId = uiState.selectedPersonaId,
            selectedProviderId = uiState.selectedProviderId,
            onDismiss = viewModel::dismissCreateDialog,
            onSelectPersona = viewModel::selectPersona,
            onSelectProvider = viewModel::selectProvider,
            onConfirm = viewModel::confirmCreateConversation,
        )
    }
}

@Composable
fun ConversationListContent(
    conversations: List<Conversation>,
    searchQuery: String = "",
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onConversationClick: (String) -> Unit,
    onNewConversationClick: () -> Unit,
    onPersonaManageClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
) {
    Scaffold(
        containerColor = AppBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Accent)
                    .clickable(onClick = onNewConversationClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建会话", tint = SurfaceCard)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBg)
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            HeaderBar(
                onPersonaManageClick = onPersonaManageClick,
                onSettingsClick = onSettingsClick,
            )
            Spacer(modifier = Modifier.height(24.dp))
            SearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (conversations.isEmpty()) {
                EmptyState(
                    isSearching = searchQuery.isNotBlank(),
                    onNewConversationClick = onNewConversationClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                Text(
                    text = "${conversations.size} 个会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = { onConversationClick(conversation.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    onPersonaManageClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.brand_logo),
            contentDescription = "Agent Chat",
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Agent Chat",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "安静地聊一会",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        IconButton(
            onClick = onPersonaManageClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceCard),
        ) {
            Icon(Icons.Default.Person, contentDescription = "人设管理", tint = TextSecondary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceCard),
        ) {
            Icon(Icons.Default.Settings, contentDescription = "设置", tint = TextSecondary)
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
        },
        placeholder = {
            Text("搜索会话", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OutlineSubtle,
            unfocusedBorderColor = OutlineSubtle,
            focusedContainerColor = SurfaceCard,
            unfocusedContainerColor = SurfaceCard,
            cursorColor = Accent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

@Composable
private fun EmptyState(
    isSearching: Boolean,
    onNewConversationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.brand_logo),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = if (isSearching) "没有找到相关会话" else "还没有对话",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isSearching) {
                "换个关键词，或开始一段新对话"
            } else {
                "选一个人设，找一个安静的角落聊起来"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        if (!isSearching) {
            Spacer(modifier = Modifier.height(28.dp))
            TextButton(onClick = onNewConversationClick) {
                Text("新建会话", color = Accent)
            }
        }
    }
}

@Composable
private fun CreateConversationDialog(
    personas: List<Persona>,
    providers: List<ProviderConfig>,
    selectedPersonaId: String?,
    selectedProviderId: String?,
    onDismiss: () -> Unit,
    onSelectPersona: (String?) -> Unit,
    onSelectProvider: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "开始新对话",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "选择对话对象",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                PersonaPickerList(
                    personas = personas,
                    selectedPersonaId = selectedPersonaId,
                    onPersonaSelected = onSelectPersona,
                    modifier = Modifier.height(240.dp),
                    scrollable = true,
                )

                Text(
                    text = "选择 Provider",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                if (providers.isEmpty()) {
                    Text(
                        text = "请先到设置页添加 Provider",
                        style = MaterialTheme.typography.bodySmall,
                        color = Accent,
                    )
                } else {
                    providers.forEach { provider ->
                        ProviderRow(
                            title = provider.name,
                            subtitle = provider.modelName,
                            selected = provider.id == selectedProviderId,
                            onClick = { onSelectProvider(provider.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !selectedProviderId.isNullOrBlank(),
            ) {
                Text("开始", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun ProviderRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceSelected else SurfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(if (selected) Accent else androidx.compose.ui.graphics.Color.Transparent),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = conversation.title.take(1).ifBlank { "A" },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = conversation.lastMessage.ifBlank { "还没有消息" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatRelativeTime(conversation.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@Preview(showBackground = true, backgroundColor = 0xFFEDEEF0)
@Composable
private fun ConversationListScreenPreview() {
    AgentChatTheme {
        ConversationListContent(
            conversations = listOf(
                Conversation(
                    id = "1",
                    title = "周末出行",
                    createdAt = 0L,
                    updatedAt = System.currentTimeMillis(),
                    lastMessage = "帮我规划一下行程",
                ),
            ),
            onConversationClick = {},
            onNewConversationClick = {},
            onPersonaManageClick = {},
            onSettingsClick = {},
        )
    }
}
