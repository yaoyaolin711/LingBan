package com.agent.chat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.agent.chat.domain.error.AppError
import com.agent.chat.domain.error.userMessage
import com.agent.chat.ui.theme.ErrorSoftBg
import com.agent.chat.ui.theme.ErrorSoftBorder
import com.agent.chat.ui.theme.ErrorSoftText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import com.agent.chat.domain.model.Persona
import com.agent.chat.ui.components.MarkdownMessageContent
import com.agent.chat.ui.components.PersonaAvatar
import com.agent.chat.ui.components.PersonaPickerList
import com.agent.chat.ui.components.TypingIndicator
import com.agent.chat.ui.export.ConversationExporter
import com.agent.chat.ui.memory.MemoryManageDialog
import com.agent.chat.ui.theme.Accent
import com.agent.chat.ui.theme.AppBg
import com.agent.chat.ui.theme.BubbleAssistant
import com.agent.chat.ui.theme.BubbleElevation
import com.agent.chat.ui.theme.BubbleUser
import com.agent.chat.ui.theme.CardElevation
import com.agent.chat.ui.theme.OnlineGreen
import com.agent.chat.ui.theme.OutlineSubtle
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.SurfaceMuted
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Psychology

private val SuggestedTopics = listOf(
    "今天过得怎么样",
    "陪我聊聊天",
    "有点累了",
)

@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    ChatScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::sendMessage,
        onToggleSearch = viewModel::toggleSearch,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onCopyMessage = { content ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("message", content))
            scope.launch { snackbarHostState.showSnackbar("已复制") }
        },
        onRegenerate = viewModel::regenerateMessage,
        onEditResend = viewModel::beginEditMessage,
        onCancelEdit = viewModel::cancelEdit,
        onClearContext = viewModel::clearContext,
        onOpenPersonaSwitcher = viewModel::openPersonaSwitcher,
        onDismissPersonaSwitcher = viewModel::dismissPersonaSwitcher,
        onSwitchPersona = viewModel::switchPersona,
        onOpenMemoryManager = viewModel::openMemoryManager,
        onDismissMemoryManager = viewModel::dismissMemoryManager,
        onDeleteMemory = viewModel::deleteMemory,
        onSuggest = viewModel::sendSuggested,
        onRetryFailed = viewModel::retryFailedMessage,
        onExportText = {
            ConversationExporter.shareText(context, uiState.title, uiState.messages)
        },
        onExportImage = {
            ConversationExporter.shareImage(context, uiState.title, uiState.messages)
        },
        onToggleToolExpanded = viewModel::toggleToolCallExpanded,
    )
}

