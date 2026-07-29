package com.agent.chat.data.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenSnapshot(
    val foregroundApp: String = "",
    val screenTexts: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

/**
 * 全局屏幕内容快照存储。由 AccessibilityService 更新，工具层读取。
 */
object ScreenContentStore {
    private val _snapshot = MutableStateFlow(ScreenSnapshot())
    val snapshot: StateFlow<ScreenSnapshot> = _snapshot.asStateFlow()

    fun updateForegroundApp(packageName: String) {
        _snapshot.value = _snapshot.value.copy(
            foregroundApp = packageName,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun updateScreenText(texts: List<String>) {
        val trimmed = texts.take(MAX_TEXT_ITEMS)
        _snapshot.value = _snapshot.value.copy(
            screenTexts = trimmed,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private const val MAX_TEXT_ITEMS = 50
}
