package com.agent.chat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.repository.PersonaRepository
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.Persona
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val greeting: String = HomeGreeting.forHour(),
    val userNickname: String = "",
    val orbState: AiOrbState = AiOrbState.Idle,
    val recentConversation: Conversation? = null,
    val recentMemory: Memory? = null,
    val recommendedPersona: Persona? = null,
    val entranceReady: Boolean = false,
    val hasExplored: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val personaRepository: PersonaRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    private val chatSettingsStore: ChatSettingsStore,
) : ViewModel() {

    private val orbState = MutableStateFlow(AiOrbState.Idle)

    val uiState: StateFlow<HomeUiState> = combine(
        chatRepository.observeConversations(),
        memoryRepository.observeRecent(limit = 1),
        personaRepository.observePersonas(),
        chatSettingsStore.snapshot,
        orbState,
    ) { conversations, memories, personas, settings, orb ->
        HomeUiState(
            greeting = HomeGreeting.forHour(),
            userNickname = settings.userNickname,
            orbState = orb,
            recentConversation = conversations.firstOrNull(),
            recentMemory = memories.firstOrNull(),
            recommendedPersona = personas.firstOrNull(),
            entranceReady = true,
            hasExplored = settings.hasExplored || conversations.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private val _openConversationId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openConversationId: SharedFlow<String> = _openConversationId.asSharedFlow()

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()

    private val _navigateAgentCenter = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateAgentCenter: SharedFlow<Unit> = _navigateAgentCenter.asSharedFlow()

    private val _navigateMemory = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateMemory: SharedFlow<Unit> = _navigateMemory.asSharedFlow()

    private val entranceStarted = MutableStateFlow(false)
    val showEntrance: StateFlow<Boolean> = entranceStarted.asStateFlow()

    init {
        viewModelScope.launch {
            delay(40)
            entranceStarted.value = true
        }
    }

    fun onStartExplore() {
        chatSettingsStore.setHasExplored(true)
        onEnterChat()
    }

    /** 点击 Orb / 最近聊天：进入最近会话，否则新建。 */
    fun onEnterChat() {
        viewModelScope.launch {
            if (!chatSettingsStore.get().hasExplored) {
                chatSettingsStore.setHasExplored(true)
            }
            val recent = uiState.value.recentConversation
            if (recent != null) {
                pulseSpeakingBriefly()
                _openConversationId.emit(recent.id)
                return@launch
            }
            createAndOpenConversation(personaId = null)
        }
    }

    fun onRecentMemoryClick() {
        viewModelScope.launch {
            _navigateMemory.emit(Unit)
        }
    }

    fun onRecommendedAgentClick() {
        viewModelScope.launch {
            val persona = uiState.value.recommendedPersona
            if (persona != null) {
                createAndOpenConversation(personaId = persona.id)
            } else {
                _navigateAgentCenter.emit(Unit)
            }
        }
    }

    fun onOpenAgentCenter() {
        viewModelScope.launch {
            _navigateAgentCenter.emit(Unit)
        }
    }

    private suspend fun createAndOpenConversation(personaId: String?) {
        orbState.value = AiOrbState.Thinking
        val providerId = providerConfigRepository.getDefaultConfig()?.id
        if (providerId.isNullOrBlank()) {
            orbState.value = AiOrbState.Idle
            _statusMessage.emit("请先在设置中添加 Provider")
            return
        }
        val persona = personaId?.let { personaRepository.getPersona(it) }
        val title = persona?.name?.let { "$it 的会话" } ?: "新会话"
        val conversation = chatRepository.createConversation(
            title = title,
            personaId = personaId,
            providerConfigId = providerId,
        )
        pulseSpeakingBriefly()
        _openConversationId.emit(conversation.id)
    }

    private fun pulseSpeakingBriefly() {
        viewModelScope.launch {
            orbState.update { AiOrbState.Speaking }
            delay(900)
            orbState.update { AiOrbState.Idle }
        }
    }
}
