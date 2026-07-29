package com.agent.chat.domain.model

enum class MemoryCategory(val storageKey: String, val displayName: String) {
    LONG_TERM("long_term", "长期记忆"),
    SHORT_TERM("short_term", "短期记忆"),
    PREFERENCE("preference", "偏好设置"),
    IMPORTANT_EVENT("important_event", "重要事件"),
    ;

    companion object {
        fun fromStorage(key: String?): MemoryCategory {
            if (key.isNullOrBlank()) return SHORT_TERM
            return entries.find { it.storageKey == key } ?: SHORT_TERM
        }

        /** 根据内容与重要度自动归类，避免冰冷的「未分类」库表感。 */
        fun infer(content: String, importance: Int, createdAt: Long = System.currentTimeMillis()): MemoryCategory {
            val text = content.lowercase()
            val preferKeywords = listOf("喜欢", "偏好", "习惯", "讨厌", "不喜欢", "倾向", "风格")
            val eventKeywords = listOf("今天", "昨天", "纪念日", "生日", "面试", "入职", "毕业", "搬家", "结婚")
            when {
                preferKeywords.any { it in text } -> return PREFERENCE
                eventKeywords.any { it in text } || importance >= 9 -> return IMPORTANT_EVENT
                importance >= 7 -> return LONG_TERM
                System.currentTimeMillis() - createdAt > 7L * 24 * 60 * 60 * 1000 -> return LONG_TERM
                else -> return SHORT_TERM
            }
        }
    }
}

data class Memory(
    val id: String,
    val personaId: String,
    val conversationId: String,
    val content: String,
    val createdAt: Long,
    val importance: Int = 5,
    val category: MemoryCategory = MemoryCategory.SHORT_TERM,
    /** 禁止注入 Prompt / 工具读取 */
    val blockedFromAi: Boolean = false,
) {
    /** 时间轴主标题：优先取「标签：内容」前半，否则截取首句。 */
    fun timelineTitle(): String {
        val trimmed = content.trim()
        val colon = trimmed.indexOfFirst { it == '：' || it == ':' }
        return if (colon in 1..24) {
            trimmed.substring(0, colon).trim()
        } else {
            trimmed.lineSequence().firstOrNull()?.take(28)?.ifBlank { "一条记忆" } ?: "一条记忆"
        }
    }

    fun timelineBody(): String {
        val trimmed = content.trim()
        val colon = trimmed.indexOfFirst { it == '：' || it == ':' }
        return if (colon in 1..24) {
            trimmed.substring(colon + 1).trim().ifBlank { trimmed }
        } else {
            trimmed
        }
    }
}
