package me.rerere.rikkahub.ui.pages.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import kotlin.uuid.Uuid

data class HomeUiState(
    val assistant: Assistant? = null,
    val recentConversation: Conversation? = null,
    val memoryCount: Int = 0,
    val recentMemoryPreview: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeVM(
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = settingsStore.settingsFlow
        .mapLatest { buildState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun newConversationId(): Uuid = Uuid.random()

    private suspend fun buildState(settings: Settings): HomeUiState {
        val assistant = settings.getCurrentAssistant()
        val recent = runCatching {
            conversationRepo.getRecentConversations(assistant.id, limit = 1).firstOrNull()
        }.getOrNull()
        val memories = runCatching {
            memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
        }.getOrDefault(emptyList())
        return HomeUiState(
            assistant = assistant,
            recentConversation = recent,
            memoryCount = memories.size,
            recentMemoryPreview = memories.firstOrNull()?.content,
        )
    }
}
