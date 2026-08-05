package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowManager
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowRule
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowTargetAssistant
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowTimeMode
import kotlin.uuid.Uuid

const val SCHEDULED_WORKFLOW_TOOL = "scheduled_workflow_tool"

fun createScheduledWorkflowTools(
    scheduledWorkflowManager: ScheduledWorkflowManager,
): List<Tool> = listOf(
    Tool(
        name = SCHEDULED_WORKFLOW_TOOL,
        description = """
            Manage scheduled workflow rules for the app.
            Actions:
            - list_rules
            - get_rule
            - save_rule
            - delete_rule
            - enable_rule
            - disable_rule
            - reorder_assistant_priority
        """.trimIndent(),
        systemPrompt = { _, _ ->
            """
            **Scheduled Workflows**
            Use `$SCHEDULED_WORKFLOW_TOOL` to create or update time-based workflow triggers.
            A rule must point to one workflow, choose one or more assistants that already enabled that workflow,
            and define a schedule mode plus hour/minute.
            """.trimIndent()
        },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", schemaString("One of: list_rules, get_rule, save_rule, delete_rule, enable_rule, disable_rule, reorder_assistant_priority"))
                    put("id", schemaString("Rule UUID for get/delete/enable/disable/reorder"))
                    put("name", schemaString("Rule name for save_rule"))
                    put("description", schemaString("Optional description for save_rule"))
                    put("workflow_id", schemaString("Workflow UUID for save_rule"))
                    put("time_mode", schemaString("daily_at_time or weekdays_at_time"))
                    put("hour", schemaString("0-23 for save_rule"))
                    put("minute", schemaString("0-59 for save_rule"))
                    put("enabled", schemaString("Optional boolean for save_rule"))
                    put("assistant_ids", schemaString("Comma-separated assistant UUIDs for save_rule"))
                    put("assistant_priority", schemaString("Comma-separated assistant UUIDs in trigger order"))
                },
                required = listOf("action"),
            )
        },
        needsApproval = { args ->
            args.jsonObject["action"]?.jsonPrimitive?.contentOrNull in setOf(
                "save_rule",
                "delete_rule",
                "enable_rule",
                "disable_rule",
                "reorder_assistant_priority",
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
            val settings = scheduledWorkflowManager.listRules()
            val result = when (action) {
                "list_rules" -> {
                    buildJsonObject {
                        put("count", settings.size)
                        put("rules", buildJsonArray {
                            settings.forEach { rule ->
                                add(buildJsonObject {
                                    put("id", rule.id.toString())
                                    put("name", rule.name)
                                    put("workflow_id", rule.workflowId.toString())
                                    put("time_mode", rule.timeMode.name.lowercase())
                                    put("hour", rule.hour)
                                    put("minute", rule.minute)
                                    put("enabled", rule.enabled)
                                    put("assistant_priority", buildJsonArray {
                                        rule.assistantPriority.forEach { add(JsonPrimitive(it.toString())) }
                                    })
                                })
                            }
                        })
                    }.toString()
                }

                "get_rule" -> {
                    val id = parseUuid(obj["id"]?.jsonPrimitive?.contentOrNull) ?: error("id is required")
                    val rule = scheduledWorkflowManager.getRule(id) ?: error("Rule not found: $id")
                    encodeRule(rule)
                }

                "save_rule" -> {
                    val workflowId = parseUuid(obj["workflow_id"]?.jsonPrimitive?.contentOrNull)
                        ?: error("workflow_id is required")
                    val assistantIds = parseUuidList(obj["assistant_ids"]?.jsonPrimitive?.contentOrNull)
                    val priority = parseUuidList(obj["assistant_priority"]?.jsonPrimitive?.contentOrNull)
                    val existing = parseUuid(obj["id"]?.jsonPrimitive?.contentOrNull)
                        ?.let { scheduledWorkflowManager.getRule(it) }
                    val rule = (existing ?: ScheduledWorkflowRule(workflowId = workflowId)).copy(
                        name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        workflowId = workflowId,
                        targets = assistantIds.map { ScheduledWorkflowTargetAssistant(it, enabled = true) },
                        assistantPriority = priority.ifEmpty { assistantIds },
                        timeMode = when (obj["time_mode"]?.jsonPrimitive?.contentOrNull) {
                            "weekdays_at_time" -> ScheduledWorkflowTimeMode.WEEKDAYS_AT_TIME
                            else -> ScheduledWorkflowTimeMode.DAILY_AT_TIME
                        },
                        hour = obj["hour"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 9,
                        minute = obj["minute"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                        enabled = obj["enabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                    )
                    val saved = scheduledWorkflowManager.saveRule(rule)
                    encodeRule(saved)
                }

                "delete_rule" -> {
                    val id = parseUuid(obj["id"]?.jsonPrimitive?.contentOrNull) ?: error("id is required")
                    scheduledWorkflowManager.deleteRule(id)
                    buildJsonObject {
                        put("ok", true)
                        put("id", id.toString())
                    }.toString()
                }

                "enable_rule", "disable_rule" -> {
                    val id = parseUuid(obj["id"]?.jsonPrimitive?.contentOrNull) ?: error("id is required")
                    val rule = scheduledWorkflowManager.getRule(id) ?: error("Rule not found: $id")
                    val saved = scheduledWorkflowManager.saveRule(
                        rule.copy(enabled = action == "enable_rule")
                    )
                    encodeRule(saved)
                }

                "reorder_assistant_priority" -> {
                    val id = parseUuid(obj["id"]?.jsonPrimitive?.contentOrNull) ?: error("id is required")
                    val priority = parseUuidList(obj["assistant_priority"]?.jsonPrimitive?.contentOrNull)
                    val rule = scheduledWorkflowManager.getRule(id) ?: error("Rule not found: $id")
                    val saved = scheduledWorkflowManager.saveRule(rule.copy(assistantPriority = priority))
                    encodeRule(saved)
                }

                else -> error("Unknown action: $action")
            }
            listOf(UIMessagePart.Text(result))
        },
    )
)

private fun encodeRule(rule: ScheduledWorkflowRule): String = buildJsonObject {
    put("id", rule.id.toString())
    put("name", rule.name)
    put("description", rule.description)
    put("workflow_id", rule.workflowId.toString())
    put("time_mode", rule.timeMode.name.lowercase())
    put("hour", rule.hour)
    put("minute", rule.minute)
    put("enabled", rule.enabled)
    put("assistant_ids", buildJsonArray {
        rule.targets.forEach { add(JsonPrimitive(it.assistantId.toString())) }
    })
    put("assistant_priority", buildJsonArray {
        rule.assistantPriority.forEach { add(JsonPrimitive(it.toString())) }
    })
}.toString()

private fun schemaString(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun parseUuid(raw: String?): Uuid? =
    raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { Uuid.parse(it) }.getOrNull() }

private fun parseUuidList(raw: String?): List<Uuid> =
    raw.orEmpty()
        .split(",")
        .mapNotNull { parseUuid(it) }
