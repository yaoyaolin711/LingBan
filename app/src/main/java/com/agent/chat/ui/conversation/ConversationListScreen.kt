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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.R
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.ProviderConfig
import com.agent.chat.ui.components.PersonaAvatar
import com.agent.chat.ui.components.PersonaPickerList
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.AgentChatTheme
import java.text.SimpleDateFormat
import java.util.Calendar
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
        personas = uiState.personas,
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
    personas: List<Persona> = emptyList(),
    searchQuery: String = "",
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onConversationClick: (String) -> Unit,
    onNewConversationClick: () -> Unit,
    onPersonaManageClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
) {
    val colors = AgentThemeColors
    val personaMap = remember(personas) { personas.associateBy { it.id } }
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable(onClick = onNewConversationClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建会话", tint = colors.surface)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            HeaderBar(
                onPersonaManageClick = onPersonaManageClick,
                onSettingsClick = onSettingsClick,
            )
            Spacer(modifier = Modifier.height(16.dp))
            SearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (conversations.isEmpty()) {
                EmptyState(
                    isSearching = searchQuery.isNotBlank(),
                    onNewConversationClick = onNewConversationClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        val persona = conversation.personaId?.let { personaMap[it] }
                        ConversationItem(
                            conversation = conversation,
                            persona = persona,
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
    val colors = AgentThemeColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.brand_logo),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "灵伴",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "在就好",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        IconButton(
            onClick = onPersonaManageClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surface),
        ) {
            Icon(Icons.Default.Person, contentDescription = "人设管理", tint = colors.accent)
        }
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surface),
        ) {
            Icon(Icons.Default.Settings, contentDescription = "设置", tint = colors.textSecondary)
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgentThemeColors

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary)
        },
        placeholder = {
            Text("搜索会话", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.outline,
            unfocusedBorderColor = colors.outline,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            cursorColor = colors.accent,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
        ),
    )
}

@Composable
private fun EmptyState(
    isSearching: Boolean,
    onNewConversationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgentThemeColors

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
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isSearching) {
                "换个关键词，或开始一段新对话"
            } else {
                "选一个陪伴对象，开始聊天"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
        if (!isSearching) {
            Spacer(modifier = Modifier.height(28.dp))
            TextButton(onClick = onNewConversationClick) {
                Text("新建会话", color = colors.accent)
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
    val colors = AgentThemeColors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = "开始新对话",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
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
                    color = colors.textSecondary,
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
                    color = colors.textSecondary,
                )
                if (providers.isEmpty()) {
                    Text(
                        text = "请先到设置页添加 Provider",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
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
                Text("开始", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.textSecondary)
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
    val colors = AgentThemeColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.surfaceSelected else colors.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    persona: Persona?,
    onClick: () -> Unit,
) {
    val colors = AgentThemeColors

    val displayName = persona?.name?.takeIf { it.isNotBlank() } ?: conversation.title
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonaAvatar(
            name = displayName,
            avatar = persona?.avatar.orEmpty(),
            size = 52.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatChatListTime(conversation.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.lastMessage.ifBlank { "说点什么吧…" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatChatListTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("M/d", Locale.getDefault()).format(Date(timestamp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF7F1)
@Composable
private fun ConversationListScreenPreview() {
    AgentChatTheme {
        ConversationListContent(
            conversations = listOf(
                Conversation(
                    id = "1",
                    title = "小橘的会话",
                    personaId = "p1",
                    createdAt = 0L,
                    updatedAt = System.currentTimeMillis(),
                    lastMessage = "今天过得怎么样？",
                ),
            ),
            personas = listOf(
                Persona(
                    id = "p1",
                    name = "小橘",
                    avatar = "🍊",
                    systemPrompt = "test",
                ),
            ),
            onConversationClick = {},
            onNewConversationClick = {},
            onPersonaManageClick = {},
            onSettingsClick = {},
        )
    }
}
