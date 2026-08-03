package me.rerere.rikkahub.service

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.ai.ui.planRollingSummaryUpdate
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ConversationCompressHelper
import me.rerere.rikkahub.data.ai.DEFAULT_AUTO_SUMMARY_TARGET_TOKENS
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.SessionOverviewHelper
import me.rerere.rikkahub.data.ai.carryoverOfferSourceTitle
import me.rerere.rikkahub.data.ai.isCarryoverOffer
import me.rerere.rikkahub.data.ai.withoutCarryoverOffers
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.createConversationTools
import me.rerere.rikkahub.data.ai.tools.buildRecallChatHistoryTool
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createWorkflowTools
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.ai.tools.withAutoApprovalBypass
import me.rerere.rikkahub.data.agent.AgentManager
import me.rerere.rikkahub.data.agent.AgentRuntimeEvent
import me.rerere.rikkahub.data.agent.TaskRoute
import me.rerere.rikkahub.data.agent.TaskRouter
import me.rerere.rikkahub.data.companion.CompanionFacade
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.CompanionPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkflowPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.workflow.WorkflowManager
import me.rerere.rikkahub.data.workflow.WorkflowRuntimeBundle
import me.rerere.rikkahub.data.workflow.WorkflowScheduler
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.workspace.WorkspaceShellStatus
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

internal fun backgroundTextGenerationParams(
    model: Model,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
)

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckTitleModelSettings,
}

