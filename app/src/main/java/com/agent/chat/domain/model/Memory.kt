package com.agent.chat.domain.model

/**
 * Memory Manager 四分类（存储键兼容旧 long_term / short_term / important_event）。
 */
enum class MemoryCategory(val storageKey: String, val displayName: String) {
    /** 长期稳定信息：职业、兴趣、身份等 */
    CORE("core", "核心记忆"),
    /** 表达与互动偏好 */
    PREFERENCE("preference", "偏好记忆"),
    /** 近期事件 */
    EVENT("event", "事件记忆"),
    /** 重要情绪事件 */
    EMOTION("emotion", "情绪记忆"),
    ;

    companion object {
        fun fromStorage(key: String?): MemoryCategory {
            if (key.isNullOrBlank()) return EVENT
            return when (key) {
                CORE.storageKey, "long_term" -> CORE
                PREFERENCE.storageKey -> PREFERENCE
                EVENT.storageKey, "short_term", "important_event" -> EVENT
                EMOTION.storageKey -> EMOTION
                else -> entries.find { it.storageKey == key } ?: EVENT
            }
        }

        /** 根据内容与重要度自动归类。 */
        fun infer(
            content: String,
            importance: Int,
            createdAt: Long = System.currentTimeMillis(),
        ): MemoryCategory {
            val text = content.lowercase()
            val preferKeywords = listOf(
                "喜欢", "偏好", "习惯", "讨厌", "不喜欢", "倾向", "风格",
                "希望你", "说话", "口吻", "称呼",
            )
            val emotionKeywords = listOf(
                "难过", "崩溃", "焦虑", "开心", "感动", "委屈", "害怕",
                "孤独", "愤怒", "心疼", "哭", "情绪", "压力",
            )
            val coreKeywords = listOf(
                "职业", "工作", "兴趣", "爱好", "住在", "名字", "叫我",
                "我是", "从事", "专业", "学校", "家人",
            )
            val eventKeywords = listOf(
                "今天", "昨天", "明天", "上周", "下周", "纪念日", "生日",
                "面试", "入职", "毕业", "搬家", "结婚", "开会", "出差", "计划",
            )
            return when {
                preferKeywords.any { it in text } -> PREFERENCE
                emotionKeywords.any { it in text } && importance >= 6 -> EMOTION
                coreKeywords.any { it in text } || importance >= 8 -> CORE
                eventKeywords.any { it in text } -> EVENT
                importance >= 7 -> CORE
                System.currentTimeMillis() - createdAt > 14L * 24 * 60 * 60 * 1000 -> CORE
                else -> EVENT
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
    /** 重要度 1–10，越高越优先保留/注入 */
    val importance: Int = 5,
    val category: MemoryCategory = MemoryCategory.EVENT,
    /** 禁止注入 Prompt / 工具读取 */
    val blockedFromAi: Boolean = false,
) {
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
