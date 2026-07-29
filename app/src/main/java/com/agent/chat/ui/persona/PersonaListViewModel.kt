package com.agent.chat.ui.persona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.memory.MemorySettingsStore
import com.agent.chat.data.persona.CharacterCardImporter
import com.agent.chat.data.persona.ParsedPersonaDraft
import com.agent.chat.data.persona.PersonaSmartImportException
import com.agent.chat.data.persona.PersonaSmartImporter
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.repository.PersonaRepository
import com.agent.chat.domain.model.LorebookEntry
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.OutputRegex
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.PresetMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonaListUiState(
    val personas: List<Persona> = emptyList(),
    val editingPersona: Persona? = null,
    val isEditorOpen: Boolean = false,
    val editorTab: PersonaEditorTab = PersonaEditorTab.BASIC,
    val editorName: String = "",
    val editorAvatar: String = "",
    val editorSystemPrompt: String = "",
    val editorTemperature: String = "0.7",
    val editorDescription: String = "",
    val editorOpeningLine: String = "",
    val editorPresets: List<PresetMessage> = emptyList(),
    val editorLorebook: List<LorebookEntry> = emptyList(),
    val editorRegexes: List<OutputRegex> = emptyList(),
    val statusMessage: String? = null,
    val memoryPersona: Persona? = null,
    val memories: List<Memory> = emptyList(),
    val extractThreshold: Int = MemorySettingsStore.DEFAULT_THRESHOLD,
    val showCreateChooser: Boolean = false,
    val showSmartImport: Boolean = false,
    val smartImportText: String = "",
    val isSmartImportParsing: Boolean = false,
)

