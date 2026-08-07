package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.ai.tools.MEMORY_SEARCH_TOOL
import me.rerere.rikkahub.data.ai.tools.RECALL_CHAT_HISTORY_TOOL
import me.rerere.rikkahub.data.db.entity.MemoryLayer
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.GlobalUserProfile
import me.rerere.rikkahub.data.repository.MemoryTurnHints
import me.rerere.rikkahub.utils.JsonInstantPretty

internal const val MEMORY_PROFILE_CHAR_BUDGET = 560
internal const val MEMORY_EPISODE_INDEX_CHAR_BUDGET = 560
internal const val MEMORY_EPISODE_PREVIEW_CHARS = 40
internal const val MEMORY_GRAPH_HUBS_CHAR_BUDGET = 120
internal const val GLOBAL_USER_PROFILE_CHAR_BUDGET = 720

/**
 * User-authored stable identity card injected for every companion when non-empty.
 * Separate from per-assistant `memory_tool` memories.
 */
internal fun buildGlobalUserProfilePrompt(profile: GlobalUserProfile): String {
    if (profile.isEmpty) return ""
    return buildString {
        appendLine()
        append("**User profile (shared across all companions)**")
        appendLine()
        appendLine(
            "Stable facts the user filled in manually. Every companion may rely on these. " +
                "Do not contradict without checking with the user. " +
                "Relationship-specific memories with a single companion stay in that companion's private memory — do not merge them here."
        )
        fun line(label: String, value: String) {
            if (value.isBlank()) return
            appendLine("$label: ${value.trim()}")
        }
        line("Name", profile.displayName)
        line("Birthday", profile.birthday)
        line("Occupation", profile.occupation)
        line("Personality", profile.personality)
        line("Preferred addressing", profile.preferredAddressing)
        line("Language/locale", profile.locale)
        line("Notes", profile.extraNotes)
    }.take(GLOBAL_USER_PROFILE_CHAR_BUDGET)
}

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
        if (hasMemoryTool) add("**Memories** (profile + episode index) and `$MEMORY_SEARCH_TOOL`")
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
                    "  - Durable cross-conversation facts: ONLY use `$MEMORY_SEARCH_TOOL` " +
                        "(never invent a second memory-search tool). " +
                        "If **Memories** profile/index or **Memory hints for this turn** already answers the question, do NOT call `$MEMORY_SEARCH_TOOL`. " +
                        "Results / turn hints may include a short relation summary (entity neighborhood); " +
                        "prefer the first hit and do not invent edges that were not given. " +
                        "Do not blend conflicting episodes into a third fact. " +
                        "Conflict order: user's current statement > profile HEAD > recalled episodes. " +
                        "Use `memory_tool` only to create/edit/delete durable facts the user clearly wants remembered."
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

/**
 * Budgeted memory injection: full L2 profile (capped) + L0 episode index previews.
 * Full episode bodies are retrieved via [MEMORY_SEARCH_TOOL].
 * Optional Graph hubs line is a ≤120-char skeleton only (no full graph dump).
 */
internal fun buildMemoryPrompt(
    memories: List<AssistantMemory>,
    graphHubsSummary: String? = null,
): String {
    if (memories.isEmpty() && graphHubsSummary.isNullOrBlank()) return ""

    val profiles = memories
        .filter { it.layer == MemoryLayer.PROFILE }
        .sortedWith(compareByDescending<AssistantMemory> { it.updatedAt }.thenByDescending { it.id })
    val episodes = memories
        .filter { it.layer != MemoryLayer.PROFILE }
        .sortedWith(compareByDescending<AssistantMemory> { it.updatedAt }.thenByDescending { it.id })

    val profileJson = buildBudgetedProfileJson(profiles)
    val episodeIndex = buildBudgetedEpisodeIndex(episodes)
    val hubs = graphHubsSummary?.trim()?.take(MEMORY_GRAPH_HUBS_CHAR_BUDGET)?.takeIf { it.isNotBlank() }

    if (profileJson == null && episodeIndex == null && hubs == null) return ""

    return buildString {
        appendLine()
        append("**Memories**")
        appendLine()
        appendLine(
            "Durable facts from `memory_tool`. Profile fields below are current HEAD values. " +
                "Episode notes are indexed only (id + short preview); call `$MEMORY_SEARCH_TOOL` for full text. " +
                "Do not dump this block unless the user asks what you remember. " +
                "If a listed fact conflicts with what the user says now, follow the user and update via `memory_tool`. " +
                "If `$MEMORY_SEARCH_TOOL` returns multiple hits, prefer the first."
        )
        if (profileJson != null) {
            appendLine("Profile:")
            appendLine(profileJson)
        }
        if (episodeIndex != null) {
            appendLine("Episode index (use `$MEMORY_SEARCH_TOOL` for details):")
            appendLine(episodeIndex)
            val indexedCount = episodeIndex.lineSequence().count { it.isNotBlank() }
            if (episodes.size > indexedCount) {
                appendLine("(+${episodes.size - indexedCount} more episode notes not shown — search if needed)")
            }
        } else if (episodes.isNotEmpty()) {
            appendLine("Episode notes exist but none fit the index budget; call `$MEMORY_SEARCH_TOOL`.")
        }
        if (hubs != null) {
            appendLine("Graph hubs (skeleton only — do not invent edges beyond this):")
            appendLine(hubs)
        }
    }
}