@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBackClick: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onCopyMessage: (String) -> Unit = {},
    onRegenerate: (String) -> Unit = {},
    onEditResend: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onClearContext: () -> Unit = {},
    onOpenPersonaSwitcher: () -> Unit = {},
    onDismissPersonaSwitcher: () -> Unit = {},
    onSwitchPersona: (String?) -> Unit = {},
    onOpenMemoryManager: () -> Unit = {},
    onDismissMemoryManager: () -> Unit = {},
    onDeleteMemory: (String) -> Unit = {},
    onSuggest: (String) -> Unit = {},
    onRetryFailed: () -> Unit = {},
    onExportText: () -> Unit = {},
    onExportImage: () -> Unit = {},
    onToggleToolExpanded: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val messages = uiState.displayedMessages
    val lastId = messages.lastOrNull()?.id
    val lastContentLen by remember(messages) {
        derivedStateOf { messages.lastOrNull()?.content?.length ?: 0 }
    }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingSwitchPersonaId by remember(uiState.persona?.id) {
        mutableStateOf(uiState.persona?.id)
    }

    LaunchedEffect(
        messages.size,
        lastId,
        lastContentLen,
        uiState.searchQuery,
        uiState.showPaceTyping,
        uiState.toolCalls.size,
    ) {
        if (messages.isNotEmpty() && uiState.searchQuery.isBlank()) {
            listState.animateScrollToItem(messages.lastIndex)
        } else if (uiState.showPaceTyping || uiState.toolCalls.isNotEmpty()) {
            listState.animateScrollToItem(maxOf(messages.size, 0))
        }
    }

    Scaffold(
        containerColor = AppBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                persona = uiState.persona,
                title = uiState.persona?.name ?: uiState.title,
                onBackClick = onBackClick,
                onToggleSearch = onToggleSearch,
                onClearContext = onClearContext,
                onSwitchPersona = onOpenPersonaSwitcher,
                onManageMemory = onOpenMemoryManager,
                onExport = { showExportDialog = true },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(AppBg)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                if (uiState.editingMessageId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "编辑后重发",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancelEdit) {
                            Text("取消", color = Accent)
                        }
                    }
                }
                ChatInputBar(
                    value = uiState.inputText,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    enabled = !uiState.isBusy,
                    personaName = uiState.persona?.name,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.isSearchVisible) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text("搜索本会话…", color = TextSecondary) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除", tint = TextSecondary)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = chatFieldColors(),
                )
            }

            when {
                messages.isEmpty() && !uiState.isBusy && !uiState.showPaceTyping -> {
                    EmptyChatState(
                        persona = uiState.persona,
                        onSuggest = onSuggest,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    val streamingId = uiState.streamingMessageId
                    val isStreaming = uiState.isStreaming
                    val isBusy = uiState.isBusy
                    val showPaceTyping = uiState.showPaceTyping
                    val personaName = uiState.persona?.name ?: "AI"
                    val personaAvatar = uiState.persona?.avatar.orEmpty()
                    val failedId = uiState.failedMessageId
                    val messageError = uiState.messageError
                    val debugDetail = uiState.debugErrorDetail
                    val toolCalls = uiState.toolCalls

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            items = messages,
                            key = { it.id },
                            contentType = { it.role },
                        ) { message ->
                            val showTyping = isStreaming &&
                                message.id == streamingId &&
                                message.role == MessageRole.ASSISTANT &&
                                message.content.isEmpty() &&
                                toolCalls.isEmpty()
                            val isFailed = message.id == failedId && messageError != null

                            if (showTyping) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PersonaAvatar(
                                        name = personaName,
                                        avatar = personaAvatar,
                                        size = 28.dp,
                                    )
                                    TypingIndicator()
                                }
                            } else if (isFailed) {
                                FailedMessageBlock(
                                    message = message,
                                    error = messageError!!,
                                    debugDetail = debugDetail,
                                    onRetry = onRetryFailed,
                                )
                            } else if (message.content.isNotEmpty()) {
                                AnimatedMessageBubble(
                                    message = message,
                                    personaName = personaName,
                                    personaAvatar = personaAvatar,
                                    enabled = !isBusy,
                                    onCopy = { onCopyMessage(message.content) },
                                    onRegenerate = { onRegenerate(message.id) },
                                    onEditResend = { onEditResend(message.id) },
                                )
                            }
                        }

                        if (toolCalls.isNotEmpty()) {
                            item(key = "tool_calls") {
                                ToolCallStrip(
                                    items = toolCalls,
                                    onToggle = onToggleToolExpanded,
                                )
                            }
                        }

                        if (showPaceTyping) {
                            item(key = "pace_typing") {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PersonaAvatar(
                                        name = personaName,
                                        avatar = personaAvatar,
                                        size = 28.dp,
                                    )
                                    TypingIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showPersonaSwitcher) {
        AlertDialog(
            onDismissRequest = onDismissPersonaSwitcher,
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "切换对话对象",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            },
            text = {
                Column {
                    Text(
                        text = "选择后将继续在本会话中对话",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PersonaPickerList(
                        personas = uiState.personas,
                        selectedPersonaId = pendingSwitchPersonaId,
                        onPersonaSelected = { pendingSwitchPersonaId = it },
                        modifier = Modifier.height(320.dp),
                        scrollable = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onSwitchPersona(pendingSwitchPersonaId) }) {
                    Text("确认", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPersonaSwitcher) {
                    Text("取消", color = TextSecondary)
                }
            },
        )
    }

    if (uiState.showMemoryManager) {
        MemoryManageDialog(
            memories = uiState.memories,
            personaName = uiState.persona?.name,
            onDismiss = onDismissMemoryManager,
            onDelete = onDeleteMemory,
            extractThreshold = uiState.extractThreshold,
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = SurfaceCard,
            title = {
                Text("导出会话", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    onExportText()
                }) { Text("导出文本", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    onExportImage()
                }) { Text("导出图片", color = Accent) }
            },
        )
    }
}

