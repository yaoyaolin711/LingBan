package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.ai.tools.RECALL_CHAT_HISTORY_TOOL
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.utils.JsonInstantPretty

/**
 * Runtime-assembled continuity / memory policy (not the user-editable system prompt).
 * Soft guidance only — never hard-require tool calls on every turn.
 */
internal fun buildWorkflowToolHintsPrompt(hintedToolNames: List<String>): String {
    val names = hintedToolNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (names.isEmpty()) return ""
    return buildString {
        appendLine()
        append("**Workflow tool hints**")
        appendLine()
        appendLine(
            "A matched custom workflow suggests preferring these tools when relevant to the user's request " +
                "(soft hint only — do not call them unless useful): " +
                names.joinToString(", ") { "`$it`" } + "."
        )
    }
}

internal fun buildRuntimeContinuityPolicy(
    hasRollingSummary: Boolean,
    hasRecallTool: Boolean,
    hasMemoryTool: Boolean,
    hasCrossConversationSearch: Boolean,
): String {
    if (!hasRollingSummary && !hasRecallTool && !hasMemoryTool && !hasCrossConversationSearch) {
        return ""
    }

    val lookupSteps = buildList {
        add("recent messages in this turn")
        if (hasRollingSummary) add("**Conversation context summary**")
        if (hasMemoryTool) add("**Memories** already listed below")
        if (hasRecallTool) add("`$RECALL_CHAT_HISTORY_TOOL` for THIS chat's original messages")
        if (hasCrossConversationSearch) add("`conversation_search` / `recent_chats` for OTHER past chats")
    }

    return buildString {
        appendLine()
        append("**Runtime context policy**")
        appendLine()
        appendLine(
            "This block is injected by the app. Keep continuity natural: look things up before answering about " +
                "past details; do not invent facts."
        )
        if (hasRollingSummary) {
            appendLine(
                "- Earlier turns may be compressed into **Conversation context summary**. Treat that summary as true " +
                    "conversation history for high-level continuity."
            )
        }
        if (hasRecallTool || hasMemoryTool || hasCrossConversationSearch) {
            appendLine("- When the user asks about something you should know from prior context, but it is not clearly in recent messages" +
                (if (hasRollingSummary) " or the summary" else "") +
                (if (hasMemoryTool) " or **Memories**" else "") +
                ", look it up with the appropriate tool **before** answering:")
            if (hasRecallTool) {
                appendLine(
                    "  - THIS conversation's specifics (numbers, names, quotes, decisions): `$RECALL_CHAT_HISTORY_TOOL`. " +
                        "Skip if the answer is already obvious from recent messages."
                )
            }
            if (hasMemoryTool) {
                appendLine(
                    "  - Durable cross-conversation preferences/identity: use listed **Memories**; " +
                        "only call `memory_tool` to create/edit/delete durable facts the user clearly wants remembered " +
                        "(not transient one-off chat). Prefer `edit` over duplicate `create`."
                )
            }
            if (hasCrossConversationSearch) {
                appendLine(
                    "  - OTHER past chats: `conversation_search` / `recent_chats` " +
                        "(never use these for details that belong in THIS thread's `$RECALL_CHAT_HISTORY_TOOL`)."
                )
            }
            appendLine(
                "- Only after you have checked what is available (${lookupSteps.joinToString("; ")}) " +
                    "and still found nothing matching what the user described, say you don't have that recorded — " +
                    "politely and briefly, without sounding like a system error. Never fabricate a substitute."
            )
        } else if (hasRollingSummary) {
            appendLine(
                "- If a detail is missing from recent messages and the summary, do not invent it; " +
                    "say you don't have that recorded."
            )
        }
    }
}

internal fun buildMemoryPrompt(memories: List<AssistantMemory>) =
    buildString {
        appendLine()
        append("**Memories**")
        appendLine()
        appendLine(
            "Durable facts stored via `memory_tool`. Use them when relevant. " +
                "Do not dump this list unless the user asks what you remember. " +
                "If a listed fact conflicts with what the user says now, follow the user and update via `memory_tool` when appropriate."
        )
        if (memories.isEmpty()) {
            appendLine("(empty — no durable memories yet)")
        } else {
            val json = buildJsonArray {
                memories.forEach { memory ->
                    add(buildJsonObject {
                        put("id", memory.id)
                        put("content", memory.content)
                    })
                }
            }
            append(JsonInstantPretty.encodeToString(json))
            appendLine()
        }
    }

internal fun buildRollingSummaryPrompt(summary: String) =
    buildString {
        appendLine()
        append("**Conversation context summary**")
        appendLine()
        appendLine(
            "Compressed overview of earlier messages trimmed by the context message limit. " +
                "Use it for continuity; do not deny facts stated here."
        )
        appendLine(
            "This summary can omit fine-grained details. For specifics the user asks about that are not in " +
                "recent messages or this summary, call `$RECALL_CHAT_HISTORY_TOOL` first " +
                "(not `conversation_search`, which targets other chats). " +
                "Only if that lookup finds nothing should you say you don't have it recorded."
        )
        appendLine()
        append(summary.trim())
        appendLine()
    }

// Carryover overview prompt lives in SessionOverviewHelper.kt (buildCarryoverOverviewPrompt)

