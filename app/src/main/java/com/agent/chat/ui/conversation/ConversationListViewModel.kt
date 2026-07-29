package com.agent.chat.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.network.NetworkMonitor
import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.data.repository.PersonaRepository
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.domain.error.AppError
import com.agent.chat.domain.error.userMessage
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    val personas: List<Persona> = emptyList(),
    val providers: List<ProviderConfig> = emptyList(),
    val searchQuery: String = "",
    val showCreateDialog: Boolean = false,
    val selectedPersonaId: String? = null,
    val selectedProviderId: String? = null,
)

private data class CreateDialogState(
    val show: Boolean = false,
    val selectedPersonaId: String? = null,
    val selectedProviderId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val dialogState = MutableStateFlow(CreateDialogState())
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ConversationListUiState> = combine(
        searchQuery.flatMapLatest { query -> chatRepository.searchConversations(query) },
        personaRepository.observePersonas(),
        providerConfigRepository.observeConfigs(),
        dialogState,
        searchQuery,
    ) { conversations, personas, providers, dialog, query ->
        ConversationListUiState(
            conversations = conversations,
            personas = personas,
            providers = providers,
            searchQuery = query,
            showCreateDialog = dialog.show,
            selectedPersonaId = dialog.selectedPersonaId,
            selectedProviderId = dialog.selectedProviderId
                ?: providers.firstOrNull()?.id,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConversationListUiState(),
    )

    private val _createdConversationId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val createdConversationId: SharedFlow<String> = _createdConversationId.asSharedFlow()

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            if (!networkMonitor.isNetworkAvailable()) {
                _statusMessage.emit(AppError.NetworkUnavailable.userMessage())
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun openCreateDialog() {
        viewModelScope.launch {
            val defaultProviderId = providerConfigRepository.getDefaultConfig()?.id
            dialogState.value = CreateDialogState(
                show = true,
                selectedPersonaId = null,
                selectedProviderId = defaultProviderId,
            )
        }
    }

    fun dismissCreateDialog() {
        dialogState.value = CreateDialogState()
    }

    fun selectPersona(personaId: String?) {
        dialogState.update { it.copy(selectedPersonaId = personaId) }
    }

    fun selectProvider(providerId: String) {
        dialogState.update { it.copy(selectedProviderId = providerId) }
    }

    fun confirmCreateConversation() {
        viewModelScope.launch {
            val state = uiState.value
            val providerId = state.selectedProviderId
            if (providerId.isNullOrBlank()) {
                _statusMessage.emit("请先在设置中添加 Provider")
                return@launch
            }
            val persona = state.selectedPersonaId?.let { personaRepository.getPersona(it) }
            val title = persona?.name?.let { "$it 的会话" } ?: "新会话"
            dialogState.value = CreateDialogState()
            val conversation = chatRepository.createConversation(
                title = title,
                personaId = state.selectedPersonaId,
                providerConfigId = providerId,
            )
            _createdConversationId.emit(conversation.id)
        }
    }
}