@Composable
private fun ChatTopBar(
    persona: Persona?,
    title: String,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onClearContext: () -> Unit,
    onSwitchPersona: () -> Unit,
    onManageMemory: () -> Unit,
    onExport: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBg)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
            )
        }
        PersonaAvatar(
            name = persona?.name ?: "AI",
            avatar = persona?.avatar.orEmpty(),
            size = 36.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (persona != null) "在线" else "自由对话",
                style = MaterialTheme.typography.bodySmall,
                color = if (persona != null) OnlineGreen else TextSecondary,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = TextSecondary)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("搜索消息") },
                    onClick = {
                        menuExpanded = false
                        onToggleSearch()
                    },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                )
                DropdownMenuItem(
                    text = { Text("切换人设") },
                    onClick = {
                        menuExpanded = false
                        onSwitchPersona()
                    },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                )
                DropdownMenuItem(
                    text = { Text("记忆管理") },
                    onClick = {
                        menuExpanded = false
                        onManageMemory()
                    },
                    leadingIcon = { Icon(Icons.Default.Psychology, null) },
                )
                DropdownMenuItem(
                    text = { Text("清空上下文") },
                    onClick = {
                        menuExpanded = false
                        onClearContext()
                    },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                )
                DropdownMenuItem(
                    text = { Text("导出会话") },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChatState(
    persona: Persona?,
    onSuggest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting = remember(persona) {
        when {
            persona == null -> "准备好了就开口吧，我在听。"
            persona.openingLine.isNotBlank() -> persona.openingLine
            persona.description.isNotBlank() -> persona.description
            else -> "你好，我是${persona.name}。想从哪聊起？"
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PersonaAvatar(
            name = persona?.name ?: "AI",
            avatar = persona?.avatar.orEmpty(),
            size = 72.dp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = persona?.name ?: "新对话",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(28.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuggestedTopics.forEach { topic ->
                SuggestionChip(text = topic, onClick = { onSuggest(topic) })
            }
        }
    }
}

@Composable
private fun FailedMessageBlock(
    message: Message,
    error: AppError,
    debugDetail: String?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (message.content.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BubbleAssistant),
                elevation = CardDefaults.cardElevation(defaultElevation = BubbleElevation),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    MarkdownMessageContent(
                        markdown = message.content,
                        textColor = TextPrimary,
                        isUser = false,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ErrorSoftBg)
                .border(1.dp, ErrorSoftBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = error.userMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorSoftText,
            )
            if (!debugDetail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = debugDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("重试", color = Accent)
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AnimatedMessageBubble(
    message: Message,
    personaName: String,
    personaAvatar: String,
    enabled: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEditResend: () -> Unit,
) {
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "msg_${message.id}",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 10f
            },
    ) {
        MessageBubble(
            message = message,
            personaName = personaName,
            personaAvatar = personaAvatar,
            enabled = enabled,
            onCopy = onCopy,
            onRegenerate = onRegenerate,
            onEditResend = onEditResend,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    personaName: String,
    personaAvatar: String,
    enabled: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEditResend: () -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            PersonaAvatar(
                name = personaName,
                avatar = personaAvatar,
                size = 32.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box {
            Card(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        enabled = enabled && message.content.isNotBlank(),
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    ),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 18.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) BubbleUser else BubbleAssistant,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isUser) 0.dp else BubbleElevation,
                ),
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (isUser) {
                        Text(
                            text = message.content,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        MarkdownMessageContent(
                            markdown = message.content,
                            textColor = TextPrimary,
                            isUser = false,
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        menuExpanded = false
                        onCopy()
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                )
                DropdownMenuItem(
                    text = { Text("重新生成") },
                    onClick = {
                        menuExpanded = false
                        onRegenerate()
                    },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                )
                if (isUser) {
                    DropdownMenuItem(
                        text = { Text("编辑重发") },
                        onClick = {
                            menuExpanded = false
                            onEditResend()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    personaName: String?,
) {
    val canSend = enabled && value.isNotBlank()
    val placeholder = if (personaName.isNullOrBlank()) {
        "说点什么…"
    } else {
        "和${personaName}聊点什么…"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = { /* 扩展入口：图片/语音等 */ },
            enabled = enabled,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceCard),
        ) {
            Icon(Icons.Default.Add, contentDescription = "更多功能", tint = TextSecondary)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            placeholder = {
                Text(
                    text = if (enabled) placeholder else "正在回复…",
                    color = TextSecondary,
                )
            },
            maxLines = 4,
            shape = RoundedCornerShape(20.dp),
            colors = chatFieldColors(),
        )
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) Accent else SurfaceMuted),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = if (canSend) SurfaceCard else TextSecondary,
            )
        }
    }
}

@Composable
private fun chatFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OutlineSubtle,
    unfocusedBorderColor = OutlineSubtle,
    focusedContainerColor = SurfaceCard,
    unfocusedContainerColor = SurfaceCard,
    disabledContainerColor = SurfaceCard,
    cursorColor = Accent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextSecondary,
)

@Composable
private fun ToolCallStrip(
    items: List<ToolCallUiItem>,
    onToggle: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val borderColor = when {
                item.running -> Accent.copy(alpha = 0.35f)
                item.success == false -> ErrorSoftBorder
                else -> OutlineSubtle
            }
            val bg = when {
                item.success == false -> ErrorSoftBg
                else -> SurfaceCard
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable { onToggle(item.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            item.running -> "· ${item.label}…"
                            item.success == false -> "· ${item.label}失败"
                            else -> "· ${item.label}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.success == false) ErrorSoftText else TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = Accent,
                        )
                    }
                }
                if (item.expanded && item.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}
