package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowManager
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowRule
import me.rerere.rikkahub.data.workflow.WorkflowDefinition
import me.rerere.rikkahub.data.workflow.WorkflowManager
import kotlin.uuid.Uuid

class ScheduledWorkflowVM(
    private val settingsStore: SettingsStore,
    private val workflowManager: WorkflowManager,
    private val scheduledWorkflowManager: ScheduledWorkflowManager,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, Settings.dummy())

    private val _workflows = MutableStateFlow<List<WorkflowDefinition>>(emptyList())
    val workflows = _workflows.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            scheduledWorkflowManager.pruneInvalidRules()
            _workflows.value = workflowManager.listWorkflows()
        }
    }

    fun saveRule(
        rule: ScheduledWorkflowRule,
        onResult: (Boolean, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { scheduledWorkflowManager.saveRule(rule) }
                .onSuccess { onResult(true, it.name) }
                .onFailure { onResult(false, it.message ?: "保存失败") }
        }
    }

    fun deleteRule(id: Uuid) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduledWorkflowManager.deleteRule(id)
        }
    }
}