@HiltViewModel
class PersonaListViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val memoryRepository: MemoryRepository,
    private val memorySettingsStore: MemorySettingsStore,
    private val personaSmartImporter: PersonaSmartImporter,
    private val characterCardImporter: CharacterCardImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonaListUiState())
    val uiState: StateFlow<PersonaListUiState> = _uiState.asStateFlow()

    private val _exportJson = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportJson: SharedFlow<String> = _exportJson.asSharedFlow()

    private var memoryObserveJob: Job? = null
    private var parseJob: Job? = null

    init {
        viewModelScope.launch {
            personaRepository.observePersonas().collect { personas ->
                _uiState.update { it.copy(personas = personas) }
            }
        }
        viewModelScope.launch {
            memorySettingsStore.snapshot.collect { settings ->
                _uiState.update { it.copy(extractThreshold = settings.extractThreshold) }
            }
        }
    }

    fun openCreateChooser() {
        _uiState.update {
            it.copy(
                showCreateChooser = true,
                showSmartImport = false,
                isEditorOpen = false,
            )
        }
    }

    fun dismissCreateChooser() {
        _uiState.update { it.copy(showCreateChooser = false) }
    }

    fun openCreateEditor() {
        _uiState.update {
            it.copy(
                showCreateChooser = false,
                showSmartImport = false,
                isEditorOpen = true,
                editingPersona = null,
                editorTab = PersonaEditorTab.BASIC,
                editorName = "",
                editorAvatar = "",
                editorSystemPrompt = "",
                editorTemperature = "0.7",
                editorDescription = "",
                editorOpeningLine = "",
                editorPresets = emptyList(),
                editorLorebook = emptyList(),
                editorRegexes = emptyList(),
                statusMessage = null,
            )
        }
    }

    fun openSmartImport() {
        parseJob?.cancel()
        _uiState.update {
            it.copy(
                showCreateChooser = false,
                showSmartImport = true,
                smartImportText = "",
                isSmartImportParsing = false,
                isEditorOpen = false,
            )
        }
    }

    fun dismissSmartImport() {
        if (_uiState.value.isSmartImportParsing) return
        parseJob?.cancel()
        _uiState.update {
            it.copy(
                showSmartImport = false,
                smartImportText = "",
                isSmartImportParsing = false,
            )
        }
    }

    fun onSmartImportTextChange(value: String) {
        _uiState.update { it.copy(smartImportText = value) }
    }

    fun parseSmartImport() {
        val text = _uiState.value.smartImportText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "请先粘贴人设描述文本") }
            return
        }
        if (_uiState.value.isSmartImportParsing) return

        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            _uiState.update { it.copy(isSmartImportParsing = true) }
            try {
                // 若粘贴的是角色卡 JSON，优先直接解析，不走 AI
                if (text.startsWith("{") &&
                    (text.contains("\"spec\"") || text.contains("\"first_mes\"") || text.contains("\"mes_example\""))
                ) {
                    val draft = characterCardImporter.importFromJsonText(text)
                    applyDraftToEditor(draft, fromCard = true)
                } else {
                    val draft = personaSmartImporter.parse(text)
                    applyDraftToEditor(draft, fromCard = false)
                }
            } catch (e: PersonaSmartImportException) {
                _uiState.update {
                    it.copy(
                        isSmartImportParsing = false,
                        statusMessage = e.message ?: "解析失败，请重试",
                    )
                }
            } catch (e: IllegalArgumentException) {
                _uiState.update {
                    it.copy(
                        isSmartImportParsing = false,
                        statusMessage = e.message ?: "请先粘贴人设描述文本",
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSmartImportParsing = false,
                        statusMessage = "没能识别出人设信息，你可以手动填写或换一段文本试试",
                    )
                }
            }
        }
    }

    fun importCharacterCard(bytes: ByteArray, mimeHint: String? = null) {
        viewModelScope.launch {
            try {
                val draft = characterCardImporter.import(bytes, mimeHint)
                applyDraftToEditor(draft, fromCard = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusMessage = "角色卡导入失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    private fun applyDraftToEditor(draft: ParsedPersonaDraft, fromCard: Boolean) {
        _uiState.update {
            it.copy(
                showCreateChooser = false,
                showSmartImport = false,
                isSmartImportParsing = false,
                smartImportText = "",
                isEditorOpen = true,
                editingPersona = null,
                editorTab = if (fromCard && draft.presetMessages.isNotEmpty()) {
                    PersonaEditorTab.PRESET
                } else {
                    PersonaEditorTab.BASIC
                },
                editorName = draft.name,
                editorAvatar = DEFAULT_AVATAR_PLACEHOLDER,
                editorSystemPrompt = draft.systemPrompt,
                editorTemperature = "0.85",
                editorDescription = draft.description,
                editorOpeningLine = draft.openingLine,
                editorPresets = draft.presetMessages,
                editorLorebook = draft.lorebookEntries,
                editorRegexes = emptyList(),
                statusMessage = if (fromCard) "已导入角色卡，请确认后保存" else "已解析，请确认后保存",
            )
        }
    }

    fun openEditEditor(persona: Persona) {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editingPersona = persona,
                editorTab = PersonaEditorTab.BASIC,
                editorName = persona.name,
                editorAvatar = persona.avatar,
                editorSystemPrompt = persona.systemPrompt,
                editorTemperature = persona.defaultTemperature.toString(),
                editorDescription = persona.description,
                editorOpeningLine = persona.openingLine,
                editorPresets = persona.presetMessages,
                editorLorebook = persona.lorebookEntries,
                editorRegexes = persona.outputRegexes,
                statusMessage = null,
                showCreateChooser = false,
                showSmartImport = false,
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                isEditorOpen = false,
                editingPersona = null,
                editorOpeningLine = "",
                editorPresets = emptyList(),
                editorLorebook = emptyList(),
                editorRegexes = emptyList(),
                editorTab = PersonaEditorTab.BASIC,
            )
        }
    }

    fun openMemoryManager(persona: Persona) {
        memoryObserveJob?.cancel()
        _uiState.update { it.copy(memoryPersona = persona, memories = emptyList()) }
        memoryObserveJob = viewModelScope.launch {
            memoryRepository.observeByPersona(persona.id).collect { memories ->
                _uiState.update { it.copy(memories = memories) }
            }
        }
    }

    fun dismissMemoryManager() {
        memoryObserveJob?.cancel()
        memoryObserveJob = null
        _uiState.update { it.copy(memoryPersona = null, memories = emptyList()) }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(memoryId)
            _uiState.update { it.copy(statusMessage = "已删除记忆") }
        }
    }

    fun onEditorTabChange(tab: PersonaEditorTab) = _uiState.update { it.copy(editorTab = tab) }
    fun onEditorNameChange(value: String) = _uiState.update { it.copy(editorName = value) }
    fun onEditorAvatarChange(value: String) = _uiState.update { it.copy(editorAvatar = value) }
    fun onEditorSystemPromptChange(value: String) =
        _uiState.update { it.copy(editorSystemPrompt = value) }

    fun onEditorTemperatureChange(value: String) =
        _uiState.update { it.copy(editorTemperature = value) }

    fun onEditorDescriptionChange(value: String) =
        _uiState.update { it.copy(editorDescription = value) }

    fun onEditorOpeningLineChange(value: String) =
        _uiState.update { it.copy(editorOpeningLine = value) }

    fun onEditorPresetsChange(value: List<PresetMessage>) =
        _uiState.update { it.copy(editorPresets = value) }

    fun onEditorLorebookChange(value: List<LorebookEntry>) =
        _uiState.update { it.copy(editorLorebook = value) }

    fun onEditorRegexesChange(value: List<OutputRegex>) =
        _uiState.update { it.copy(editorRegexes = value) }

    fun saveEditor() {
        val state = _uiState.value
        val name = state.editorName.trim()
        val prompt = state.editorSystemPrompt.trim()
        if (name.isEmpty() || prompt.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "名称和 System Prompt 不能为空") }
            return
        }
        val temperature = state.editorTemperature.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f
        val presets = state.editorPresets.mapNotNull { p ->
            val content = p.content.trim()
            if (content.isEmpty()) null else p.copy(content = content)
        }
        val lore = state.editorLorebook.mapNotNull { e ->
            val content = e.content.trim()
            if (content.isEmpty() || e.keys.isEmpty()) null
            else e.copy(content = content, keys = e.keys.map { it.trim() }.filter { it.isNotEmpty() })
        }
        val regexes = state.editorRegexes.mapNotNull { r ->
            val pattern = r.pattern.trim()
            if (pattern.isEmpty()) null
            else r.copy(pattern = pattern, replacement = r.replacement)
        }

        viewModelScope.launch {
            val editing = state.editingPersona
            if (editing == null) {
                personaRepository.createPersona(
                    name = name,
                    systemPrompt = prompt,
                    avatar = state.editorAvatar.ifBlank { DEFAULT_AVATAR_PLACEHOLDER },
                    defaultTemperature = temperature,
                    description = state.editorDescription,
                    openingLine = state.editorOpeningLine,
                    presetMessages = presets,
                    lorebookEntries = lore,
                    outputRegexes = regexes,
                )
            } else {
                personaRepository.updatePersona(
                    editing.copy(
                        name = name,
                        systemPrompt = prompt,
                        avatar = state.editorAvatar,
                        defaultTemperature = temperature,
                        description = state.editorDescription,
                        openingLine = state.editorOpeningLine,
                        presetMessages = presets,
                        lorebookEntries = lore,
                        outputRegexes = regexes,
                    ),
                )
            }
            _uiState.update {
                it.copy(
                    isEditorOpen = false,
                    editingPersona = null,
                    editorOpeningLine = "",
                    editorPresets = emptyList(),
                    editorLorebook = emptyList(),
                    editorRegexes = emptyList(),
                    statusMessage = "已保存",
                )
            }
        }
    }

    fun deletePersona(id: String) {
        viewModelScope.launch {
            personaRepository.deletePersona(id)
            _uiState.update { it.copy(statusMessage = "已删除") }
        }
    }

    fun exportPersonas() {
        viewModelScope.launch {
            try {
                val json = personaRepository.exportToJson()
                _exportJson.emit(json)
                _uiState.update { it.copy(statusMessage = "导出就绪") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "导出失败：${e.message}") }
            }
        }
    }

    fun importPersonas(json: String) {
        viewModelScope.launch {
            try {
                // 单张角色卡 JSON 也允许从「导入」入口进来
                val trimmed = json.trim()
                if (trimmed.startsWith("{") &&
                    !trimmed.contains("\"personas\"") &&
                    (trimmed.contains("\"spec\"") || trimmed.contains("\"first_mes\""))
                ) {
                    val draft = characterCardImporter.importFromJsonText(trimmed)
                    applyDraftToEditor(draft, fromCard = true)
                    return@launch
                }
                val count = personaRepository.importFromJson(json)
                _uiState.update { it.copy(statusMessage = "已导入 $count 个人设") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "导入失败：${e.message}") }
            }
        }
    }

    fun consumeStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun reportMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    companion object {
        const val DEFAULT_AVATAR_PLACEHOLDER = "🎭"
    }
}