/** High-confidence turn appendix (max 2 + optional relation line). Empty if no entity hit. */
internal fun buildMemoryPreretrievePrompt(hints: MemoryTurnHints): String {
    if (hints.isEmpty) return ""
    return buildString {
        appendLine()
        append("**Memory hints for this turn**")
        appendLine()
        appendLine(
            "High-confidence matches for entities mentioned by the user (pre-ranked, prefer the first). " +
                "May include a short relation line — do not invent edges beyond it. " +
                "Use when relevant; do not invent beyond these. Skip `$MEMORY_SEARCH_TOOL` if this already answers."
        )
        hints.relationLine?.takeIf { it.isNotBlank() }?.let { line ->
            appendLine("Relations: $line")
        }
        hints.memories.forEachIndexed { index, memory ->
            appendLine("${index + 1}. [id=${memory.id}] ${memory.content}")
        }
    }
}

internal fun buildBudgetedProfileJson(profiles: List<AssistantMemory>): String? {
    if (profiles.isEmpty()) return null
    val selected = ArrayList<AssistantMemory>()
    var used = 2 // [] wrapper approximation
    for (memory in profiles) {
        val piece = estimateMemoryJsonChars(memory, includeFullContent = true)
        val extra = if (selected.isEmpty()) piece else piece + 1
        if (selected.isNotEmpty() && used + extra > MEMORY_PROFILE_CHAR_BUDGET) break
        if (selected.isEmpty() && extra > MEMORY_PROFILE_CHAR_BUDGET) {
            // Always include at least a truncated first profile entry
            selected.add(memory.copy(content = memory.content.take(MEMORY_PROFILE_CHAR_BUDGET.coerceAtLeast(24))))
            break
        }
        selected.add(memory)
        used += extra
    }
    if (selected.isEmpty()) return null
    return JsonInstantPretty.encodeToString(
        buildJsonArray {
            selected.forEach { memory ->
                add(buildJsonObject {
                    put("id", memory.id)
                    memory.topicKey?.let { put("topic", it) }
                    put("content", memory.content)
                })
            }
        }
    )
}

internal fun buildBudgetedEpisodeIndex(episodes: List<AssistantMemory>): String? {
    if (episodes.isEmpty()) return null
    val lines = ArrayList<String>()
    var used = 0
    for (memory in episodes) {
        val preview = memory.content.replace('\n', ' ').trim().take(MEMORY_EPISODE_PREVIEW_CHARS)
        val line = "- id=${memory.id}: $preview"
        val extra = line.length + 1
        if (lines.isNotEmpty() && used + extra > MEMORY_EPISODE_INDEX_CHAR_BUDGET) break
        if (lines.isEmpty() && extra > MEMORY_EPISODE_INDEX_CHAR_BUDGET) {
            lines.add(line.take(MEMORY_EPISODE_INDEX_CHAR_BUDGET))
            break
        }
        lines.add(line)
        used += extra
    }
    return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun estimateMemoryJsonChars(memory: AssistantMemory, includeFullContent: Boolean): Int {
    val content = if (includeFullContent) memory.content else memory.content.take(MEMORY_EPISODE_PREVIEW_CHARS)
    val topic = memory.topicKey?.length ?: 0
    // Rough JSON overhead for id/topic/content keys
    return content.length + topic + 28
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
