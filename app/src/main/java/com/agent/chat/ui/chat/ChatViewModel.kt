package com.agent.chat.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.BuildConfig
import com.agent.chat.data.ai.OutputRegexApplier
import com.agent.chat.data.ai.PromptContextInjector
import com.agent.chat.data.ai.ToolChatEvent
import com.agent.chat.data.ai.ToolChatOrchestrator
import com.agent.chat.data.ai.prompt.PromptComposeRequest
import com.agent.chat.data.ai.prompt.PromptComposer
import com.agent.chat.data.ai.prompt.UserContext
import com.agent.chat.data.ai.response.ResponseController
import com.agent.chat.data.ai.response.ResponseEvalContext
import com.agent.chat.data.ai.response.ResponseScores
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.care.CareContextBuilder
import com.agent.chat.data.error.AppErrorLogger
import com.agent.chat.data.error.AppErrorMapper
import com.agent.chat.data.interaction.InteractionPreferenceStore
import com.agent.chat.data.memory.MemoryExtractor
import com.agent.chat.data.memory.MemorySettingsStore
import com.agent.chat.data.expression.ExpressionDefaults
import com.agent.chat.data.expression.ExpressionEngine
import com.agent.chat.data.expression.ExpressionManager
import com.agent.chat.data.expression.ExpressionPolicies
import com.agent.chat.data.conversationstate.ConversationStateManager
import com.agent.chat.data.conversationstate.ConversationStatePolicies
import com.agent.chat.data.runtime.BehaviorPlanPolicies
import com.agent.chat.data.runtime.RuntimeDecisionEngine
import com.agent.chat.data.runtime.RuntimeDecisionInput
import com.agent.chat.data.mode.ModeManager
import com.agent.chat.data.relationship.RelationshipEngine
import com.agent.chat.data.relationship.RelationshipManager
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ModelConfig
import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.repository.PersonaRepository
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.domain.error.AppError
import com.agent.chat.domain.error.userMessage
import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.RelationshipProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToolCallUiItem(
    val id: String,
    val name: String,
    val label: String,
    val running: Boolean,
    val success: Boolean? = null,
    val detail: String = "",
    val expanded: Boolean = false,
)

