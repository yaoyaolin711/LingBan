package com.agent.chat.data.ai.prompt

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PromptLogEntry(
    val timestampMs: Long,
    val conversationId: String?,
    val agentId: String,
    val modelName: String?,
    val providerName: String?,
    val sectionIds: List<String>,
    val charCount: Int,
    val systemPrompt: String,
) {
    val timeLabel: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
}

/**
 * 记录最终发给 LLM 的 System Prompt，便于调试。
 * - Logcat：`PromptComposer`
 * - 内存：最近 [MAX_HISTORY] 条 + [lastEntry]
 */
@Singleton
class PromptLogger @Inject constructor() {

    private val _lastEntry = MutableStateFlow<PromptLogEntry?>(null)
    val lastEntry: StateFlow<PromptLogEntry?> = _lastEntry.asStateFlow()

    private val history = ArrayDeque<PromptLogEntry>(MAX_HISTORY)

    fun log(result: PromptComposeResult, conversationId: String? = null) {
        val entry = PromptLogEntry(
            timestampMs = System.currentTimeMillis(),
            conversationId = conversationId,
            agentId = result.meta.agentId,
            modelName = result.meta.modelName,
            providerName = result.meta.providerName,
            sectionIds = result.meta.sectionIds,
            charCount = result.meta.charCount,
            systemPrompt = result.systemPrompt,
        )
        synchronized(history) {
            if (history.size >= MAX_HISTORY) history.removeFirst()
            history.addLast(entry)
        }
        _lastEntry.value = entry

        Log.d(
            TAG,
            "composed agent=${entry.agentId} model=${entry.modelName} " +
                "sections=${entry.sectionIds} chars=${entry.charCount}",
        )
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "----- SYSTEM PROMPT BEGIN -----\n${entry.systemPrompt}\n----- END -----")
        } else {
            val preview = entry.systemPrompt.take(PREVIEW_CHARS)
            Log.d(TAG, "preview:\n$preview${if (entry.systemPrompt.length > PREVIEW_CHARS) "…" else ""}")
        }
    }

    fun recent(limit: Int = MAX_HISTORY): List<PromptLogEntry> =
        synchronized(history) { history.toList().takeLast(limit) }

    fun clear() {
        synchronized(history) { history.clear() }
        _lastEntry.value = null
    }

    companion object {
        private const val TAG = "PromptComposer"
        private const val MAX_HISTORY = 20
        private const val PREVIEW_CHARS = 800
    }
}
