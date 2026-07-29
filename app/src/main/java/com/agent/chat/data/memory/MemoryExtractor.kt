package com.agent.chat.data.memory

import android.content.Context
import android.util.Log
import com.agent.chat.data.provider.AIProvider
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ModelConfig
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 异步增量记忆提取：只发送「上次摘要 + 新增消息」，Token 不随全量历史增长。
 * 失败不影响正常聊天。
 */
@Singleton
class MemoryExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiProvider: AIProvider,
    private val memoryRepository: MemoryRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    private val memorySettingsStore: MemorySettingsStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Fire-and-forget：不阻塞对话。无 persona 时跳过。
     * @param chatProviderConfigId 主对话 Provider，仅在未配置摘要专用模型时作为回退
     */
    fun maybeExtractAsync(
        personaId: String?,
        conversationId: String,
        messages: List<Message>,
        providerConfigId: String?,
    ) {
        if (personaId.isNullOrBlank()) return
        val threshold = memorySettingsStore.get().extractThreshold
        if (messages.size < threshold) return

        scope.launch {
            runCatching {
                extractIfNeeded(
                    personaId = personaId,
                    conversationId = conversationId,
                    messages = messages,
                    chatProviderConfigId = providerConfigId,
                    threshold = threshold,
                )
            }.onFailure { e ->
                Log.w(TAG, "记忆提取失败（已忽略）: ${e.message}")
            }
        }
    }

    private suspend fun extractIfNeeded(
        personaId: String,
        conversationId: String,
        messages: List<Message>,
        chatProviderConfigId: String?,
        threshold: Int,
    ) {
        val mutex = locks.getOrPut(conversationId) { Mutex() }
        mutex.withLock {
            val lastProcessedAt = prefs.getLong(prefKey(conversationId), 0L)
            val pending = messages.filter { it.createdAt > lastProcessedAt && it.content.isNotBlank() }
            if (pending.size < threshold) return

            val window = pending.takeLast(threshold)
            val previousMemory = memoryRepository.getLatestByPersona(personaId)
            val previousSummary = previousMemory?.content.orEmpty()

            val summary = summarizeIncremental(
                previousSummary = previousSummary,
                newMessages = window,
                chatProviderConfigId = chatProviderConfigId,
            ) ?: return

            if (summary.isBlank() || isNoMemoryResult(summary)) {
                markProcessed(conversationId, window.last().createdAt)
                return
            }

            val clipped = summary.take(MemorySettingsStore.SUMMARY_MAX_CHARS)
            val importance = estimateImportance(clipped)
            if (previousMemory != null) {
                // 滚动更新同一条摘要，避免记忆条目无限膨胀
                memoryRepository.updateMemoryContent(
                    memory = previousMemory,
                    content = clipped,
                    importance = importance,
                    conversationId = conversationId,
                )
            } else {
                memoryRepository.saveMemory(
                    personaId = personaId,
                    conversationId = conversationId,
                    content = clipped,
                    importance = importance,
                )
            }
            markProcessed(conversationId, window.last().createdAt)
        }
    }

    private suspend fun summarizeIncremental(
        previousSummary: String,
        newMessages: List<Message>,
        chatProviderConfigId: String?,
    ): String? {
        val providerConfig = resolveSummaryProvider(chatProviderConfigId) ?: return null

        val dialogue = newMessages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
            }
            "$role: ${msg.content.trim()}"
        }

        val prompt = buildString {
            append("这是你之前的记忆摘要（如果没有则为空）：")
            append(previousSummary.ifBlank { "（空）" })
            append("\n这是最近的新对话内容：")
            append(dialogue)
            append("\n请更新摘要，保留重要信息、删除不再相关的内容，控制在")
            append(MemorySettingsStore.SUMMARY_MAX_CHARS)
            append("字以内")
        }

        val requestMessages = listOf(ChatMessage(ChatMessage.ROLE_USER, prompt))
        val config = providerConfigRepository.toModelConfig(
            config = providerConfig,
            temperature = 0.3f,
        )

        return collectCompletion(requestMessages, config)
            .trim()
            .takeIf { it.isNotBlank() }
    }

    /**
     * 优先使用设置里的摘要专用 Provider；未配置时回退到主对话 / 默认 Provider。
     */
    private suspend fun resolveSummaryProvider(chatProviderConfigId: String?) =
        memorySettingsStore.get().summaryProviderId
            ?.let { providerConfigRepository.getConfig(it) }
            ?: chatProviderConfigId?.let { providerConfigRepository.getConfig(it) }
            ?: providerConfigRepository.getDefaultConfig()

    private suspend fun collectCompletion(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ): String {
        val builder = StringBuilder()
        aiProvider.chatStream(messages, config).collect { token ->
            builder.append(token)
        }
        return builder.toString()
    }

    private fun markProcessed(conversationId: String, timestamp: Long) {
        prefs.edit().putLong(prefKey(conversationId), timestamp).apply()
    }

    private fun prefKey(conversationId: String) = "last_ts_$conversationId"

    private fun isNoMemoryResult(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized in NO_MEMORY_MARKERS ||
            (normalized.startsWith("无") && normalized.length < 8)
    }

    private fun estimateImportance(content: String): Int {
        val lengthBoost = when {
            content.length >= 80 -> 2
            content.length >= 40 -> 1
            else -> 0
        }
        return (5 + lengthBoost).coerceIn(1, 10)
    }

    companion object {
        private const val TAG = "MemoryExtractor"
        private const val PREFS_NAME = "memory_extractor"

        /** 与 [MemorySettingsStore.DEFAULT_THRESHOLD] 保持一致，供 UI 兜底展示 */
        const val MESSAGE_THRESHOLD = 20

        private val NO_MEMORY_MARKERS = setOf(
            "无", "没有", "无。", "没有。", "暂无", "无值得记住的信息",
            "none", "n/a", "无关键信息", "（空）",
        )
    }
}