data class ChatUiState(
    val conversationId: String,
    val title: String = "新会话",
    val persona: Persona? = null,
    val personas: List<Persona> = emptyList(),
    val providerName: String? = null,
    val messages: List<Message> = emptyList(),
    val displayedMessages: List<Message> = emptyList(),
    val inputText: String = "",
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingMessageId: String? = null,
    val animatingUserMessageId: String? = null,
    val showPersonaSwitcher: Boolean = false,
    val showMemoryManager: Boolean = false,
    val showRelationshipManager: Boolean = false,
    val showExpressionManager: Boolean = false,
    val relationshipProfile: RelationshipProfile = RelationshipProfile(),
    val expressionProfile: ExpressionProfile = ExpressionProfile(),
    val expressionCustomized: Boolean = false,
    val memories: List<Memory> = emptyList(),
    val extractThreshold: Int = MemorySettingsStore.DEFAULT_THRESHOLD,
    val naturalChatPaceEnabled: Boolean = true,
    val isPacingReply: Boolean = false,
    val showPaceTyping: Boolean = false,
    val editingMessageId: String? = null,
    val failedMessageId: String? = null,
    val messageError: AppError? = null,
    val debugErrorDetail: String? = null,
    /** 本轮工具调用过程（仅展示，不入库） */
    val toolCalls: List<ToolCallUiItem> = emptyList(),
    /** 开发模式：最近一次 Response Controller 评分 */
    val lastResponseScores: ResponseScores? = null,
    val lastResponseEvalNote: String? = null,
    val showResponseScores: Boolean = BuildConfig.DEBUG,
    val userAvatarPath: String = "",
    val userNickname: String = "",
) {
    val isBusy: Boolean get() = isStreaming || isPacingReply
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val toolChatOrchestrator: ToolChatOrchestrator,
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    private val memoryRepository: MemoryRepository,
    private val memoryExtractor: MemoryExtractor,
    private val memorySettingsStore: MemorySettingsStore,
    private val chatSettingsStore: ChatSettingsStore,
    private val careContextBuilder: CareContextBuilder,
    private val promptComposer: PromptComposer,
    private val responseController: ResponseController,
    private val modeManager: ModeManager,
    private val relationshipManager: RelationshipManager,
    private val relationshipEngine: RelationshipEngine,
    private val expressionManager: ExpressionManager,
    private val interactionPreferenceStore: InteractionPreferenceStore,
    private val conversationStateManager: ConversationStateManager,
    private val runtimeDecisionEngine: RuntimeDecisionEngine,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: String =
        checkNotNull(savedStateHandle["conversationId"]) { "conversationId required" }

    private val _uiState = MutableStateFlow(ChatUiState(conversationId = conversationId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var streamJob: Job? = null
    private var memoryObserveJob: Job? = null
    private var activePersonaId: String? = null
    private var activeProviderConfigId: String? = null
    /** 分段展示期间锁定 displayedMessages，避免 Room 回写冲掉节奏态 */
    private var pacingDisplayActive: Boolean = false

    init {
        viewModelScope.launch {
            chatRepository.ensureConversationExists(conversationId)
        }
        viewModelScope.launch {
            personaRepository.observePersonas().collect { personas ->
                _uiState.update { it.copy(personas = personas) }
            }
        }
        viewModelScope.launch {
            chatRepository.observeConversation(conversationId).collect { conversation ->
                if (conversation != null) {
                    activePersonaId = conversation.personaId
                    activeProviderConfigId = conversation.providerConfigId
                    val persona = conversation.personaId?.let { personaRepository.getPersona(it) }
                    val providerName = conversation.providerConfigId
                        ?.let { providerConfigRepository.getConfig(it)?.name }
                        ?: providerConfigRepository.getDefaultConfig()?.name
                    val resolvedExpression = expressionManager.resolve(
                        stored = conversation.expressionProfile,
                        relationship = conversation.relationshipProfile,
                    )
                    _uiState.update {
                        it.copy(
                            title = conversation.title,
                            persona = persona,
                            providerName = providerName,
                            relationshipProfile = conversation.relationshipProfile,
                            expressionProfile = resolvedExpression,
                            expressionCustomized = conversation.expressionProfile != null,
                        )
                    }
                    observeMemoriesForPersona(conversation.personaId)
                }
            }
        }
        viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collect { messages ->
                val state = _uiState.value
                if (state.isStreaming || state.isPacingReply || pacingDisplayActive) {
                    // 仅同步 canonical messages，不打断节奏展示
                    if (!state.isStreaming) {
                        _uiState.update { it.copy(messages = messages) }
                    }
                    return@collect
                }
                _uiState.update {
                    it.copy(
                        messages = messages,
                        displayedMessages = filterMessages(messages, it.searchQuery),
                        streamingMessageId = null,
                    )
                }
            }
        }
        viewModelScope.launch {
            memorySettingsStore.snapshot.collect { settings ->
                _uiState.update { it.copy(extractThreshold = settings.extractThreshold) }
            }
        }
        viewModelScope.launch {
            chatSettingsStore.snapshot.collect { settings ->
                _uiState.update {
                    it.copy(
                        naturalChatPaceEnabled = settings.naturalChatPaceEnabled,
                        userAvatarPath = settings.userAvatarPath,
                        userNickname = settings.userNickname,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        pacingDisplayActive = false
        conversationStateManager.onConversationInactive(conversationId)
        super.onCleared()
    }

    private fun observeMemoriesForPersona(personaId: String?) {
        memoryObserveJob?.cancel()
        if (personaId.isNullOrBlank()) {
            _uiState.update { it.copy(memories = emptyList()) }
            return
        }
        memoryObserveJob = viewModelScope.launch {
            memoryRepository.observeByPersona(personaId).collect { memories ->
                _uiState.update { it.copy(memories = memories) }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearMessageError() {
        _uiState.update {
            it.copy(failedMessageId = null, messageError = null, debugErrorDetail = null)
        }
    }

    fun retryFailedMessage() {
        val id = _uiState.value.failedMessageId ?: return
        clearMessageError()
        regenerateMessage(id)
    }

    fun toggleSearch() {
        _uiState.update {
            val visible = !it.isSearchVisible
            it.copy(
                isSearchVisible = visible,
                searchQuery = if (visible) it.searchQuery else "",
                displayedMessages = when {
                    pacingDisplayActive -> it.displayedMessages
                    visible -> filterMessages(it.messages, it.searchQuery)
                    else -> it.messages
                },
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        if (pacingDisplayActive) {
            _uiState.update { it.copy(searchQuery = query) }
            return
        }
        _uiState.update {
            it.copy(
                searchQuery = query,
                displayedMessages = filterMessages(it.messages, query),
            )
        }
    }

    fun clearContext() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            pacingDisplayActive = false
            chatRepository.clearMessages(conversationId)
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    displayedMessages = emptyList(),
                    streamingMessageId = null,
                    animatingUserMessageId = null,
                    isPacingReply = false,
                    showPaceTyping = false,
                    failedMessageId = null,
                    messageError = null,
                    debugErrorDetail = null,
                )
            }
            _events.emit("已清空上下文")
        }
    }

    fun openPersonaSwitcher() {
        _uiState.update { it.copy(showPersonaSwitcher = true) }
    }

    fun dismissPersonaSwitcher() {
        _uiState.update { it.copy(showPersonaSwitcher = false) }
    }

    fun openMemoryManager() {
        val personaId = activePersonaId
        if (personaId.isNullOrBlank()) {
            viewModelScope.launch { _events.emit("请先选择人设后再管理记忆") }
            return
        }
        _uiState.update { it.copy(showMemoryManager = true) }
    }

    fun dismissMemoryManager() {
        _uiState.update { it.copy(showMemoryManager = false) }
    }

    fun openRelationshipManager() {
        _uiState.update { it.copy(showRelationshipManager = true) }
    }

    fun dismissRelationshipManager() {
        _uiState.update { it.copy(showRelationshipManager = false) }
    }

    fun saveRelationshipProfile(profile: RelationshipProfile) {
        viewModelScope.launch {
            relationshipManager.save(conversationId, profile)
            val expression = if (_uiState.value.expressionCustomized) {
                _uiState.value.expressionProfile
            } else {
                ExpressionDefaults.recommend(profile)
            }
            _uiState.update {
                it.copy(
                    relationshipProfile = profile,
                    expressionProfile = expression,
                )
            }
            _events.emit("已更新关系设定：${profile.relationshipType.displayName}")
        }
    }

    fun openExpressionManager() {
        _uiState.update { it.copy(showExpressionManager = true) }
    }

    fun dismissExpressionManager() {
        _uiState.update { it.copy(showExpressionManager = false) }
    }

    fun saveExpressionProfile(profile: ExpressionProfile) {
        viewModelScope.launch {
            expressionManager.save(conversationId, profile)
            _uiState.update {
                it.copy(
                    expressionProfile = profile,
                    expressionCustomized = true,
                )
            }
            _events.emit("已更新表达风格")
        }
    }

    fun applyRecommendedExpression() {
        viewModelScope.launch {
            chatRepository.clearConversationExpression(conversationId)
            val recommended = ExpressionDefaults.recommend(_uiState.value.relationshipProfile)
            _uiState.update {
                it.copy(
                    expressionProfile = recommended,
                    expressionCustomized = false,
                )
            }
            _events.emit("已恢复关系推荐表达风格")
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(memoryId)
            _events.emit("已删除记忆")
        }
    }

    fun switchPersona(personaId: String?) {
        viewModelScope.launch {
            chatRepository.updateConversationPersona(conversationId, personaId)
            activePersonaId = personaId
            val persona = personaId?.let { personaRepository.getPersona(it) }
            _uiState.update {
                it.copy(
                    persona = persona,
                    showPersonaSwitcher = false,
                    title = persona?.name?.let { name -> "$name 的会话" } ?: it.title,
                )
            }
            if (persona != null) {
                chatRepository.updateConversationTitle(conversationId, "${persona.name} 的会话")
            }
            _events.emit(if (persona == null) "已切换为自由对话" else "已切换到 ${persona.name}")
        }
    }

    fun sendSuggested(text: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(inputText = text) }
        sendMessage()
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty() || state.isBusy) return

        val editingId = state.editingMessageId
        if (editingId != null) {
            val target = state.messages.find { it.id == editingId } ?: return
            launchStream {
                chatRepository.deleteMessagesFrom(conversationId, target.createdAt)
                val remaining = state.messages.filter { it.createdAt < target.createdAt }
                _uiState.update {
                    it.copy(
                        messages = remaining,
                        displayedMessages = filterMessages(remaining, it.searchQuery),
                        editingMessageId = null,
                        inputText = "",
                    )
                }
                performUserTurn(text, historyBeforeUser = remaining)
            }
            return
        }

        launchStream {
            performUserTurn(text, historyBeforeUser = state.messages)
        }
    }

    fun regenerateMessage(messageId: String) {
        val state = _uiState.value
        if (state.isBusy) return
        val realId = ReplySegmenter.sourceMessageId(messageId)
        val target = state.messages.find { it.id == realId } ?: return

        launchStream {
            when (target.role) {
                MessageRole.ASSISTANT -> {
                    val history = state.messages.filter { it.createdAt < target.createdAt }
                    chatRepository.deleteMessagesFrom(conversationId, target.createdAt)
                    val assistant = newAssistantPlaceholder()
                    setWorkingMessages(history + assistant, streamingId = assistant.id)
                    chatRepository.saveMessage(assistant)
                    collectAssistantStream(historyForApi = history, assistantId = assistant.id)
                }
                MessageRole.USER -> {
                    val followingAssistant = state.messages.firstOrNull {
                        it.createdAt > target.createdAt && it.role == MessageRole.ASSISTANT
                    }
                    val history = state.messages.filter { it.createdAt <= target.createdAt }
                    if (followingAssistant != null) {
                        chatRepository.deleteMessagesFrom(conversationId, followingAssistant.createdAt)
                    }
                    val assistant = newAssistantPlaceholder()
                    setWorkingMessages(history + assistant, streamingId = assistant.id)
                    chatRepository.saveMessage(assistant)
                    collectAssistantStream(historyForApi = history, assistantId = assistant.id)
                }
            }
        }
    }

    fun beginEditMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (message.role != MessageRole.USER) {
            viewModelScope.launch { _events.emit("仅支持编辑用户消息后重发") }
            return
        }
        _uiState.update {
            it.copy(editingMessageId = messageId, inputText = message.content)
        }
        viewModelScope.launch { _events.emit("已载入编辑，发送后将替换后续消息") }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(editingMessageId = null, inputText = "") }
    }

    fun addMessageToMemory(messageId: String) {
        viewModelScope.launch {
            val personaId = activePersonaId
            if (personaId.isNullOrBlank()) {
                _events.emit("请先选择伙伴后再加入记忆")
                return@launch
            }
            val realId = ReplySegmenter.sourceMessageId(messageId)
            val message = _uiState.value.messages.find { it.id == realId }
                ?: _uiState.value.displayedMessages.find { it.id == messageId }
            if (message == null || message.content.isBlank()) {
                _events.emit("无法加入空消息")
                return@launch
            }
            memoryRepository.saveMemory(
                personaId = personaId,
                conversationId = conversationId,
                content = message.content.trim().take(500),
                importance = 7,
            )
            _events.emit("已加入记忆")
        }
    }

    fun favoriteMessage(messageId: String) {
        viewModelScope.launch {
            val realId = ReplySegmenter.sourceMessageId(messageId)
            val message = _uiState.value.messages.find { it.id == realId }
                ?: _uiState.value.displayedMessages.find { it.id == messageId }
            if (message == null || message.content.isBlank()) {
                _events.emit("无法收藏空消息")
                return@launch
            }
            _events.emit("已收藏")
        }
    }

    fun onUserFlyInFinished(messageId: String) {
        _uiState.update {
            if (it.animatingUserMessageId == messageId) {
                it.copy(animatingUserMessageId = null)
            } else {
                it
            }
        }
    }

    private fun launchStream(block: suspend () -> Unit) {
        streamJob?.cancel()
        pacingDisplayActive = false
        streamJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    isPacingReply = false,
                    showPaceTyping = false,
                    failedMessageId = null,
                    messageError = null,
                    debugErrorDetail = null,
                )
            }
            try {
                block()
            } catch (e: CancellationException) {
                settleDisplayedToCanonical()
                throw e
            } catch (e: Exception) {
                // 流式错误已在 collectAssistantStream 写入气泡态；此处仅兜底全局提示
                if (_uiState.value.messageError == null) {
                    val appError = AppErrorMapper.from(e)
                    AppErrorLogger.log(e, appError)
                    _events.emit(appError.userMessage())
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingMessageId = null,
                        isPacingReply = false,
                        showPaceTyping = false,
                    )
                }
            }
        }
    }

    private suspend fun performUserTurn(text: String, historyBeforeUser: List<Message>) {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val userMessage = Message(
            id = "user_$now",
            conversationId = conversationId,
            role = MessageRole.USER,
            content = text,
            createdAt = now,
        )
        val assistant = Message(
            id = "assistant_${now + 1}",
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            createdAt = now + 1,
        )
        val shouldRename = historyBeforeUser.isEmpty() &&
            (state.title.isBlank() || state.title == "新会话" || state.title.endsWith("的会话"))

        val working = historyBeforeUser + userMessage + assistant
        _uiState.update {
            it.copy(
                inputText = "",
                messages = working,
                displayedMessages = filterMessages(working, it.searchQuery),
                editingMessageId = null,
                animatingUserMessageId = userMessage.id,
                streamingMessageId = assistant.id,
                title = if (shouldRename) text.take(20) else it.title,
            )
        }

        chatRepository.ensureConversationExists(conversationId)
        chatRepository.saveMessage(userMessage)
        chatRepository.saveMessage(assistant)
        if (shouldRename) {
            chatRepository.updateConversationTitle(conversationId, text.take(20))
        }
        collectAssistantStream(
            historyForApi = historyBeforeUser + userMessage,
            assistantId = assistant.id,
        )
    }

    private suspend fun collectAssistantStream(historyForApi: List<Message>, assistantId: String) {
        _uiState.update {
            it.copy(streamingMessageId = assistantId, toolCalls = emptyList())
        }
        chatSettingsStore.touchLastUserActivity()
        try {
            val providerConfig = activeProviderConfigId
                ?.let { providerConfigRepository.getConfig(it) }
                ?: providerConfigRepository.getDefaultConfig()
                ?: throw IllegalStateException("请先在设置页配置 Provider")

            val persona = activePersonaId?.let { personaRepository.getPersona(it) }
            val chatSettings = chatSettingsStore.get()
            val chatMode = chatSettings.chatMode
            val personaEffect = modeManager.personaEffect(persona, chatMode)
            val careContext = careContextBuilder.build(
                personaId = activePersonaId,
                recentMessages = historyForApi,
            )
            val queryText = historyForApi
                .asReversed()
                .firstOrNull { it.role == MessageRole.USER }
                ?.content
                .orEmpty()
            val memoryBudget = modeManager.memoryTokenBudget(
                MemorySettingsStore.PROMPT_MEMORY_MAX_TOKENS,
                chatMode,
            )
            val memories = if (modeManager.promptPolicy(chatMode).memoryEnabled) {
                activePersonaId
                    ?.let {
                        memoryRepository.retrieveForPrompt(
                            personaId = it,
                            queryText = queryText,
                            recentMessages = historyForApi,
                            maxTokens = memoryBudget,
                        ).memories
                    }
                    .orEmpty()
            } else {
                emptyList()
            }
            val relationshipProfile = _uiState.value.relationshipProfile
            val expressionProfile = _uiState.value.expressionProfile
            val conversationState = conversationStateManager.updateOnUserMessage(
                conversationId = conversationId,
                userMessage = queryText,
                chatMode = chatMode,
            )
            val runtimeDecision = runtimeDecisionEngine.decide(
                RuntimeDecisionInput(
                    persona = persona?.copy(
                        defaultTemperature = personaEffect.temperature,
                        profile = personaEffect.profile ?: persona.profile,
                    ),
                    relationship = relationshipProfile,
                    expression = expressionProfile,
                    interactionPreference = interactionPreferenceStore.get(),
                    memories = memories,
                    conversationState = conversationState,
                    userMessage = queryText,
                    chatMode = chatMode,
                ),
            )
            val behaviorPlan = runtimeDecision.plan
            val composed = promptComposer.compose(
                PromptComposeRequest(
                    persona = persona?.copy(
                        defaultTemperature = personaEffect.temperature,
                        profile = personaEffect.profile ?: persona.profile,
                    ),
                    memories = memories,
                    userContext = UserContext(
                        nickname = chatSettings.userNickname,
                        interest = chatSettings.userInterest,
                        occupation = chatSettings.userOccupation,
                        goal = chatSettings.userGoal,
                    ),
                    conversationHistory = historyForApi,
                    careContext = careContext,
                    modelName = providerConfig.modelName,
                    providerName = providerConfig.name,
                    conversationId = conversationId,
                    baseHumanEnabled = chatSettings.companionStyleEnabled,
                    chatMode = chatMode,
                    rolePlayEnabled = chatMode == LingBanChatMode.ROLEPLAY,
                    relationshipProfile = relationshipProfile,
                    expressionProfile = expressionProfile,
                    interactionPreference = interactionPreferenceStore.get(),
                    userMessage = queryText,
                    conversationState = conversationState,
                    behaviorPlan = behaviorPlan,
                ),
            )
            val systemPrompt = composed.systemPrompt
            val baseMessages = buildList {
                if (systemPrompt.isNotEmpty()) {
                    add(ChatMessage.system(systemPrompt))
                }
                addAll(
                    PromptContextInjector.buildPresetChatMessages(
                        persona = persona,
                        userNickname = chatSettings.userNickname,
                    ),
                )
                val reminder = PromptContextInjector.timeReminderIfNeeded(historyForApi)
                if (reminder != null) {
                    add(ChatMessage.user(reminder))
                }
                addAll(historyForApi.map { it.toChatMessage() })
            }
            val requestConfig = providerConfigRepository.toModelConfig(
                config = providerConfig,
                temperature = personaEffect.temperature,
            )
            val paceEnabled = chatSettings.naturalChatPaceEnabled

            val responsePolicy = BehaviorPlanPolicies.adjustResponsePolicy(
                ConversationStatePolicies.adjustResponsePolicy(
                    relationshipEngine.adjustResponsePolicy(
                        modeManager.responsePolicy(chatMode),
                        relationshipProfile,
                        chatMode,
                    ),
                    conversationState,
                ),
                behaviorPlan,
            )
            val expressionPolicy = ExpressionPolicies.fromProfile(expressionProfile)
            val evalContext = ResponseEvalContext(
                userMessage = queryText,
                chatMode = chatMode,
                policy = responsePolicy,
                relationshipProfile = relationshipProfile,
                expressionProfile = expressionProfile,
                expressionPolicy = expressionPolicy,
                conversationState = conversationState,
                behaviorPlan = behaviorPlan,
            )
            val controlled = responseController.run(
                context = evalContext,
                enabled = chatSettings.responseControllerEnabled,
            ) { attempt, previous ->
                if (attempt > 0) {
                    // 重生成：清空气泡并重置工具条
                    updateCanonicalMessage(assistantId) { it.copy(content = "") }
                    _uiState.update {
                        it.copy(toolCalls = emptyList(), streamingMessageId = assistantId)
                    }
                }
                val messages = if (attempt > 0 && previous != null) {
                    baseMessages + ChatMessage.user(responseController.buildRepairHint(previous, evalContext))
                } else {
                    baseMessages
                }
                runAssistantGeneration(
                    messages = messages,
                    requestConfig = requestConfig,
                    assistantId = assistantId,
                    paceEnabled = paceEnabled,
                )
            }

            val rawFinal = controlled.text.ifBlank { "（无回复）" }
            val rewritten = OutputRegexApplier.apply(rawFinal, persona)
            val finalContent = rewritten.persisted
            val displayContent = rewritten.displayed
            chatRepository.updateMessageContent(assistantId, finalContent)
            chatRepository.touchConversation(conversationId)
            updateCanonicalMessage(assistantId) { it.copy(content = displayContent) }

            val scoreNote = buildString {
                append(if (controlled.evaluation.passed) "通过" else "未完全通过")
                if (controlled.regenerated) append(" · 已重生成×${controlled.attempts - 1}")
                if (controlled.evaluation.reasons.isNotEmpty()) {
                    append(" · ")
                    append(controlled.evaluation.reasons.joinToString("；"))
                }
            }
            _uiState.update {
                it.copy(
                    failedMessageId = null,
                    messageError = null,
                    debugErrorDetail = null,
                    lastResponseScores = controlled.evaluation.scores,
                    lastResponseEvalNote = scoreNote,
                    showResponseScores = BuildConfig.DEBUG,
                )
            }

            if (paceEnabled) {
                revealInNaturalPace(
                    assistantId = assistantId,
                    fullContent = displayContent,
                    preferNewline = chatSettings.splitBubbleByNewline,
                )
            } else {
                updateDisplayedFromCanonical()
            }

            val latestMessages = _uiState.value.messages
            memoryExtractor.maybeExtractAsync(
                personaId = activePersonaId,
                conversationId = conversationId,
                messages = latestMessages,
                providerConfigId = activeProviderConfigId ?: providerConfig.id,
            )
        } catch (e: CancellationException) {
            settleDisplayedToCanonical()
            throw e
        } catch (e: Exception) {
            val appError = AppErrorMapper.from(e)
            AppErrorLogger.log(e, appError)
            val persisted = _uiState.value.messages.find { it.id == assistantId }?.content.orEmpty()
            if (persisted.isNotBlank()) {
                chatRepository.updateMessageContent(assistantId, persisted)
                chatRepository.touchConversation(conversationId)
            }
            pacingDisplayActive = false
            _uiState.update {
                it.copy(
                    failedMessageId = assistantId,
                    messageError = appError,
                    debugErrorDetail = buildDebugDetail(e),
                    isPacingReply = false,
                    showPaceTyping = false,
                    displayedMessages = filterMessages(it.messages, it.searchQuery),
                )
            }
        }
    }

    private suspend fun runAssistantGeneration(
        messages: List<ChatMessage>,
        requestConfig: ModelConfig,
        assistantId: String,
        paceEnabled: Boolean,
    ): String {
        var assistantContent = ""
        toolChatOrchestrator.run(
            messages = messages,
            baseConfig = requestConfig,
            context = ToolExecutionContext(
                personaId = activePersonaId,
                conversationId = conversationId,
            ),
        ).collect { event ->
            when (event) {
                is ToolChatEvent.ContentDelta -> {
                    assistantContent = event.text
                    updateCanonicalMessage(assistantId) {
                        it.copy(content = assistantContent)
                    }
                    if (!paceEnabled) {
                        updateDisplayedFromCanonicalKeepStreaming(assistantId)
                    }
                }
                is ToolChatEvent.ToolStarted -> {
                    _uiState.update { state ->
                        state.copy(
                            toolCalls = state.toolCalls + ToolCallUiItem(
                                id = event.call.id,
                                name = event.call.name,
                                label = toolLabel(event.call.name),
                                running = true,
                            ),
                        )
                    }
                }
                is ToolChatEvent.ToolFinished -> {
                    _uiState.update { state ->
                        state.copy(
                            toolCalls = state.toolCalls.map { item ->
                                if (item.id == event.call.id) {
                                    item.copy(
                                        running = false,
                                        success = event.success,
                                        detail = event.resultPreview,
                                    )
                                } else {
                                    item
                                }
                            },
                        )
                    }
                }
                is ToolChatEvent.Completed -> {
                    assistantContent = event.finalContent
                }
            }
        }
        return assistantContent.ifBlank { "（无回复）" }
    }

    fun toggleToolCallExpanded(toolCallId: String) {
        _uiState.update { state ->
            state.copy(
                toolCalls = state.toolCalls.map {
                    if (it.id == toolCallId) it.copy(expanded = !it.expanded) else it
                },
            )
        }
    }

    private fun toolLabel(name: String): String = when (name) {
        "memory" -> "记忆"
        "get_current_time" -> "看了看时间"
        "get_battery" -> "查了电量"
        "get_device_info" -> "看了设备信息"
        "calendar_events" -> "翻了日历"
        "set_alarm" -> "设了闹钟"
        "get_location" -> "看了位置"
        "get_app_usage" -> "看了使用情况"
            "get_recent_notifications" -> "看了通知"
            "music_control" -> "操作了音乐"
            "get_recent_sms" -> "看了短信"
            "get_screen_state" -> "看了屏幕状态"
            "get_screen_content" -> "看了你的屏幕"
            else -> name
    }

    private fun updateDisplayedFromCanonicalKeepStreaming(assistantId: String) {
        _uiState.update { current ->
            current.copy(
                displayedMessages = filterMessages(current.messages, current.searchQuery),
                streamingMessageId = assistantId,
            )
        }
    }

    private fun buildDebugDetail(e: Throwable): String? {
        if (!com.agent.chat.BuildConfig.DEBUG) return null
        val type = e.javaClass.simpleName
        val msg = e.message?.take(240).orEmpty()
        return "$type: $msg"
    }

    private fun newAssistantPlaceholder(): Message {
        val now = System.currentTimeMillis()
        return Message(
            id = "assistant_$now",
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            createdAt = now,
        )
    }

    private fun setWorkingMessages(working: List<Message>, streamingId: String?) {
        _uiState.update {
            it.copy(
                messages = working,
                displayedMessages = filterMessages(working, it.searchQuery),
                streamingMessageId = streamingId,
            )
        }
    }

    /** 仅替换目标消息，保持其余 Message 引用不变，便于 LazyColumn 跳过重组 */
    private fun updateCanonicalMessage(messageId: String, transform: (Message) -> Message) {
        _uiState.update { current ->
            var changed = false
            val messages = current.messages.map { msg ->
                if (msg.id == messageId) {
                    changed = true
                    transform(msg)
                } else {
                    msg
                }
            }
            if (!changed) return@update current
            current.copy(messages = messages)
        }
    }

    private fun updateDisplayedFromCanonical() {
        pacingDisplayActive = false
        _uiState.update { current ->
            current.copy(
                displayedMessages = filterMessages(current.messages, current.searchQuery),
                isPacingReply = false,
                showPaceTyping = false,
            )
        }
    }

    private fun settleDisplayedToCanonical() {
        pacingDisplayActive = false
        _uiState.update { current ->
            current.copy(
                displayedMessages = filterMessages(current.messages, current.searchQuery),
                isPacingReply = false,
                showPaceTyping = false,
                streamingMessageId = null,
            )
        }
    }

    private suspend fun revealInNaturalPace(
        assistantId: String,
        fullContent: String,
        preferNewline: Boolean = true,
    ) {
        val source = _uiState.value.messages.find { it.id == assistantId } ?: return
        val segments = ReplySegmenter.split(fullContent, preferNewline = preferNewline)
        val baseMessages = _uiState.value.messages.filter { it.id != assistantId }

        if (segments.size <= 1) {
            updateDisplayedFromCanonical()
            return
        }

        pacingDisplayActive = true
        val revealed = ArrayList<Message>(segments.size)

        _uiState.update {
            it.copy(
                isPacingReply = true,
                showPaceTyping = true,
                streamingMessageId = null,
                displayedMessages = filterMessages(baseMessages, it.searchQuery),
            )
        }

        try {
            for ((index, segment) in segments.withIndex()) {
                _uiState.update { it.copy(showPaceTyping = true) }
                delay(ReplySegmenter.estimateTypingDelayMs(segment))

                revealed.add(
                    Message(
                        id = ReplySegmenter.segmentId(assistantId, index),
                        conversationId = source.conversationId,
                        role = MessageRole.ASSISTANT,
                        content = segment,
                        createdAt = source.createdAt + index,
                    ),
                )
                val display = baseMessages + revealed
                _uiState.update {
                    it.copy(
                        showPaceTyping = index < segments.lastIndex,
                        displayedMessages = filterMessages(display, it.searchQuery),
                    )
                }
            }
            _uiState.update {
                it.copy(isPacingReply = false, showPaceTyping = false)
            }
        } catch (e: CancellationException) {
            settleDisplayedToCanonical()
            throw e
        }
    }

    private fun filterMessages(messages: List<Message>, query: String): List<Message> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return messages
        return messages.filter { it.content.contains(trimmed, ignoreCase = true) }
    }

    private fun Message.toChatMessage(): ChatMessage = ChatMessage(
        role = when (role) {
            MessageRole.USER -> ChatMessage.ROLE_USER
            MessageRole.ASSISTANT -> ChatMessage.ROLE_ASSISTANT
        },
        content = content,
    )
}
