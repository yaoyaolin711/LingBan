package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.contextWindowStartIndex
import me.rerere.rikkahub.utils.JsonInstantPretty

/** Tool name — must stay unique vs conversation_search / recent_chats / memory_tool. */
const val RECALL_CHAT_HISTORY_TOOL = "recall_chat_history"

data class RecallChatHit(
    val index: Int,
    val role: String,
    val text: String,
    val outsideContextWindow: Boolean,
)

/**
 * Keyword search over the current conversation's full message list (including text
 * trimmed from the model payload by [me.rerere.ai.ui.limitContext]).
 */
fun searchCurrentChatHistory(
    messages: List<UIMessage>,
    contextMessageLimit: Int,
    query: String,
    limit: Int,
): List<RecallChatHit> {
    val tokens = query.trim()
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.length >= 2 }
        .distinct()
        .ifEmpty {
            listOf(query.trim()).filter { it.isNotEmpty() }
        }
    if (tokens.isEmpty()) return emptyList()

    val windowStart = messages.contextWindowStartIndex(contextMessageLimit)
    val scored = messages.mapIndexedNotNull { index, message ->
        val text = message.toText().trim()
        if (text.isBlank()) return@mapIndexedNotNull null
        val lower = text.lowercase()
        val score = tokens.sumOf { token ->
            val t = token.lowercase()
            when {
                lower.contains(t) -> 2
                else -> 0
            }
        }
        if (score <= 0) return@mapIndexedNotNull null
        // Prefer hits outside the active context window (what the model cannot see).
        val outside = index < windowStart
        val boosted = score + if (outside) 1 else 0
        boosted to RecallChatHit(
            index = index,
            role = message.role.name.lowercase(),
            text = text.take(1200),
            outsideContextWindow = outside,
        )
    }.sortedWith(
        compareByDescending<Pair<Int, RecallChatHit>> { it.first }
            .thenByDescending { it.second.outsideContextWindow }
            .thenBy { it.second.index }
    )

    return scored.take(limit.coerceIn(1, 30)).map { it.second }
}

/**
 * Read a contiguous range of original messages from the current conversation.
 */
fun readCurrentChatRange(
    messages: List<UIMessage>,
    contextMessageLimit: Int,
    startIndex: Int,
    endIndexInclusive: Int,
    maxMessages: Int = 20,
): List<RecallChatHit> {
    if (messages.isEmpty()) return emptyList()
    val windowStart = messages.contextWindowStartIndex(contextMessageLimit)
    val from = startIndex.coerceIn(0, messages.lastIndex)
    val to = endIndexInclusive.coerceIn(from, messages.lastIndex)
    return messages.subList(from, (to + 1).coerceAtMost(from + maxMessages))
        .mapIndexed { offset, message ->
            val index = from + offset
            RecallChatHit(
                index = index,
                role = message.role.name.lowercase(),
                text = message.toText().trim().take(1200),
                outsideContextWindow = index < windowStart,
            )
        }
        .filter { it.text.isNotBlank() }
}

fun buildRecallChatHistoryTool(
    getMessages: () -> List<UIMessage>,
    contextMessageLimit: Int,
): Tool = Tool(
    name = RECALL_CHAT_HISTORY_TOOL,
    description = """
        Recall original message details from the CURRENT conversation after context trimming.
        Early messages may only appear as a rolling summary in the system prompt; use this tool
        when the user asks for specifics (numbers, names, quotes, prior decisions) that may live
        in those trimmed messages.

        Prefer keyword `query` search. Optionally use `start_index` + `end_index` to read a known range.
        Do NOT use this for other conversations — use `conversation_search` / `recent_chats` instead
        (only when those tools are available). Do NOT use `memory_tool` for in-chat detail recall.
        Today you should cite returned `index` values if you need a follow-up range read.
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Keywords or a short phrase to find in this conversation's original messages"
                    )
                })
                put("start_index", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional inclusive start message index (0-based) for range read")
                })
                put("end_index", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional inclusive end message index for range read")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max search hits to return (default 8, max 30)")
                })
            }
        )
    },
    execute = {
        val params = it.jsonObject
        val messages = getMessages()
        val query = params["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val startIndex = params["start_index"]?.jsonPrimitive?.intOrNull
        val endIndex = params["end_index"]?.jsonPrimitive?.intOrNull
        val limit = (params["limit"]?.jsonPrimitive?.intOrNull ?: 8).coerceIn(1, 30)

        val hits = when {
            startIndex != null && endIndex != null -> readCurrentChatRange(
                messages = messages,
                contextMessageLimit = contextMessageLimit,
                startIndex = startIndex,
                endIndexInclusive = endIndex,
            )
            query.isNotBlank() -> searchCurrentChatHistory(
                messages = messages,
                contextMessageLimit = contextMessageLimit,
                query = query,
                limit = limit,
            )
            else -> error("Provide query and/or start_index+end_index")
        }

        val payload = buildJsonObject {
            put("conversation_scope", "current")
            put("context_message_limit", contextMessageLimit)
            put("window_start_index", messages.contextWindowStartIndex(contextMessageLimit))
            put("hit_count", hits.size)
            put(
                "hits",
                buildJsonArray {
                    hits.forEach { hit ->
                        add(buildJsonObject {
                            put("index", hit.index)
                            put("role", hit.role)
                            put("outside_context_window", hit.outsideContextWindow)
                            put("text", hit.text)
                        })
                    }
                }
            )
        }
        listOf(UIMessagePart.Text(JsonInstantPretty.encodeToString(payload)))
    }
)
