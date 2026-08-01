package me.rerere.rikkahub.data.workflow

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import kotlin.uuid.Uuid

fun validateWorkflowSchema(definition: WorkflowDefinition) {
    require(definition.schemaVersion in 1..WORKFLOW_SCHEMA_VERSION) {
        "Unsupported workflow schemaVersion=${definition.schemaVersion}"
    }
    definition.steps.forEach { step ->
        when (step) {
            is WorkflowStep.InjectPrompt ->
                require(step.content.isNotBlank()) { "inject_prompt step requires content" }
            is WorkflowStep.HintTools ->
                require(step.toolNames.isNotEmpty()) { "hint_tools step requires toolNames" }
            is WorkflowStep.RunSkill ->
                require(step.skillName.isNotBlank()) { "run_skill step requires skillName" }
        }
    }
    if (definition.trigger.match != WorkflowMatchType.Always) {
        require(definition.trigger.patterns.any { it.isNotBlank() }) {
            "keyword/regex trigger requires patterns"
        }
    }
}

class WorkflowManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val TAG = "WorkflowManager"
    }

    fun getWorkflowsDir(): File {
        val dir = context.filesDir.resolve(FileFolders.WORKFLOWS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listWorkflows(): List<WorkflowDefinition> {
        val dir = getWorkflowsDir()
        return dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(WORKFLOW_FILE_SUFFIX) || it.name.endsWith(".json")) }
            ?.mapNotNull { file ->
                runCatching { readWorkflowFile(file) }
                    .onFailure { Log.w(TAG, "Failed to parse ${file.name}", it) }
                    .getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun getWorkflow(id: Uuid): WorkflowDefinition? {
        val preferred = File(getWorkflowsDir(), "${id}$WORKFLOW_FILE_SUFFIX")
        if (preferred.exists()) return readWorkflowFile(preferred)
        return listWorkflows().firstOrNull { it.id == id }
    }

    fun saveWorkflow(definition: WorkflowDefinition): WorkflowDefinition {
        require(definition.name.isNotBlank()) { "Workflow name is required" }
        validateWorkflowSchema(definition)
        val normalized = definition.copy(schemaVersion = WORKFLOW_SCHEMA_VERSION)
        val file = File(getWorkflowsDir(), "${normalized.id}$WORKFLOW_FILE_SUFFIX")
        // Remove any legacy/alternate filename for same id
        getWorkflowsDir().listFiles()
            ?.filter { it.isFile && it != file && it.nameWithoutExtension.startsWith(normalized.id.toString()) }
            ?.forEach { it.delete() }
        file.writeText(JsonInstant.encodeToString(WorkflowDefinition.serializer(), normalized))
        return normalized
    }

    fun importWorkflowJson(
        jsonText: String,
        overwriteSameId: Boolean = false,
        forceNewId: Boolean = false,
    ): WorkflowDefinition {
        val parsed = JsonInstant.decodeFromString(WorkflowDefinition.serializer(), jsonText)
        validateWorkflowSchema(parsed)
        var def = parsed.copy(schemaVersion = WORKFLOW_SCHEMA_VERSION)
        val existing = getWorkflow(def.id)
        when {
            forceNewId -> def = def.copy(id = Uuid.random())
            existing != null && !overwriteSameId -> def = def.copy(id = Uuid.random())
        }
        require(def.name.isNotBlank()) { "Workflow name is required" }
        return saveWorkflow(def)
    }

    fun exportWorkflowJson(id: Uuid): String {
        val def = getWorkflow(id) ?: error("Workflow not found: $id")
        return JsonInstant.encodeToString(WorkflowDefinition.serializer(), def)
    }

    suspend fun deleteWorkflow(id: Uuid): Boolean = withContext(Dispatchers.IO) {
        val file = File(getWorkflowsDir(), "${id}$WORKFLOW_FILE_SUFFIX")
        val deleted = if (file.exists()) file.delete() else {
            listWorkflows().any { it.id == id && File(getWorkflowsDir(), "${it.id}$WORKFLOW_FILE_SUFFIX").delete() }
        }
        if (deleted) {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.enabledWorkflowIds.contains(id)) {
                            assistant.copy(enabledWorkflowIds = assistant.enabledWorkflowIds - id)
                        } else {
                            assistant
                        }
                    }
                )
            }
        }
        deleted
    }

    suspend fun pruneOrphanedEnabledWorkflows(): List<WorkflowDefinition> = withContext(Dispatchers.IO) {
        val workflows = listWorkflows()
        val existing = workflows.mapTo(HashSet()) { it.id }
        settingsStore.update { settings ->
            var changed = false
            val newAssistants = settings.assistants.map { assistant ->
                val pruned = assistant.enabledWorkflowIds.filterTo(LinkedHashSet()) { it in existing }
                if (pruned.size != assistant.enabledWorkflowIds.size) {
                    changed = true
                    assistant.copy(enabledWorkflowIds = pruned)
                } else {
                    assistant
                }
            }
            if (changed) settings.copy(assistants = newAssistants) else settings
        }
        workflows
    }

    fun validateSchema(definition: WorkflowDefinition) = validateWorkflowSchema(definition)

    private fun readWorkflowFile(file: File): WorkflowDefinition {
        val def = JsonInstant.decodeFromString(WorkflowDefinition.serializer(), file.readText())
        validateWorkflowSchema(def)
        return def
    }
}
