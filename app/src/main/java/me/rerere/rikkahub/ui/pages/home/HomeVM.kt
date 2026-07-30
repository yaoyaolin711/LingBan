package me.rerere.rikkahub.ui.pages.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid

data class HomeUiState(
    val assistant: Assistant? = null,
    val recentConversation: Conversation? = null,
    val memoryCount: Int = 0,
    val recentMemoryPreview: String? = null,
    /** Conversation count with current companion — shown as interaction metric. */
    val interactionCount: Int = 0,
    /** Days since first conversation with this companion. */
    val companionDays: Long = 0,
)

/**
 * Soft relationship tier derived purely for UI display (no domain state machine).
 */
fun HomeUiState.relationshipLevelKey(): String = when {
    interactionCount <= 0 -> "new"
    interactionCount < 5 -> "acquaintance"
    interactionCount < 15 -> "familiar"
    interactionCount < 40 -> "close"
    else -> "bonded"
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeVM(
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {
    val uiState = settingsStore.settingsFlow
        .mapLatest { buildState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun newConversationId(): Uuid = Uuid.random()

    private suspend fun buildState(settings: Settings): HomeUiState {
        val assistant = settings.getCurrentAssistant()
        val conversations = runCatching {
            conversationRepo.getConversationsOfAssistant(assistant.id).first()
        }.getOrDefault(emptyList())
        val recent = conversations.maxByOrNull { it.updateAt }
        val earliest = conversations.minByOrNull { it.createAt }?.createAt
        val companionDays = earliest?.let {
            ChronoUnit.DAYS.between(it, Instant.now()).coerceAtLeast(0)
        } ?: 0L
        val memories = runCatching {
            memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
        }.getOrDefault(emptyList())
        return HomeUiState(
            assistant = assistant,
            recentConversation = recent,
            memoryCount = memories.size,
            recentMemoryPreview = memories.firstOrNull()?.content,
            interactionCount = conversations.size,
            companionDays = companionDays,
        )
    }
}
