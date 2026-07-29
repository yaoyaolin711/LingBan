package com.agent.chat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import com.agent.chat.domain.model.Persona
import com.agent.chat.ui.components.PersonaPickerList
import com.agent.chat.ui.export.ConversationExporter
import com.agent.chat.ui.home.AiOrb
import com.agent.chat.ui.home.AiOrbState
import com.agent.chat.ui.memory.MemoryManageDialog
import com.agent.chat.ui.motion.SwipeBackContainer
import com.agent.chat.ui.theme.Accent
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.CardElevation
import com.agent.chat.ui.theme.OutlineSubtle
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val SuggestedTopics = listOf(
    "今天过得怎么样",
    "陪我聊聊天",
    "有点累了",
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ChatScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
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

    SwipeBackContainer(onBack = onBackClick) {
        ChatScreenContent(
            animatedVisibilityScope = animatedVisibilityScope,
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
            onFavorite = viewModel::favoriteMessage,
            onAddMemory = viewModel::addMessageToMemory,
            onPlusClick = {
                scope.launch { snackbarHostState.showSnackbar("更多能力即将开放") }
            },
            onAttachClick = {
                scope.launch { snackbarHostState.showSnackbar("附件即将开放") }
            },
            onVoiceClick = {
                scope.launch { snackbarHostState.showSnackbar("语音输入即将开放") }
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ChatScreenContent(
    animatedVisibilityScope: AnimatedVisibilityScope,
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
    onFavorite: (String) -> Unit = {},
    onAddMemory: (String) -> Unit = {},
    onPlusClick: () -> Unit = {},
    onAttachClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
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
    var inputVisible by remember { mutableStateOf(true) }
    var inputFocused by remember { mutableStateOf(false) }
    var lastIndex by remember { mutableIntStateOf(0) }
    var lastOffset by remember { mutableIntStateOf(0) }

    val streamingContent = messages
        .lastOrNull { it.id == uiState.streamingMessageId }
        ?.content
        .orEmpty()
    val presence = resolveChatPresence(
        isStreaming = uiState.isStreaming,
        showPaceTyping = uiState.showPaceTyping,
        streamingContentEmpty = streamingContent.isEmpty(),
        hasRunningTools = uiState.toolCalls.any { it.running },
    )
    val displayName = uiState.persona?.name?.takeIf { it.isNotBlank() } ?: "灵伴"

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
            inputVisible = true
        } else if (uiState.showPaceTyping || uiState.toolCalls.isNotEmpty()) {
            listState.animateScrollToItem(maxOf(messages.size, 0))
        }
    }

    // 上滑阅读时隐藏输入框；回到底部 / 聚焦 / 生成中时显示
    LaunchedEffect(listState, inputFocused, uiState.isBusy) {
        snapshotFlow {
            val nearBottom = !listState.canScrollForward
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            Triple(nearBottom, index, offset)
        }
            .distinctUntilChanged()
            .collect { (nearBottom, index, offset) ->
                val deltaIndex = index - lastIndex
                val deltaOffset = offset - lastOffset
                // 手指上滑：列表向下推进（index/offset 增大）→ 扩大阅读空间，隐藏输入
                val fingerSwipeUp = deltaIndex > 0 || (deltaIndex == 0 && deltaOffset > 12)
                val fingerSwipeDown = deltaIndex < 0 || (deltaIndex == 0 && deltaOffset < -12)
                when {
                    inputFocused || uiState.isBusy || nearBottom -> inputVisible = true
                    fingerSwipeUp && !nearBottom -> inputVisible = false
                    fingerSwipeDown -> inputVisible = true
                }
                lastIndex = index
                lastOffset = offset
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AgentThemeColors.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ChatTopBar(
                    animatedVisibilityScope = animatedVisibilityScope,
                    displayName = displayName,
                    presence = presence,
                    onBackClick = onBackClick,
                    onToggleSearch = onToggleSearch,
                    onClearContext = onClearContext,
                    onSwitchPersona = onOpenPersonaSwitcher,
                    onManageMemory = onOpenMemoryManager,
                    onExport = { showExportDialog = true },
                )
            },
            bottomBar = {
                FloatingInputBar(
                    value = uiState.inputText,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    enabled = !uiState.isBusy,
                    visible = inputVisible,
                    personaName = uiState.persona?.name,
                    editing = uiState.editingMessageId != null,
                    onCancelEdit = onCancelEdit,
                    onPlusClick = onPlusClick,
                    onAttachClick = onAttachClick,
                    onVoiceClick = onVoiceClick,
                    onFocusChange = { focused ->
                        inputFocused = focused
                        if (focused) inputVisible = true
                    },
                )
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
                        val failedId = uiState.failedMessageId
                        val messageError = uiState.messageError
                        val debugDetail = uiState.debugErrorDetail
                        val toolCalls = uiState.toolCalls

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = 20.dp,
                                vertical = 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(
                                items = messages,
                                key = { it.id },
                                contentType = { it.role },
                            ) { message ->
                                val showThinking = isStreaming &&
                                    message.id == streamingId &&
                                    message.role == MessageRole.ASSISTANT &&
                                    message.content.isEmpty() &&
                                    toolCalls.isEmpty()
                                val isFailed = message.id == failedId && messageError != null
                                val streamingThis = isStreaming && message.id == streamingId

                                AnimatedChatItem(messageId = message.id) {
                                    when {
                                        showThinking -> ThinkingStreamRow()
                                        isFailed -> FailedMessageBlock(
                                            message = message,
                                            error = messageError!!,
                                            debugDetail = debugDetail,
                                            onRetry = onRetryFailed,
                                        )
                                        message.role == MessageRole.USER && message.content.isNotEmpty() -> {
                                            UserMessageBubble(
                                                message = message,
                                                enabled = !isBusy,
                                                onCopy = { onCopyMessage(message.content) },
                                                onRegenerate = { onRegenerate(message.id) },
                                                onEditResend = { onEditResend(message.id) },
                                                onFavorite = { onFavorite(message.id) },
                                                onAddMemory = { onAddMemory(message.id) },
                                            )
                                        }
                                        message.role == MessageRole.ASSISTANT &&
                                            (message.content.isNotEmpty() || (streamingThis && toolCalls.isNotEmpty())) -> {
                                            AiContentStream(
                                                message = message,
                                                isStreaming = streamingThis,
                                                toolCalls = if (streamingThis) toolCalls else emptyList(),
                                                enabled = !isBusy,
                                                onCopy = { onCopyMessage(message.content) },
                                                onRegenerate = { onRegenerate(message.id) },
                                                onFavorite = { onFavorite(message.id) },
                                                onAddMemory = { onAddMemory(message.id) },
                                                onToggleTool = onToggleToolExpanded,
                                            )
                                        }
                                    }
                                }
                            }

                            if (toolCalls.isNotEmpty() &&
                                messages.none { it.id == streamingId && it.role == MessageRole.ASSISTANT }
                            ) {
                                item(key = "tool_calls") {
                                    ToolCallStream(
                                        items = toolCalls,
                                        onToggle = onToggleToolExpanded,
                                    )
                                }
                            }

                            if (showPaceTyping) {
                                item(key = "pace_typing") {
                                    ThinkingStreamRow()
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
private fun AnimatedChatItem(
    messageId: String,
    content: @Composable () -> Unit,
) {
    var visible by remember(messageId) { mutableStateOf(false) }
    LaunchedEffect(messageId) { visible = true }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "msg_$messageId",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 12f
            },
    ) {
        content()
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
        AiOrb(state = AiOrbState.Idle, size = 88.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = persona?.name ?: "灵伴",
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
