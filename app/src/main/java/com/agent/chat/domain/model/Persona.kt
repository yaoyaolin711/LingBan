package com.agent.chat.domain.model

data class Persona(
    val id: String,
    val name: String,
    val avatar: String = "",
    val systemPrompt: String,
    val defaultTemperature: Float = 0.7f,
    val description: String = "",
    /** 符合角色语气的开场白，用于空会话问候等 */
    val openingLine: String = "",
    /** 预设示范对话（few-shot） */
    val presetMessages: List<PresetMessage> = emptyList(),
    /** 关键词触发的世界书条目 */
    val lorebookEntries: List<LorebookEntry> = emptyList(),
    /** 助手输出正则改写 */
    val outputRegexes: List<OutputRegex> = emptyList(),
)
