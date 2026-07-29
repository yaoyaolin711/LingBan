package com.agent.chat.domain.model

/** 预设示范对话（few-shot），请求时插在 system 之后、真实历史之前 */
data class PresetMessage(
    val role: String,
    val content: String,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/** 世界书条目：近期对话命中关键词时注入 system */
data class LorebookEntry(
    val id: String,
    /** 逗号/换行分隔前的关键词列表 */
    val keys: List<String> = emptyList(),
    val content: String = "",
    val enabled: Boolean = true,
    val caseSensitive: Boolean = false,
)

/**
 * 输出正则改写。
 * [visualOnly]=true 时只改展示文案，落库仍用原文；false 时展示与落库都改写。
 */
data class OutputRegex(
    val id: String,
    val pattern: String,
    val replacement: String = "",
    val enabled: Boolean = true,
    val visualOnly: Boolean = false,
)
