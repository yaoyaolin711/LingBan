package me.rerere.rikkahub.ui.pages.extensions.workflows

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.workflow.WorkflowDefinition
import me.rerere.rikkahub.data.workflow.WorkflowManager
import kotlin.uuid.Uuid

class WorkflowsVM(
    private val workflowManager: WorkflowManager,
) : ViewModel() {
    private val _workflows = MutableStateFlow<List<WorkflowDefinition>>(emptyList())
    val workflows = _workflows.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            workflowManager.pruneOrphanedEnabledWorkflows()
            _workflows.value = workflowManager.listWorkflows()
        }
    }

    fun save(definition: WorkflowDefinition, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                workflowManager.saveWorkflow(definition)
                _workflows.value = workflowManager.listWorkflows()
                withContext(Dispatchers.Main) { onResult(true, definition.name) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "保存失败") }
            }
        }
    }

    fun delete(id: Uuid) {
        viewModelScope.launch(Dispatchers.IO) {
            workflowManager.deleteWorkflow(id)
            _workflows.value = workflowManager.listWorkflows()
        }
    }

    fun exportJson(id: Uuid): String? = runCatching { workflowManager.exportWorkflowJson(id) }.getOrNull()

    fun importFromJsonText(
        jsonText: String,
        overwriteSameId: Boolean,
        forceNewId: Boolean,
        onResult: (Boolean, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val imported = workflowManager.importWorkflowJson(
                    jsonText = jsonText.trim(),
                    overwriteSameId = overwriteSameId,
                    forceNewId = forceNewId,
                )
                _workflows.value = workflowManager.listWorkflows()
                withContext(Dispatchers.Main) {
                    onResult(true, imported.name.ifBlank { imported.id.toString() })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "导入失败") }
            }
        }
    }

    fun importFromFile(
        context: Context,
        uri: Uri,
        overwriteSameId: Boolean,
        forceNewId: Boolean,
        onResult: (Boolean, String) -> Unit,
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: run {
                    withContext(Dispatchers.Main) { onResult(false, "无法读取文件") }
                    return@launch
                }
                val nameHint = FileUtils.getFileNameFromUri(appContext, uri).orEmpty()
                val imported = workflowManager.importWorkflowJson(
                    jsonText = text,
                    overwriteSameId = overwriteSameId,
                    forceNewId = forceNewId,
                )
                _workflows.value = workflowManager.listWorkflows()
                withContext(Dispatchers.Main) {
                    onResult(true, imported.name.ifBlank { nameHint })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "导入失败") }
            }
        }
    }

    fun get(id: Uuid): WorkflowDefinition? = workflowManager.getWorkflow(id)
}
