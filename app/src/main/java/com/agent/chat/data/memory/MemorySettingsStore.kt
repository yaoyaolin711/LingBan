package com.agent.chat.data.memory

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 记忆系统运行时配置（SharedPreferences），与主对话 Provider 解耦。
 */
@Singleton
class MemorySettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<MemorySettings> = _snapshot.asStateFlow()

    fun get(): MemorySettings = _snapshot.value

    fun setSummaryProviderId(providerId: String?) {
        prefs.edit()
            .putString(KEY_SUMMARY_PROVIDER_ID, providerId?.takeIf { it.isNotBlank() })
            .apply()
        publish()
    }

    fun setExtractThreshold(threshold: Int) {
        prefs.edit()
            .putInt(KEY_EXTRACT_THRESHOLD, threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD))
            .apply()
        publish()
    }

    private fun readSnapshot(): MemorySettings = MemorySettings(
        summaryProviderId = prefs.getString(KEY_SUMMARY_PROVIDER_ID, null)?.takeIf { it.isNotBlank() },
        extractThreshold = prefs.getInt(KEY_EXTRACT_THRESHOLD, DEFAULT_THRESHOLD)
            .coerceIn(MIN_THRESHOLD, MAX_THRESHOLD),
    )

    private fun publish() {
        _snapshot.value = readSnapshot()
    }

    companion object {
        private const val PREFS_NAME = "memory_settings"
        private const val KEY_SUMMARY_PROVIDER_ID = "summary_provider_id"
        private const val KEY_EXTRACT_THRESHOLD = "extract_threshold"

        const val DEFAULT_THRESHOLD = 20
        const val MIN_THRESHOLD = 15
        const val MAX_THRESHOLD = 50

        /** 注入 System Prompt 的记忆硬性上限（字，兼容旧设置文案） */
        const val PROMPT_MEMORY_MAX_CHARS = 1200

        /** 注入记忆的最大 token 预算（约 1.5 字/token） */
        const val PROMPT_MEMORY_MAX_TOKENS = 800

        /** 单次滚动摘要输出上限（字） */
        const val SUMMARY_MAX_CHARS = 800

        /** 粗略：中文约 1.5 字/Token，用于设置页提示 */
        fun estimatePromptMemoryTokens(chars: Int = PROMPT_MEMORY_MAX_CHARS): Int =
            (chars / 1.5).toInt().coerceAtLeast(1)

        fun estimatePromptMemoryTokensByBudget(
            tokens: Int = PROMPT_MEMORY_MAX_TOKENS,
        ): Int = tokens

        fun estimateExtractTokens(threshold: Int, avgMsgChars: Int = 80): Int {
            val newDialogue = threshold * avgMsgChars
            val prevSummary = SUMMARY_MAX_CHARS
            val promptOverhead = 120
            return ((prevSummary + newDialogue + promptOverhead) / 1.5).toInt()
        }
    }
}

data class MemorySettings(
    val summaryProviderId: String? = null,
    val extractThreshold: Int = MemorySettingsStore.DEFAULT_THRESHOLD,
)
