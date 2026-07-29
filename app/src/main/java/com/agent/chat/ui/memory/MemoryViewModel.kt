package com.agent.chat.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.MemoryCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserPortraitUi(
    val name: String = "",
    val interest: String = "",
    val occupation: String = "",
    val goal: String = "",
)

data class MemoryUiState(
    val portrait: UserPortraitUi = UserPortraitUi(),
    val selectedCategory: MemoryCategory = MemoryCategory.CORE,
    val memories: List<Memory> = emptyList(),
    val expandedId: String? = null,
    val editingMemory: Memory? = null,
    val editingPortrait: Boolean = false,
    val entranceReady: Boolean = false,
) {
    val filtered: List<Memory>
        get() = memories.filter { it.category == selectedCategory }
            .sortedByDescending { it.createdAt }

    fun countOf(category: MemoryCategory): Int =
        memories.count { it.category == category }
}

private data class MemoryUiExtras(
    val selectedCategory: MemoryCategory,
    val expandedId: String?,
    val editingMemory: Memory?,
    val editingPortrait: Boolean,
    val entranceReady: Boolean,
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val chatSettingsStore: ChatSettingsStore,
) : ViewModel() {

    private val extras = MutableStateFlow(
        MemoryUiExtras(
            selectedCategory = MemoryCategory.CORE,
            expandedId = null,
            editingMemory = null,
            editingPortrait = false,
            entranceReady = false,
        ),
    )

    val uiState: StateFlow<MemoryUiState> = combine(
        memoryRepository.observeAll(),
        chatSettingsStore.snapshot,
        extras,
    ) { memories, settings, ex ->
        MemoryUiState(
            portrait = UserPortraitUi(
                name = settings.userNickname,
                interest = settings.userInterest,
                occupation = settings.userOccupation,
                goal = settings.userGoal,
            ),
            selectedCategory = ex.selectedCategory,
            memories = memories,
            expandedId = ex.expandedId,
            editingMemory = ex.editingMemory,
            editingPortrait = ex.editingPortrait,
            entranceReady = ex.entranceReady,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoryUiState(),
    )

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            delay(40)
            extras.update { it.copy(entranceReady = true) }
        }
        viewModelScope.launch {
            memoryRepository.observeAll().collect { list ->
                val current = extras.value.selectedCategory
                if (list.isNotEmpty() && list.none { it.category == current }) {
                    val firstNonEmpty = MemoryCategory.entries.firstOrNull { cat ->
                        list.any { it.category == cat }
                    } ?: return@collect
                    extras.update { it.copy(selectedCategory = firstNonEmpty) }
                }
            }
        }
    }

    fun selectCategory(category: MemoryCategory) {
        extras.update { it.copy(selectedCategory = category, expandedId = null) }
    }

    fun toggleExpand(memoryId: String) {
        extras.update { ex ->
            ex.copy(expandedId = if (ex.expandedId == memoryId) null else memoryId)
        }
    }

    fun openEdit(memory: Memory) {
        extras.update { it.copy(editingMemory = memory) }
    }

    fun dismissEdit() {
        extras.update { it.copy(editingMemory = null) }
    }

    fun saveEdit(content: String, category: MemoryCategory) {
        val target = extras.value.editingMemory ?: return
        viewModelScope.launch {
            memoryRepository.updateMemory(
                target.copy(
                    content = content.trim(),
                    category = category,
                    importance = when (category) {
                        MemoryCategory.EMOTION -> maxOf(target.importance, 8)
                        MemoryCategory.CORE -> maxOf(target.importance, 7)
                        MemoryCategory.PREFERENCE -> target.importance.coerceIn(5, 8)
                        MemoryCategory.EVENT -> target.importance.coerceAtMost(7)
                    },
                ),
            )
            extras.update { it.copy(editingMemory = null) }
            _events.emit("记忆已更新")
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
            extras.update { ex ->
                ex.copy(expandedId = if (ex.expandedId == id) null else ex.expandedId)
            }
            _events.emit("已删除")
        }
    }

    fun toggleBlocked(memory: Memory) {
        viewModelScope.launch {
            val next = !memory.blockedFromAi
            memoryRepository.setBlockedFromAi(memory.id, next)
            _events.emit(if (next) "已禁止 AI 使用这条记忆" else "已允许 AI 使用")
        }
    }

    fun openPortraitEditor() {
        extras.update { it.copy(editingPortrait = true) }
    }

    fun dismissPortraitEditor() {
        extras.update { it.copy(editingPortrait = false) }
    }

    fun savePortrait(name: String, interest: String, occupation: String, goal: String) {
        chatSettingsStore.setUserNickname(name)
        chatSettingsStore.setUserInterest(interest)
        chatSettingsStore.setUserOccupation(occupation)
        chatSettingsStore.setUserGoal(goal)
        extras.update { it.copy(editingPortrait = false) }
        viewModelScope.launch { _events.emit("画像已更新") }
    }
}
