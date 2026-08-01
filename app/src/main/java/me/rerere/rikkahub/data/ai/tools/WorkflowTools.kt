package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workflow.WorkflowManager
import me.rerere.rikkahub.utils.JsonInstantPretty
import kotlin.uuid.Uuid

const val WORKFLOW_TOOL = "workflow_tool"

/**
 * Lets the model create/list/enable workflows in the app library automatically
 * (no manual import UI). Optional [workspaceId] allows `save` from a file under `/workspace`.
 */
fun createWorkflowTools(
    workflowManager: WorkflowManager,
    settingsStore: SettingsStore,
    assistantId: Uuid,
    workspaceRepository: WorkspaceRepository? = null,
    workspaceId: String? = null,
): List<Tool> {
    return listOf(
        Tool(
            name = WORKFLOW_TOOL,
            description = """
                Manage custom shareable workflows in the app library.
                Use this to CREATE or UPDATE a workflow from JSON and optionally enable it on the current assistant —
                the user does not need to import manually.
                Actions:
                - list: list workflows in the library
                - get: get one workflow JSON by id
                - save: import/save a workflow from `json` (preferred) or from `workspace_path` under /workspace
                - enable / disable: bind or unbind a workflow id on the CURRENT assistant
            """.trimIndent(),
            systemPrompt = { _, _ ->
                buildString {
                    appendLine("**Workflows**")
                    appendLine(
                        "You can create workflows for this user with `$WORKFLOW_TOOL` action=`save`. " +
                            "Pass a full workflow JSON in `json` (schemaVersion 1, trigger, steps: inject_prompt/hint_tools/run_skill). " +
                            "Set `enable_on_assistant`=true (default) so it activates on this assistant immediately. " +
                            "Set `overwrite_same_id`=true to replace an existing id. " +
                            "If you drafted a file in the workspace, you may pass `workspace_path` like `/workspace/my.workflow.json` instead of `json`."
                    )
                    appendLine()
                }
            },
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put(
                            "action",
                            buildJsonObject {
                                put("type", "string")
                                put(
                                    "description",
                                    "One of: list, get, save, enable, disable"
                                )
                            },
                        )
                        put(
                            "json",
                            buildJsonObject {
                                put("type", "string")
                                put(
                                    "description",
                                    "Full workflow JSON text for action=save"
                                )
                            },
                        )
                        put(
                            "workspace_path",
                            buildJsonObject {
                                put("type", "string")
                                put(
                                    "description",
                                    "Optional Rootfs path under /workspace containing workflow JSON (action=save)"
                                )
                            },
                        )
                        put(
                            "id",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "Workflow UUID for get/enable/disable")
                            },
                        )
                        put(
                            "overwrite_same_id",
                            buildJsonObject {
                                put("type", "boolean")
                                put(
                                    "description",
                                    "When saving, overwrite if id exists (default true)"
                                )
                            },
                        )
                        put(
                            "force_new_id",
                            buildJsonObject {
                                put("type", "boolean")
                                put(
                                    "description",
                                    "When saving, always assign a new UUID (default false)"
                                )
                            },
                        )
                        put(
                            "enable_on_assistant",
                            buildJsonObject {
                                put("type", "boolean")
                                put(
                                    "description",
                                    "After save, enable this workflow on the current assistant (default true)"
                                )
                            },
                        )
                    },
                    required = listOf("action"),
                )
            },
            needsApproval = { args ->
                val action = args.jsonObject["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
                action == "save" || action == "enable" || action == "disable"
            },
            execute = { args ->
                val obj = args.jsonObject
                val action = obj["action"]?.jsonPrimitive?.contentOrNull
                    ?: error("action is required")
                val result = when (action) {
                    "list" -> {
                        val list = workflowManager.listWorkflows()
                        buildJsonObject {
                            put("count", list.size)
                            put(
                                "workflows",
                                buildJsonArray {
                                    list.forEach { wf ->
                                        add(
                                            buildJsonObject {
                                                put("id", wf.id.toString())
                                                put("name", wf.name)
                                                put("description", wf.description)
                                                put("steps", wf.steps.size)
                                                put("match", wf.trigger.match.name)
                                            }
                                        )
                                    }
                                }
                            )
                        }.toString()
                    }

                    "get" -> {
                        val id = parseUuid(obj["id"]?.jsonPrimitive?.contentOrNull)
                            ?: error("id is required")
                        val wf = workflowManager.getWorkflow(id)
                            ?: error("Workflow not found: $id")
                        JsonInstantPretty.encodeToString(
                            me.rerere.rikkahub.data.workflow.WorkflowDefinition.serializer(),
                            wf,
                        )
                    }

                    "save" -> {
                        val jsonText = resolveWorkflowJsonText(
                            json = obj["json"]?.jsonPrimitive?.contentOrNull,
                            workspacePath = obj["workspace_path"]?.jsonPrimitive?.contentOrNull,
                            workspaceRepository = workspaceRepository,
                            workspaceId = workspaceId,
                        )
                        val overwrite = obj["overwrite_same_id"]?.jsonPrimitive?.contentOrNull
                            ?.toBooleanStrictOrNull() ?: true
                        val forceNew = obj["force_new_id"]?.jsonPrimitive?.contentOrNull
                            ?.toBooleanStrictOrNull() ?: false
                        val enable = obj["enable_on_assistant"]?.jsonPrimitive?.contentOrNull
                            ?.toBooleanStrictOrNull() ?: true

                        val saved = workflowManager.importWorkflowJson(
                            jsonText = jsonText,
                            overwriteSameId = overwrite,
                            forceNewId = forceNew,
                        )
                        if (enable) {
                            setWorkflowEnabled(settingsStore, assistantId, saved.id, enabled = true)
                        }
                        buildJsonObject {
                            put("ok", true)
                            put("id", saved.id.toString())
                            put("name", saved.name)
                            put("enabled_on_assistant", enable)
                            put(
                                "message",
                                if (enable) {
                                    "Workflow saved to library and enabled on current assistant"
                                } else {
                                    "Workflow saved to library (not enabled on assistant)"
                                }
                            )
                        }.toString()
                    }

                    "enable", "disable" -> {
                        val id = parseUuid(obj["id"]?.jsonPrimitive?.contentOrNull)
                            ?: error("id is required")
                        require(workflowManager.getWorkflow(id) != null) {
                            "Workflow not found: $id"
                        }
                        val enabled = action == "enable"
                        setWorkflowEnabled(settingsStore, assistantId, id, enabled)
                        buildJsonObject {
                            put("ok", true)
                            put("id", id.toString())
                            put("enabled_on_assistant", enabled)
                        }.toString()
                    }

                    else -> error("Unknown action: $action (use list|get|save|enable|disable)")
                }
                listOf(UIMessagePart.Text(result))
            },
        )
    )
}