private val inputTransformers by lazy {
    listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        CompanionPromptTransformer,
        WorkflowPromptTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val conversationCompressHelper: ConversationCompressHelper,
    private val sessionOverviewHelper: SessionOverviewHelper,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val workflowManager: WorkflowManager,
    private val workspaceRepository: WorkspaceRepository,
    private val folderRepository: FolderRepository,
    private val companionFacade: CompanionFacade,
    private val agentManagerLazy: Lazy<AgentManager>,
) {
    private val agentManager: AgentManager get() = agentManagerLazy.value

    /** Resolve AgentManager only when needed; never fail normal chat on DI errors. */
    private fun agentManagerOrNull(): AgentManager? = runCatching { agentManagerLazy.value }
        .onFailure { Log.e(TAG, "AgentManager unavailable", it) }
        .getOrNull()

    // workspace 系统提示注入 (依赖 workspaceRepository, 故在类内构造)
    private val workspaceReminderTransformer = WorkspaceReminderTransformer(workspaceRepository)

    // 统一会话管理
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    fun cleanup() = runCatching {
        sessions.values.forEach { it.cleanup() }
        sessions.clear()
    }

    // ---- Session 管理 ----

    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {
        return sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id
                ),
                scope = appScope,
                onIdle = { removeSession(it) }
            ).also {
                _sessionsVersion.value++
                Log.i(TAG, "createSession: $id (total: ${sessions.size + 1})")
            }
        }
    }

    private fun removeSession(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            _sessionsVersion.value++
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    // ---- 引用管理 ----

    fun addConversationReference(conversationId: Uuid) {
        getOrCreateSession(conversationId).acquire()
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    private fun launchWithConversationReference(
        conversationId: Uuid,
        block: suspend () -> Unit
    ): Job = appScope.launch {
        addConversationReference(conversationId)
        try {
            block()
        } finally {
            removeConversationReference(conversationId)
        }
    }

    // ---- 对话状态访问 ----

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        return getOrCreateSession(conversationId).state
    }

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        val session = sessions[conversationId] ?: return flowOf(null)
        return session.generationJob
    }

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> {
        val session = sessions[conversationId] ?: return MutableStateFlow(null)
        return session.processingStatus
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return _sessionsVersion.flatMapLatest {
            val currentSessions = sessions.values.toList()
            if (currentSessions.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(currentSessions.map { s ->
                    s.generationJob.map { job -> s.id to job }
                }) { pairs ->
                    pairs.filter { it.second != null }.toMap()
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        getOrCreateSession(conversationId) // 确保 session 存在
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            updateConversation(conversationId, conversation)
            settingsStore.updateAssistant(conversation.assistantId)
        } else {
            // 新建对话, 并添加预设消息（上一会话概览改为异步卡片，不再在此处自动导入）
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val existingOfferNodes = getConversationFlow(conversationId).value.messageNodes.filter {
                it.currentMessage.isCarryoverOffer()
            }
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
                newConversation = true
            ).updateCurrentMessages(assistant.presetMessages).let { base ->
                if (existingOfferNodes.isEmpty()) base
                else base.copy(messageNodes = base.messageNodes + existingOfferNodes)
            }
            updateConversation(conversationId, newConversation)
        }
    }

    /**
     * 新建会话后异步生成上一会话概览，并以助手卡片形式插入目标会话，供用户选择是否导入。
     */
    fun requestCarryoverOfferAsync(
        sourceConversation: Conversation,
        targetConversationId: Uuid,
    ) {
        if (!sessionOverviewHelper.shouldOfferOverview(sourceConversation)) return
        launchWithConversationReference(targetConversationId) {
            val settings = settingsStore.settingsFlow.first()
            val carryover = runCatching {
                sessionOverviewHelper.prepareCarryoverFrom(sourceConversation, settings)
            }.onFailure {
                Log.w(TAG, "requestCarryoverOfferAsync failed", it)
            }.getOrNull() ?: return@launchWithConversationReference

            // Wait until the new chat page finished initializeConversation
            for (i in 0 until 40) {
                if (getConversationFlow(targetConversationId).value.newConversation) break
                delay(50)
            }

            val current = getConversationFlow(targetConversationId).value
            if (current.currentMessages.any { it.isCarryoverOffer() }) return@launchWithConversationReference
            if (!current.carryoverOverview.isNullOrBlank()) return@launchWithConversationReference

            val offerNode = SessionOverviewHelper.buildOfferNode(carryover)
            val firstUserIndex = current.messageNodes.indexOfFirst {
                it.currentMessage.role == MessageRole.USER
            }
            val updatedNodes = if (firstUserIndex < 0) {
                current.messageNodes + offerNode
            } else {
                current.messageNodes.toMutableList().apply { add(firstUserIndex, offerNode) }
            }
            saveConversation(
                targetConversationId,
                current.copy(messageNodes = updatedNodes),
            )
            sessionOverviewHelper.clearPending()
        }
    }

    fun acceptCarryoverOffer(conversationId: Uuid, messageId: Uuid) {
        appScope.launch {
            val current = getConversationFlow(conversationId).value
            val offerMessage = current.currentMessages.firstOrNull { it.id == messageId && it.isCarryoverOffer() }
                ?: return@launch
            val overview = offerMessage.parts
                .filterIsInstance<UIMessagePart.Text>()
                .firstOrNull { it.isCarryoverOffer() }
                ?.text
                ?.trim()
                .orEmpty()
            if (overview.isBlank()) return@launch
            val sourceTitle = offerMessage.carryoverOfferSourceTitle() ?: "上一会话"
            val confirmation = UIMessage.assistant(
                "已导入「$sourceTitle」的会话概览，后续对话会参考这些内容。"
            )
            val updatedNodes = current.messageNodes.map { node ->
                if (node.messages.any { it.id == messageId }) {
                    confirmation.toMessageNode()
                } else {
                    node
                }
            }
            saveConversation(
                conversationId,
                current.copy(
                    carryoverOverview = overview,
                    messageNodes = updatedNodes,
                ),
            )
        }
    }

    fun declineCarryoverOffer(conversationId: Uuid, messageId: Uuid) {
        appScope.launch {
            val current = getConversationFlow(conversationId).value
            val updatedNodes = current.messageNodes.filterNot { node ->
                node.messages.any { it.id == messageId && it.isCarryoverOffer() }
            }
            if (updatedNodes.size == current.messageNodes.size) return@launch
            saveConversation(conversationId, current.copy(messageNodes = updatedNodes))
        }
    }

    // ---- 发送消息 ----

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return

        val session = getOrCreateSession(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val job = appScope.launch {
            try {
                // Device tasks run on an independent appScope Job; cancel+join them
                // before starting a new turn, otherwise the interrupted task keeps controlling the phone.
                agentManagerOrNull()?.cancelAndJoin("chat_interrupt")
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)

                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val processedContent = preprocessUserInputParts(content, assistant)

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)

                // 开始补全
                if (answer) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                agentManagerOrNull()?.cancelAndJoin("chat_interrupt")
                finishInterruptedPendingTools(conversationId)
                val conversation = session.state.value

                if (message.role == MessageRole.USER) {
                    // 如果是用户消息，则截止到当前消息
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        val nodeIndex = conversation.messageNodes.indexOf(node)
                        handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
        alwaysAllow: Boolean = false,
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                agentManagerOrNull()?.cancelAndJoin("chat_interrupt")
                val conversation = session.state.value
                val toolName = conversation.messageNodes
                    .asSequence()
                    .flatMap { it.messages.asSequence() }
                    .flatMap { it.parts.asSequence() }
                    .filterIsInstance<UIMessagePart.Tool>()
                    .firstOrNull { it.toolCallId == toolCallId }
                    ?.toolName

                if (approved && alwaysAllow && !toolName.isNullOrBlank()) {
                    settingsStore.update { settings ->
                        settings.copy(autoApprovedTools = settings.autoApprovedTools + toolName)
                    }
                }

                val newApprovalState = when {
                    answer != null -> ToolApprovalState.Answered(answer)
                    approved -> ToolApprovalState.Approved
                    else -> ToolApprovalState.Denied(reason)
                }

                // Update the tool approval state
                val updatedNodes = conversation.messageNodes.map { node ->
                    node.copy(
                        messages = node.messages.map { msg ->
                            msg.copy(
                                parts = msg.parts.map { part ->
                                    when {
                                        part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                            part.copy(approvalState = newApprovalState)
                                        }

                                        else -> part
                                    }
                                }
                            )
                        }
                    )
                }
                val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                saveConversation(conversationId, updatedConversation)

                // Check if there are still pending tools
                val hasPendingTools = updatedNodes.any { node ->
                    node.currentMessage.parts.any { part ->
                        part is UIMessagePart.Tool && part.isPending
                    }
                }

                // Only continue generation when all pending tools are handled
                if (!hasPendingTools) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null
    ) {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(initialConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return
        val companionPromptBundle = companionFacade.preparePromptBundle(
            conversationId = conversationId,
            assistant = assistant,
            settings = settings,
            messages = initialConversation.currentMessages.let {
                if (messageRange != null) {
                    it.subList(messageRange.start, messageRange.endInclusive + 1)
                } else {
                    it
                }
            },
        )

        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }

        runCatching {

            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (assistant.enableWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value

            val latestUserText = conversation.currentMessages
                .lastOrNull { it.role == MessageRole.USER }
                ?.toText()
                ?.trim()
                .orEmpty()
            val phoneEnabled = assistant.localTools.contains(LocalToolOption.PhoneControl)
            // Route without touching AgentManager — classify is pure local rules.
            val routeDecision = TaskRouter.classify(latestUserText, phoneEnabled)

            when (routeDecision.route) {
                TaskRoute.DEVICE_TASK, TaskRoute.HYBRID -> {
                    val manager = agentManagerOrNull()
                    if (manager == null) {
                        Log.w(TAG, "Device route skipped; AgentManager unavailable, falling back to chat")
                    } else {
                        handleDeviceTaskRoute(
                            conversationId = conversationId,
                            assistant = assistant,
                            senderName = senderName,
                            decisionGoal = routeDecision.goal,
                            hybrid = routeDecision.route == TaskRoute.HYBRID,
                            settings = settings.takeIf { routeDecision.route == TaskRoute.HYBRID },
                            model = model.takeIf { routeDecision.route == TaskRoute.HYBRID },
                            companionPromptBundle = companionPromptBundle.takeIf {
                                routeDecision.route == TaskRoute.HYBRID
                            },
                            messageRange = messageRange.takeIf { routeDecision.route == TaskRoute.HYBRID },
                            userText = latestUserText,
                        )
                        return@runCatching
                    }
                }
                TaskRoute.CHAT -> Unit
            }

            val workflowBundle = buildWorkflowRuntimeBundle(
                assistant = assistant,
                userText = latestUserText,
                deviceRouteTaken = false,
            )

            // start generating
            val session = getOrCreateSession(conversationId)
            val conversationForGeneration = ensureRollingSummary(
                conversationId = conversationId,
                conversation = conversation,
                assistant = assistant,
                settings = settings,
                messages = conversation.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                }.withoutCarryoverOffers(),
                processingStatus = session.processingStatus,
            )
            generationHandler.generateText(
                settings = settings,
                model = model,
                processingStatus = session.processingStatus,
                messages = conversationForGeneration.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                }.withoutCarryoverOffers(),
                assistant = assistant,
                conversationSystemPrompt = conversationForGeneration.customSystemPrompt,
                conversationModeInjectionIds = conversationForGeneration.modeInjectionIds,
                conversationLorebookIds = conversationForGeneration.lorebookIds,
                companionPromptBundle = companionPromptBundle,
                workspaceCwd = conversationForGeneration.workspaceCwd,
                rollingSummary = conversationForGeneration.rollingSummary,
                carryoverOverview = conversationForGeneration.carryoverOverview,
                workflowBundle = workflowBundle,
                memories = if (assistant.useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                    add(workspaceReminderTransformer)
                },
                outputTransformers = outputTransformers,
                tools = buildList {
                    if (assistant.enableWebSearch) {
                        addAll(createSearchTools(settings))
                    }
                    addAll(localTools.getTools(assistant.localTools))
                    if (assistant.enableRecentChatsReference) {
                        addAll(createConversationTools(conversationRepo, assistant.id))
                    }
                    if (assistant.contextMessageLimit > 0) {
                        add(
                            buildRecallChatHistoryTool(
                                getMessages = { getConversationFlow(conversationId).value.currentMessages },
                                contextMessageLimit = assistant.contextMessageLimit,
                            )
                        )
                    }
                    addAll(createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), conversationForGeneration.workspaceCwd))
                    addAll(
                        createWorkflowTools(
                            workflowManager = workflowManager,
                            settingsStore = settingsStore,
                            assistantId = assistant.id,
                            workspaceRepository = workspaceRepository,
                            workspaceId = assistant.workspaceId?.toString(),
                        )
                    )
                    if (assistant.enabledSkills.isNotEmpty()) {
                        addAll(
                            createSkillTools(
                                enabledSkills = assistant.enabledSkills,
                                allSkills = skillManager.listSkills(),
                            )
                        )
                    }
                    mcpManager.getAllAvailableTools().also { allTools ->
                        val invalidNames = allTools
                            .map { it.second }
                            .distinct()
                            .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
                        if (invalidNames.isNotEmpty()) {
                            addError(
                                error = IllegalStateException(
                                    context.getString(
                                        R.string.error_mcp_invalid_server_name,
                                        invalidNames.joinToString(", ")
                                    )
                                ),
                                conversationId = conversationId,
                            )
                            return
                        }
                    }.forEach { (serverId, serverName, tool) ->
                        add(
                            Tool(
                                name = "mcp__${serverName}__${tool.name}",
                                description = tool.description ?: "",
                                parameters = { tool.inputSchema },
                                needsApproval = { tool.needsApproval },
                                execute = {
                                    mcpManager.callTool(serverId, tool.name, it.jsonObject)
                                },
                            )
                        )
                    }
                }.withAutoApprovalBypass(settings.autoApprovedTools),
            ).onCompletion {
                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = getConversationFlow(conversationId).value.copy(
                    messageNodes = getConversationFlow(conversationId).value.messageNodes.map { node ->
                        node.copy(messages = node.messages.map { it.finishReasoning() })
                    },
                    updateAt = Instant.now()
                )
                updateConversation(conversationId, updatedConversation)

                // 生成结束：取消 Live Update 通知，后台时发送完成通知
                appEventBus.emit(
                    AppEvent.ChatGenerationEnded(
                        conversationId = conversationId,
                        senderName = senderName,
                        contentPreview = updatedConversation.currentMessages.lastOrNull()
                            ?.toText()?.take(50)?.trim() ?: "",
                    )
                )
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)

                        // 通知等边缘副作用由 ChatNotificationManager 消费；
                        // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                        chunk.messages.lastOrNull()?.let { lastMessage ->
                            appEventBus.tryEmit(
                                AppEvent.ChatGenerationUpdate(conversationId, lastMessage, senderName)
                            )
                        }
                    }
                }
            }
        }.onFailure {
            if (it is CancellationException) throw it

            // 兜底取消 Live Update 通知（生成开始前失败时 onCompletion 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))

            it.printStackTrace()
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(conversationId, finalConversation)

            launchWithConversationReference(conversationId) {
                companionFacade.onGenerationCompleted(
                    conversationId = conversationId,
                    assistant = assistant,
                    settings = settings,
                    conversation = finalConversation,
                )
            }
            launchWithConversationReference(conversationId) {
                generateTitle(conversationId, finalConversation)
            }
            launchWithConversationReference(conversationId) {
                generateSuggestion(conversationId, finalConversation)
            }
        }
    }

    private fun buildWorkflowRuntimeBundle(
        assistant: Assistant,
        userText: String,
        deviceRouteTaken: Boolean,
        forcedWorkflowId: Uuid? = null,
    ): WorkflowRuntimeBundle {
        if (assistant.enabledWorkflowIds.isEmpty() && forcedWorkflowId == null) {
            return WorkflowRuntimeBundle()
        }
        val all = workflowManager.listWorkflows()
        val matched = WorkflowScheduler.matchWorkflows(
            assistant = assistant,
            all = all,
            userText = userText,
            forcedWorkflowId = forcedWorkflowId,
        )
        Log.i(
            TAG,
            "workflow match: assistant=${assistant.id} enabled=${assistant.enabledWorkflowIds.size} " +
                "all=${all.size} matched=${matched.map { it.name }} deviceRoute=$deviceRouteTaken " +
                "priority=${assistant.workflowPriority}"
        )
        return WorkflowScheduler.buildRuntimeBundle(
            assistant = assistant,
            matched = matched,
            readSkillBody = skillManager::readSkillBody,
            deviceRouteTaken = deviceRouteTaken,
        )
    }

    /**
     * DEVICE_TASK / HYBRID: stream Runtime events into an assistant message.
     * HYBRID then continues with LLM summary (PhoneControl tools stripped).
     */
    private suspend fun handleDeviceTaskRoute(
        conversationId: Uuid,
        assistant: Assistant,
        senderName: String,
        decisionGoal: String,
        hybrid: Boolean,
        settings: me.rerere.rikkahub.data.datastore.Settings? = null,
        model: Model? = null,
        companionPromptBundle: me.rerere.rikkahub.data.companion.model.CompanionPromptBundle? = null,
        messageRange: ClosedRange<Int>? = null,
        userText: String = "",
    ) {
        val progressMessage = UIMessage.assistant("正在执行手机操作…")
        val base = getConversationFlow(conversationId).value
        updateConversation(
            conversationId,
            base.copy(messageNodes = base.messageNodes + progressMessage.toMessageNode()),
        )
        appEventBus.tryEmit(
            AppEvent.ChatGenerationUpdate(conversationId, progressMessage, senderName)
        )

        val convIdStr = conversationId.toString()
        val eventJob = appScope.launch {
            agentManager.events.collect { event ->
                if (event.conversationId != null && event.conversationId != convIdStr) return@collect
                val status = when (event) {
                    is AgentRuntimeEvent.TaskStarted -> "开始执行：${event.goal}"
                    is AgentRuntimeEvent.StateUpdated -> {
                        val agentState = agentManager.agentState.value
                            ?.takeIf { it.taskId == event.taskId }
                            ?: me.rerere.rikkahub.data.agent.AgentState(
                                taskId = event.taskId,
                                goal = decisionGoal,
                                phase = event.phase,
                                currentPackage = event.currentApp,
                                currentActivity = event.currentActivity,
                                lastAction = event.lastAction,
                                lastActionResult = event.lastResult,
                            )
                        me.rerere.rikkahub.data.agent.status.AgentStatusFormatter.format(agentState)
                    }
                    // Progress / PhaseChanged / ActionStarted: UI ignores (AgentState via StateUpdated)
                    else -> null
                } ?: return@collect
                val current = getConversationFlow(conversationId).value
                val updated = current.updateCurrentMessages(
                    current.currentMessages.map { msg ->
                        if (msg.id == progressMessage.id) {
                            msg.copy(parts = listOf(UIMessagePart.Text(status)))
                        } else {
                            msg
                        }
                    }
                )
                updateConversation(conversationId, updated)
                appEventBus.tryEmit(
                    AppEvent.ChatGenerationUpdate(
                        conversationId,
                        updated.currentMessages.firstOrNull { it.id == progressMessage.id }
                            ?: progressMessage.copy(parts = listOf(UIMessagePart.Text(status))),
                        senderName,
                    )
                )
            }
        }

        val result = try {
            agentManager.submitDeviceTask(
                goal = decisionGoal,
                conversationId = convIdStr,
            )
        } catch (e: CancellationException) {
            markDeviceProgressCancelled(conversationId, progressMessage.id)
            throw e
        } finally {
            eventJob.cancel()
        }

        if (!hybrid) {
            val finalText = when {
                result.success -> result.summary
                result.summary.equals("cancelled", ignoreCase = true) ||
                    result.summary.equals("chat_stop", ignoreCase = true) ||
                    result.summary.equals("chat_interrupt", ignoreCase = true) ||
                    result.summary.equals("user_stop", ignoreCase = true) ||
                    result.summary.equals("superseded", ignoreCase = true) -> "操作已取消"
                else -> "操作未完成：${result.summary}"
            }
            val current = getConversationFlow(conversationId).value
            val updated = current.updateCurrentMessages(
                current.currentMessages.map { msg ->
                    if (msg.id == progressMessage.id) {
                        msg.copy(parts = listOf(UIMessagePart.Text(finalText)))
                    } else {
                        msg
                    }
                }
            )
            updateConversation(conversationId, updated)
            appEventBus.tryEmit(
                AppEvent.ChatGenerationEnded(
                    conversationId,
                    senderName,
                    finalText.take(50),
                )
            )
            return
        }

        // HYBRID: LLM summarizes without PhoneControl tools
        val resolvedSettings = settings ?: settingsStore.settingsFlow.first()
        val resolvedModel = model
            ?: resolvedSettings.findModelById(assistant.chatModelId ?: resolvedSettings.chatModelId)
            ?: return
        val runtimeNote =
            "\n\n[AgentRuntime] goal=${result.goal}\nsuccess=${result.success}\n${result.summary}\n请用自然语言简要告知用户操作结果，不要再调用手机控制工具。"
        val conversation = getConversationFlow(conversationId).value
        // Drop progress message before LLM turn; LLM reply becomes the assistant answer.
        val withoutProgress = conversation.copy(
            messageNodes = conversation.messageNodes.filterNot { node ->
                node.messages.any { it.id == progressMessage.id }
            }
        )
        updateConversation(conversationId, withoutProgress)

        val session = getOrCreateSession(conversationId)
        val messages = withoutProgress.currentMessages.let {
            if (messageRange != null) {
                it.subList(messageRange.start, messageRange.endInclusive + 1)
            } else {
                it
            }
        }
        val conversationForGeneration = ensureRollingSummary(
            conversationId = conversationId,
            conversation = withoutProgress,
            assistant = assistant,
            settings = resolvedSettings,
            messages = messages,
            processingStatus = session.processingStatus,
        )
        val workflowBundle = buildWorkflowRuntimeBundle(
            assistant = assistant,
            userText = userText,
            deviceRouteTaken = true,
        )
        val localOpts = assistant.localTools.filter { it !is LocalToolOption.PhoneControl }
        generationHandler.generateText(
            settings = resolvedSettings,
            model = resolvedModel,
            processingStatus = session.processingStatus,
            messages = conversationForGeneration.currentMessages.let {
                if (messageRange != null) {
                    it.subList(messageRange.start, messageRange.endInclusive + 1)
                } else {
                    it
                }
            },
            assistant = assistant,
            conversationSystemPrompt = (conversationForGeneration.customSystemPrompt ?: "") + runtimeNote,
            conversationModeInjectionIds = conversationForGeneration.modeInjectionIds,
            conversationLorebookIds = conversationForGeneration.lorebookIds,
            companionPromptBundle = companionPromptBundle,
            workspaceCwd = conversationForGeneration.workspaceCwd,
            rollingSummary = conversationForGeneration.rollingSummary,
            carryoverOverview = conversationForGeneration.carryoverOverview,
            workflowBundle = workflowBundle,
            memories = if (assistant.useGlobalMemory) {
                memoryRepository.getGlobalMemories()
            } else {
                memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
            },
            inputTransformers = buildList {
                addAll(inputTransformers)
                add(templateTransformer)
                add(workspaceReminderTransformer)
            },
            outputTransformers = outputTransformers,
            tools = buildList {
                if (assistant.enableWebSearch) {
                    addAll(createSearchTools(resolvedSettings))
                }
                addAll(localTools.getTools(localOpts))
                if (assistant.contextMessageLimit > 0) {
                    add(
                        buildRecallChatHistoryTool(
                            getMessages = { getConversationFlow(conversationId).value.currentMessages },
                            contextMessageLimit = assistant.contextMessageLimit,
                        )
                    )
                }
            }.withAutoApprovalBypass(resolvedSettings.autoApprovedTools),
        ).onCompletion {
            val updatedConversation = getConversationFlow(conversationId).value.copy(
                messageNodes = getConversationFlow(conversationId).value.messageNodes.map { node ->
                    node.copy(messages = node.messages.map { it.finishReasoning() })
                },
                updateAt = java.time.Instant.now(),
            )
            updateConversation(conversationId, updatedConversation)
            appEventBus.emit(
                AppEvent.ChatGenerationEnded(
                    conversationId = conversationId,
                    senderName = senderName,
                    contentPreview = updatedConversation.currentMessages.lastOrNull()
                        ?.toText()?.take(50)?.trim() ?: "",
                )
            )
        }.collect { chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> {
                    val updatedConversation = getConversationFlow(conversationId).value
                        .updateCurrentMessages(chunk.messages)
                    updateConversation(conversationId, updatedConversation)
                    chunk.messages.lastOrNull()?.let { lastMessage ->
                        appEventBus.tryEmit(
                            AppEvent.ChatGenerationUpdate(conversationId, lastMessage, senderName)
                        )
                    }
                }
            }
        }
    }

    private suspend fun createWorkspaceToolsIfReady(workspaceId: String?, cwd: String? = null): List<Tool> {
        if (workspaceId.isNullOrBlank()) return emptyList()
        val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        return createWorkspaceTools(workspaceId, workspaceRepository, cwd)
    }

    // ---- 检查无效消息 ----

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { _, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Keep messages that are ready to resume, such as approved/denied/answered tools.
                val hasResumableTool = node.currentMessage.getTools().any {
                    !it.isExecuted && it.approvalState.canResumeToolExecution()
                }
                if (hasResumableTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove messages that still have unresolved tool approvals.
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            ),
            approvalState = ToolApprovalState.Denied("Generation cancelled by user")
        )
    }

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        saveConversation(conversationId, updatedConversation)
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.titleModelId, fallback = settings.fastModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText(maxLength = 500) })
                    ),
                ),
                params = backgroundTextGenerationParams(model),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.choices[0].message?.toText()?.trim() ?: "")
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckTitleModelSettings,
            )
        }
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            if (!settings.enableSuggestion) return
            val model = settings.findModelById(settings.suggestionModelId, fallback = settings.fastModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            sessions[conversationId]?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText(maxLength = 500) }),
                    )
                ),
                params = backgroundTextGenerationParams(model),
            )
            val suggestions =
                result.choices[0].message?.toText()?.split("\n")?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessions[conversationId]?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    // ---- 压缩对话历史 ----

    /**
     * Soft rolling summary before generation when contextMessageLimit would drop a prefix.
     * On failure, keeps prior summary (if any) and surfaces an error; generation still proceeds.
     */
    private suspend fun ensureRollingSummary(
        conversationId: Uuid,
        conversation: Conversation,
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?>,
    ): Conversation {
        if (!assistant.enableAutoContextSummary || assistant.contextMessageLimit <= 0) {
            return conversation
        }

        val plan = planRollingSummaryUpdate(
            messages = messages,
            contextMessageLimit = assistant.contextMessageLimit,
            existingSummary = conversation.rollingSummary,
            coveredCount = conversation.rollingSummaryCoveredCount,
        ) ?: return conversation

        processingStatus.value = context.getString(R.string.chat_page_organizing_context)
        return try {
            val summary = conversationCompressHelper.buildRollingSummary(
                settings = settings,
                request = plan,
                targetTokens = DEFAULT_AUTO_SUMMARY_TARGET_TOKENS,
            )
            val updated = conversation.copy(
                rollingSummary = summary,
                rollingSummaryCoveredCount = plan.coverCount,
            )
            saveConversation(conversationId, updated)
            updated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ensureRollingSummary failed", e)
            addError(
                error = e,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_auto_context_summary),
            )
            conversation
        } finally {
            if (processingStatus.value == context.getString(R.string.chat_page_organizing_context)) {
                processingStatus.value = null
            }
        }
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.state.value = conversation
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     *
     * 若该会话当前有活跃 session（正在查看或后台生成），先同步内存态再落库：
     * 否则仅改数据库 folder_id，而内存里那份 Conversation 仍是旧 folderId，
     * 后续任意 saveConversation(id, state.value) 会用整对象把 folder_id 覆盖回旧值，导致移动丢失。
     * 先改内存可确保这段窗口内的整对象保存也带上新 folderId。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessions.containsKey(conversationId)) {
            updateConversationState(conversationId) { it.copy(folderId = folderId) }
        }
        conversationRepo.updateConversationFolderId(conversationId, folderId)
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessions.values.any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     *
     * 先把内存中归属该文件夹的活跃 session folderId 置空，再删库：
     * 否则 clearFolder 只改了数据库，而活跃 session 内存态仍指向该文件夹，
     * 后续整对象保存会写回一个已被删除的 folder_id，导致会话在列表中悬空。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessions.values
            .filter { it.state.value.folderId == folderId }
            .forEach { updateConversationState(it.id) { c -> c.copy(folderId = null) } }
        folderRepository.deleteFolder(folderId)
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return // 新会话且为空时不保存
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                generationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(currentConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val processedParts = preprocessUserInputParts(parts, assistant)
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = processedParts,
                ),
                selectIndex = node.messages.size
            )
        }

        if (!edited) return

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val forkConversation = Conversation(
            id = Uuid.random(),
            assistantId = currentConversation.assistantId,
            messageNodes = copiedNodes,
            customSystemPrompt = currentConversation.customSystemPrompt,
            modeInjectionIds = currentConversation.modeInjectionIds,
            lorebookIds = currentConversation.lorebookIds,
        )

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        saveConversation(conversationId, updatedConversation)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        val job = sessions[conversationId]?.getJob()
        job?.cancel()
        // Await device-task shutdown so a follow-up message cannot race with the old Runtime job.
        agentManagerOrNull()?.cancelAndJoin("chat_stop")
        runCatching { job?.join() }
        finishInterruptedPendingTools(conversationId)
    }

    private fun markDeviceProgressCancelled(conversationId: Uuid, progressMessageId: Uuid) {
        val current = getConversationFlow(conversationId).value
        val updated = current.updateCurrentMessages(
            current.currentMessages.map { msg ->
                if (msg.id == progressMessageId) {
                    msg.copy(parts = listOf(UIMessagePart.Text("操作已取消")))
                } else {
                    msg
                }
            }
        )
        updateConversation(conversationId, updated)
    }
}
