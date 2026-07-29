package com.agent.chat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.error.AppErrorException
import com.agent.chat.data.error.AppErrorLogger
import com.agent.chat.data.error.AppErrorMapper
import com.agent.chat.data.care.HomeLocationStore
import com.agent.chat.data.care.LocationSnapshotProvider
import com.agent.chat.data.interaction.InteractionPreferenceStore
import com.agent.chat.data.memory.MemorySettingsStore
import com.agent.chat.data.proactive.ProactiveNudgeWorker
import com.agent.chat.data.ai.prompt.PromptLogEntry
import com.agent.chat.data.ai.prompt.PromptLogger
import com.agent.chat.data.provider.ProviderDefaults
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.data.settings.ChatSettings
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.data.settings.ToolSettings
import com.agent.chat.data.settings.ToolSettingsStore
import com.agent.chat.domain.error.userMessage
import com.agent.chat.domain.model.InteractionPreference
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.ProviderConfig
import com.agent.chat.domain.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val isEditorOpen: Boolean = false,
    val editingId: String? = null,
    val editorName: String = "",
    val editorBaseUrl: String = ProviderDefaults.DEFAULT_BASE_URL,
    val editorApiKey: String = "",
    val editorModelName: String = ProviderDefaults.DEFAULT_MODEL_NAME,
    val editorProviderType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val isTesting: Boolean = false,
    val testingProviderId: String? = null,
    val statusMessage: String? = null,
    val inlineError: String? = null,
    val inlineDebugDetail: String? = null,
    val showDeveloperOptions: Boolean = false,
    val summaryProviderId: String? = null,
    val extractThreshold: Int = MemorySettingsStore.DEFAULT_THRESHOLD,
    val extractTokenEstimate: Int = MemorySettingsStore.estimateExtractTokens(
        MemorySettingsStore.DEFAULT_THRESHOLD,
    ),
    val promptMemoryTokenEstimate: Int = MemorySettingsStore.estimatePromptMemoryTokensByBudget(),
    val naturalChatPaceEnabled: Boolean = true,
    val companionStyleEnabled: Boolean = true,
    val chatMode: LingBanChatMode = LingBanChatMode.COMPANION,
    val responseControllerEnabled: Boolean = true,
    val splitBubbleByNewline: Boolean = true,
    val userNickname: String = "",
    val proactiveEnabled: Boolean = false,
    val proactiveIdleHours: Int = 6,
    val toolSettings: ToolSettings = ToolSettings(),
    val interactionPreference: InteractionPreference = InteractionPreference(),
    val lastPromptLog: PromptLogEntry? = null,
    val showPromptLogDialog: Boolean = false,
    val hasHomeLocation: Boolean = false,
    val homeRadiusMeters: Int = 300,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val providerConfigRepository: ProviderConfigRepository,
    private val memorySettingsStore: MemorySettingsStore,
    private val chatSettingsStore: ChatSettingsStore,
    private val interactionPreferenceStore: InteractionPreferenceStore,
    private val toolSettingsStore: ToolSettingsStore,
    private val homeLocationStore: HomeLocationStore,
    private val locationSnapshotProvider: LocationSnapshotProvider,
    private val promptLogger: PromptLogger,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var titleTapCount = 0

    init {
        viewModelScope.launch {
            combine(
                providerConfigRepository.observeConfigs(),
                memorySettingsStore.snapshot,
                chatSettingsStore.snapshot,
                interactionPreferenceStore.snapshot,
                toolSettingsStore.snapshot,
            ) { providers, memory, chat, interaction, tools ->
                SettingsSnapshot(providers, memory, chat, interaction, tools)
            }.collect { snap ->
                _uiState.update {
                    it.copy(
                        providers = snap.providers,
                        summaryProviderId = snap.memory.summaryProviderId,
                        extractThreshold = snap.memory.extractThreshold,
                        extractTokenEstimate = MemorySettingsStore.estimateExtractTokens(
                            snap.memory.extractThreshold,
                        ),
                        promptMemoryTokenEstimate = MemorySettingsStore.estimatePromptMemoryTokensByBudget(),
                        naturalChatPaceEnabled = snap.chat.naturalChatPaceEnabled,
                        companionStyleEnabled = snap.chat.companionStyleEnabled,
                        chatMode = snap.chat.chatMode,
                        responseControllerEnabled = snap.chat.responseControllerEnabled,
                        splitBubbleByNewline = snap.chat.splitBubbleByNewline,
                        userNickname = snap.chat.userNickname,
                        proactiveEnabled = snap.chat.proactiveEnabled,
                        proactiveIdleHours = snap.chat.proactiveIdleHours,
                        interactionPreference = snap.interaction,
                        toolSettings = snap.tools,
                    )
                }
            }
        }
        viewModelScope.launch {
            homeLocationStore.snapshot.collect { home ->
                _uiState.update {
                    it.copy(
                        hasHomeLocation = home.hasHome,
                        homeRadiusMeters = home.radiusMeters,
                    )
                }
            }
        }
        viewModelScope.launch {
            promptLogger.lastEntry.collect { entry ->
                _uiState.update { it.copy(lastPromptLog = entry) }
            }
        }
    }

    fun openPromptLog() {
        _uiState.update { it.copy(showPromptLogDialog = true) }
    }

    fun dismissPromptLog() {
        _uiState.update { it.copy(showPromptLogDialog = false) }
    }

    fun onTitleTapped() {
        titleTapCount += 1
        if (titleTapCount >= 7) {
            titleTapCount = 0
            _uiState.update { it.copy(showDeveloperOptions = !it.showDeveloperOptions) }
            _uiState.update {
                it.copy(
                    statusMessage = if (it.showDeveloperOptions) {
                        "已开启开发者选项"
                    } else {
                        "已关闭开发者选项"
                    },
                )
            }
        }
    }

    fun setSummaryProvider(providerId: String?) {
        memorySettingsStore.setSummaryProviderId(providerId)
        _uiState.update {
            it.copy(
                statusMessage = if (providerId == null) {
                    "摘要模型已改为跟随主对话"
                } else {
                    "已设置摘要专用模型"
                },
            )
        }
    }

    fun setExtractThreshold(threshold: Int) {
        memorySettingsStore.setExtractThreshold(threshold)
        _uiState.update {
            it.copy(statusMessage = "摘要触发阈值已设为 ${memorySettingsStore.get().extractThreshold} 条")
        }
    }

    fun setNaturalChatPaceEnabled(enabled: Boolean) {
        chatSettingsStore.setNaturalChatPaceEnabled(enabled)
        _uiState.update {
            it.copy(
                statusMessage = if (enabled) "已开启模拟真实聊天节奏" else "已关闭：回复将一次性完整显示",
            )
        }
    }

    fun setCompanionStyleEnabled(enabled: Boolean) {
        chatSettingsStore.setCompanionStyleEnabled(enabled)
        _uiState.update {
            it.copy(statusMessage = if (enabled) "已开启真人聊天基础约束" else "已关闭真人聊天基础约束")
        }
    }

    fun setRolePlayEnabled(enabled: Boolean) {
        chatSettingsStore.setRolePlayEnabled(enabled)
        _uiState.update {
            it.copy(statusMessage = if (enabled) "已切换到角色扮演模式" else "已切回伴侣模式")
        }
    }

    fun setChatMode(mode: LingBanChatMode) {
        chatSettingsStore.setChatMode(mode)
        _uiState.update { it.copy(statusMessage = "已切换为${mode.displayName}") }
    }

    fun setResponseControllerEnabled(enabled: Boolean) {
        chatSettingsStore.setResponseControllerEnabled(enabled)
        _uiState.update {
            it.copy(
                statusMessage = if (enabled) {
                    "已开启 Response Controller（不达标会重生成）"
                } else {
                    "已关闭 Response Controller"
                },
            )
        }
    }

    fun setSplitBubbleByNewline(enabled: Boolean) {
        chatSettingsStore.setSplitBubbleByNewline(enabled)
        _uiState.update {
            it.copy(statusMessage = if (enabled) "优先按换行拆气泡" else "按句号拆气泡")
        }
    }

    fun setUserNickname(nickname: String) {
        chatSettingsStore.setUserNickname(nickname)
    }

    fun setProactiveEnabled(enabled: Boolean) {
        chatSettingsStore.setProactiveEnabled(enabled)
        ProactiveNudgeWorker.schedule(getApplication(), enabled)
        _uiState.update {
            it.copy(statusMessage = if (enabled) "已开启闲置主动问候" else "已关闭主动问候")
        }
    }

    fun setProactiveIdleHours(hours: Int) {
        chatSettingsStore.setProactiveIdleHours(hours)
        _uiState.update { it.copy(statusMessage = "闲置 ${chatSettingsStore.get().proactiveIdleHours} 小时后可能主动找你") }
    }

    fun setHomeRadius(radiusMeters: Int) {
        homeLocationStore.setHomeRadius(radiusMeters)
        _uiState.update {
            it.copy(statusMessage = if (homeLocationStore.snapshot.value.hasHome) {
                "家位置半径已设置为 ${radiusMeters} 米"
            } else {
                "请先设置家位置，再调整半径"
            })
        }
    }

    fun setHomeToCurrentLocation(radiusMeters: Int? = null) {
        val targetRadius = radiusMeters ?: _uiState.value.homeRadiusMeters
        viewModelScope.launch {
            val snapshot = locationSnapshotProvider.getLastKnown()
            if (snapshot == null) {
                _uiState.update {
                    it.copy(
                        statusMessage = "暂时拿不到定位。请先在「工具权限」里开启定位并授予权限。",
                    )
                }
                return@launch
            }
            homeLocationStore.setHome(
                latitude = snapshot.latitude,
                longitude = snapshot.longitude,
                radiusMeters = targetRadius,
            )
            _uiState.update { it.copy(statusMessage = "已设置家位置（半径 ${targetRadius} 米）") }
        }
    }

    fun clearHomeLocation() {
        homeLocationStore.clearHome()
        _uiState.update { it.copy(statusMessage = "已清除家位置") }
    }

    fun setRomanticConversation(enabled: Boolean) {
        interactionPreferenceStore.setRomanticConversation(enabled)
        _uiState.update {
            it.copy(statusMessage = if (enabled) "已允许浪漫对话（需用户主动）" else "已关闭浪漫对话")
        }
    }

    fun setFlirting(enabled: Boolean) {
        interactionPreferenceStore.setFlirting(enabled)
        _uiState.update {
            it.copy(statusMessage = if (enabled) "已允许暧昧互动（需用户主动）" else "已关闭暧昧互动")
        }
    }

    fun setIntimateConversation(enabled: Boolean) {
        interactionPreferenceStore.setIntimateConversation(enabled)
        _uiState.update {
            it.copy(statusMessage = if (enabled) "已允许亲密对话（需用户主动）" else "已关闭亲密对话")
        }
    }

    fun setInteractionRoleplay(enabled: Boolean) {
        interactionPreferenceStore.setRoleplay(enabled)
        _uiState.update {
            it.copy(statusMessage = if (enabled) "已允许角色扮演（需用户主动）" else "已关闭角色扮演")
        }
    }

    fun updateToolSettings(transform: (ToolSettings) -> ToolSettings) {
        toolSettingsStore.update(transform)
    }

    fun openCreateEditor() {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editingId = null,
                editorName = "",
                editorBaseUrl = ProviderDefaults.DEFAULT_BASE_URL,
                editorApiKey = "",
                editorModelName = ProviderDefaults.DEFAULT_MODEL_NAME,
                editorProviderType = ProviderType.OPENAI_COMPATIBLE,
                inlineError = null,
                inlineDebugDetail = null,
            )
        }
    }

    fun openEditEditor(config: ProviderConfig) {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editingId = config.id,
                editorName = config.name,
                editorBaseUrl = config.baseUrl,
                editorApiKey = config.apiKey,
                editorModelName = config.modelName,
                editorProviderType = config.providerType,
                inlineError = null,
                inlineDebugDetail = null,
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                isEditorOpen = false,
                editingId = null,
                inlineError = null,
                inlineDebugDetail = null,
            )
        }
    }

    fun onEditorNameChange(value: String) = _uiState.update {
        it.copy(editorName = value, inlineError = null, inlineDebugDetail = null)
    }

    fun onEditorBaseUrlChange(value: String) = _uiState.update {
        it.copy(editorBaseUrl = value, inlineError = null, inlineDebugDetail = null)
    }

    fun onEditorApiKeyChange(value: String) = _uiState.update {
        it.copy(editorApiKey = value, inlineError = null, inlineDebugDetail = null)
    }

    fun onEditorModelNameChange(value: String) = _uiState.update {
        it.copy(editorModelName = value, inlineError = null, inlineDebugDetail = null)
    }

    fun saveEditor() {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                val editingId = state.editingId
                if (editingId == null) {
                    providerConfigRepository.createConfig(
                        name = state.editorName,
                        baseUrl = state.editorBaseUrl,
                        apiKey = state.editorApiKey,
                        modelName = state.editorModelName,
                        providerType = state.editorProviderType,
                    )
                } else {
                    providerConfigRepository.updateConfig(
                        ProviderConfig(
                            id = editingId,
                            name = state.editorName,
                            baseUrl = state.editorBaseUrl,
                            apiKey = state.editorApiKey,
                            modelName = state.editorModelName,
                            providerType = state.editorProviderType,
                        ),
                    )
                }
                _uiState.update {
                    it.copy(
                        isEditorOpen = false,
                        editingId = null,
                        statusMessage = "已保存",
                        inlineError = null,
                        inlineDebugDetail = null,
                    )
                }
            } catch (e: Exception) {
                val appError = AppErrorMapper.from(e)
                AppErrorLogger.log(e, appError)
                _uiState.update {
                    it.copy(
                        inlineError = appError.userMessage(),
                        inlineDebugDetail = debugDetail(e, it.showDeveloperOptions),
                    )
                }
            }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            if (_uiState.value.summaryProviderId == id) {
                memorySettingsStore.setSummaryProviderId(null)
            }
            providerConfigRepository.deleteConfig(id)
            _uiState.update { it.copy(statusMessage = "已删除") }
        }
    }

    fun testProvider(config: ProviderConfig) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTesting = true,
                    testingProviderId = config.id,
                    inlineError = null,
                    inlineDebugDetail = null,
                )
            }
            val result = providerConfigRepository.testConnection(config)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { msg ->
                        state.copy(
                            isTesting = false,
                            testingProviderId = null,
                            statusMessage = msg,
                            inlineError = null,
                            inlineDebugDetail = null,
                        )
                    },
                    onFailure = { err ->
                        val appError = (err as? AppErrorException)?.appError
                            ?: AppErrorMapper.from(err)
                        AppErrorLogger.log(err, appError)
                        state.copy(
                            isTesting = false,
                            testingProviderId = null,
                            statusMessage = if (state.isEditorOpen) null else appError.userMessage(),
                            inlineError = if (state.isEditorOpen) appError.userMessage() else null,
                            inlineDebugDetail = if (state.isEditorOpen) {
                                debugDetail(err, state.showDeveloperOptions)
                            } else {
                                null
                            },
                        )
                    },
                )
            }
        }
    }

    fun testEditorConfig() {
        val state = _uiState.value
        val draft = ProviderConfig(
            id = state.editingId ?: "draft_test",
            name = state.editorName.ifBlank { "测试" },
            baseUrl = state.editorBaseUrl,
            apiKey = state.editorApiKey,
            modelName = state.editorModelName,
            providerType = state.editorProviderType,
        )
        testProvider(draft)
    }

    fun consumeStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun debugDetail(e: Throwable, developerOn: Boolean): String? {
        if (!developerOn && !com.agent.chat.BuildConfig.DEBUG) return null
        return "${e.javaClass.simpleName}: ${e.message?.take(200).orEmpty()}"
    }

    private data class SettingsSnapshot(
        val providers: List<ProviderConfig>,
        val memory: com.agent.chat.data.memory.MemorySettings,
        val chat: ChatSettings,
        val interaction: InteractionPreference,
        val tools: ToolSettings,
    )
}