private fun parseUuid(raw: String?): Uuid? =
    raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { Uuid.parse(it) }.getOrNull() }

private suspend fun resolveWorkflowJsonText(
    json: String?,
    workspacePath: String?,
    workspaceRepository: WorkspaceRepository?,
    workspaceId: String?,
): String {
    val direct = json?.trim().orEmpty()
    if (direct.isNotEmpty()) return direct

    val path = workspacePath?.trim().orEmpty()
    require(path.isNotEmpty()) {
        "Provide `json` (workflow JSON text) or `workspace_path` (file under /workspace)"
    }
    require(!workspaceId.isNullOrBlank() && workspaceRepository != null) {
        "This assistant has no workspace bound; pass `json` instead of `workspace_path`"
    }
    val relative = path
        .removePrefix("/workspace/")
        .removePrefix("/workspace")
        .removePrefix("/")
    require(relative.isNotEmpty()) { "Invalid workspace_path: $path" }
    return workspaceRepository.readText(workspaceId, relative)
}

private suspend fun setWorkflowEnabled(
    settingsStore: SettingsStore,
    assistantId: Uuid,
    workflowId: Uuid,
    enabled: Boolean,
) {
    settingsStore.update { settings ->
        settings.copy(
            assistants = settings.assistants.map { assistant ->
                if (assistant.id != assistantId) return@map assistant
                val ids = if (enabled) {
                    assistant.enabledWorkflowIds + workflowId
                } else {
                    assistant.enabledWorkflowIds - workflowId
                }
                assistant.copy(enabledWorkflowIds = ids)
            }
        )
    }
}
