package com.agent.chat.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.persona.StarterAgentSeeder
import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.repository.PersonaRepository
import com.agent.chat.domain.model.Persona
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AgentCardUi(
    val persona: Persona,
    val capabilities: List<String>,
    val usageCount: Int,
    val memoryCount: Int,
) {
    val memoryScope: String get() = AgentCapabilities.memoryScopeLabel(memoryCount)
}

data class AgentCenterUiState(
    val agents: List<AgentCardUi> = emptyList(),
    val entranceReady: Boolean = false,
)

@HiltViewModel
class AgentCenterViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val starterAgentSeeder: StarterAgentSeeder,
) : ViewModel() {

    val uiState: StateFlow<AgentCenterUiState> = combine(
        personaRepository.observePersonas(),
        chatRepository.observePersonaUsageCounts(),
        memoryRepository.observeAll(),
    ) { personas, usage, memories ->
        val memoryByPersona = memories.groupingBy { it.personaId }.eachCount()
        AgentCenterUiState(
            agents = personas.map { persona ->
                AgentCardUi(
                    persona = persona,
                    capabilities = AgentCapabilities.of(persona),
                    usageCount = usage[persona.id] ?: 0,
                    memoryCount = memoryByPersona[persona.id] ?: 0,
                )
            },
            entranceReady = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AgentCenterUiState(),
    )

    init {
        viewModelScope.launch {
            starterAgentSeeder.ensureStarterAgents()
        }
    }

    suspend fun deleteAgent(id: String) {
        personaRepository.deletePersona(id)
    }
}
