package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.MemorySearchBundle
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate

const val MEMORY_SEARCH_TOOL = "memory_search"

fun buildMemoryTools(
    json: Json,
    onCreation: suspend (String) -> AssistantMemory,
    onUpdate: suspend (Int, String) -> AssistantMemory,
    onDelete: suspend (Int) -> Unit,
    onSearch: suspend (query: String, includeSuperseded: Boolean) -> MemorySearchBundle,
): List<Tool> = listOf(
    Tool(
        name = "memory_tool",
        description = """
            The memory tool stores long-term information across conversations.
            Use `action` to control the operation: `create` (add), `edit` (update), `delete` (remove).
            - No relevant record: `create` + `content`
            - Existing relevant record: `edit` + `id` + `content`
            - Outdated/irrelevant record: `delete` + `id`
            Profile-like facts (name, birthday, likes/dislikes, reply style, addressing, locale) are upserted by topic:
            creating the same topic updates the current HEAD instead of duplicating.
            Later turns inject a budgeted **Memories** block (profile HEAD + episode index). Full episode text requires `$MEMORY_SEARCH_TOOL`.
            Prefer storing durable preferences the user clearly wants remembered.
            Avoid storing highly sensitive PII unless the user explicitly asks you to remember it.
            Do not store one-off transient details that only matter in the current turn.
            Do not show memory content directly in the conversation unless the user explicitly asks.
            Today is ${LocalDate.now().toLocalString(true)}.
            Similar memories should be merged; prefer updating existing records. If the user corrects a fact, update it.

            Examples:
            {"action":"create","content":"User prefers brief replies and is more active on weekends."}
            {"action":"edit","id":12,"content":"User’s preferred name updated to “A-Xing”, prefers Chinese replies."}
            {"action":"delete","id":7}
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            buildJsonArray {
                                add("create")
                                add("edit")
                                add("delete")
                            }
                        )
                        put("description", "Operation to perform: create, edit, or delete")
                    })
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "The id of the memory record (required for edit/delete)")
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "The content of the memory record (required for create/edit)")
                    })
                },
                required = listOf("action")
            )
        },
        execute = {
            val params = it.jsonObject
            val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
            val payload = when (action) {
                "create" -> {
                    val content = params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
                    val created = onCreation(content)
                    buildJsonObject {
                        put("success", created.status != "rejected")
                        put("memory", json.encodeToJsonElement(AssistantMemory.serializer(), created))
                        if (created.status == "rejected") {
                            put("reason", "canonicalize_failed")
                        }
                    }
                }

                "edit" -> {
                    val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                    val content = params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
                    val updated = onUpdate(id, content)
                    buildJsonObject {
                        put("success", updated.status != "rejected")
                        put("memory", json.encodeToJsonElement(AssistantMemory.serializer(), updated))
                        if (updated.status == "rejected") {
                            put("reason", "canonicalize_failed")
                        }
                    }
                }

                "delete" -> {
                    val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                    onDelete(id)
                    buildJsonObject {
                        put("success", true)
                        put("id", id)
                    }
                }

                else -> error("unknown action: $action, must be one of [create, edit, delete]")
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),
    Tool(
        name = MEMORY_SEARCH_TOOL,
        description = """
            Search durable Assistant memories (profile + episode notes) by keyword.
            Use when **Memories** only shows an episode index preview, or when a detail is missing from the injected profile / turn hints.
            This is the ONLY tool for durable memories (do not invent alternatives).
            NOT for raw chat transcripts — use `recall_chat_history` for THIS chat's messages,
            and `conversation_search` / `recent_chats` for OTHER chats.
            Returns at most 3 pre-ranked hits (prefer the first) plus an optional short relation_summary
            (1-hop entity neighborhood, e.g. "海边 —关联→ 阿明, 旅行"). Prefer the first hit;
            do not invent edges that are not in relation_summary.
            Set include_superseded=true only when the user asks what used to be true
            (superseded hits are marked note="曾为").
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Keyword(s) to match against memory content / topic")
                    })
                    put("include_superseded", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Include outdated superseded versions (default false)")
                    })
                },
                required = listOf("query")
            )
        },
        execute = {
            val params = it.jsonObject
            val query = params["query"]?.jsonPrimitive?.contentOrNull ?: error("query is required")
            val includeSuperseded = params["include_superseded"]?.jsonPrimitive?.booleanOrNull ?: false
            val bundle = onSearch(query, includeSuperseded)
            val hits = bundle.memories
            val payload = buildJsonObject {
                put("count", hits.size)
                bundle.relationSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                    put("relation_summary", summary)
                }
                put(
                    "results",
                    buildJsonArray {
                        hits.forEach { memory ->
                            add(
                                buildJsonObject {
                                    put("id", memory.id)
                                    put("layer", memory.layer)
                                    put("status", memory.status)
                                    memory.topicKey?.let { topic -> put("topic", topic) }
                                    put("updatedAt", memory.updatedAt)
                                    if (memory.status == "superseded") {
                                        put("note", "曾为")
                                    }
                                    put("content", memory.content)
                                }
                            )
                        }
                    }
                )
            }
            listOf(UIMessagePart.Text(json.encodeToJsonElement(payload).toString()))
        }
    ),
)
