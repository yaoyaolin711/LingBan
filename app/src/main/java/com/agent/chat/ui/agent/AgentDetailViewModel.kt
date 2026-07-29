package com.agent.chat.ui.agent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.repository.PersonaRepository
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.domain.model.Persona
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AgentDetailUiState(
    val persona: Persona? = null,
    val capabilities: List<String> = emptyList(),
    val usageCount: Int = 0,
    val memoryCount: Int = 0,
    val memoryScope: String = "",
)

@HiltViewModel
class AgentDetailViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val personaId: String =
        checkNotNull(savedStateHandle["personaId"]) { "personaId required" }

    val uiState: StateFlow<AgentDetailUiState> = combine(
        personaRepository.observePersonas().map { list -> list.find { it.id == personaId } },
        chatRepository.observePersonaUsageCounts(),
        memoryRepository.observeByPersona(personaId),
    ) { persona, usage, memories ->
        AgentDetailUiState(
            persona = persona,
            capabilities = persona?.let { AgentCapabilities.of(it) }.orEmpty(),
            usageCount = usage[personaId] ?: 0,
            memoryCount = memories.size,
            memoryScope = AgentCapabilities.memoryScopeLabel(memories.size),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AgentDetailUiState(),
    )

    private val _openConversationId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openConversationId: SharedFlow<String> = _openConversationId.asSharedFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    fun startChat() {
        viewModelScope.launch {
            val persona = uiState.value.persona ?: return@launch
            val providerId = providerConfigRepository.getDefaultConfig()?.id
            if (providerId.isNullOrBlank()) {
                _events.emit("请先在设置中添加 Provider")
                return@launch
            }
            val conversation = chatRepository.createConversation(
                title = "${persona.name} 的会话",
                personaId = persona.id,
                providerConfigId = providerId,
            )
            _openConversationId.emit(conversation.id)
        }
    }

    fun deleteAgent() {
        viewModelScope.launch {
            personaRepository.deletePersona(personaId)
            _deleted.emit(Unit)
            _events.emit("已与这位伙伴告别")
        }
    }
}
