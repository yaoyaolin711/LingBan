package com.agent.chat.data.interaction

/**
 * 检测用户本轮消息是否主动涉及某类互动（启发式）。
 */
enum class InteractionIntent {
    /** 技术 / 工作 / 知识 / 事实问答 */
    TECHNICAL,
    ROMANTIC,
    FLIRTING,
    INTIMATE,
    ROLEPLAY,
    /** 普通闲聊 */
    GENERAL,
}

object InteractionIntentDetector {

    fun detect(userMessage: String): Set<InteractionIntent> {
        val text = userMessage.trim()
        if (text.isBlank()) return setOf(InteractionIntent.GENERAL)

        val intents = mutableSetOf<InteractionIntent>()
        if (isTechnical(text)) intents += InteractionIntent.TECHNICAL
        if (isRomantic(text)) intents += InteractionIntent.ROMANTIC
        if (isFlirting(text)) intents += InteractionIntent.FLIRTING
        if (isIntimate(text)) intents += InteractionIntent.INTIMATE
        if (isRoleplay(text)) intents += InteractionIntent.ROLEPLAY
        if (intents.isEmpty()) intents += InteractionIntent.GENERAL
        return intents
    }

    fun primaryIntent(intents: Set<InteractionIntent>): InteractionIntent = when {
        InteractionIntent.TECHNICAL in intents -> InteractionIntent.TECHNICAL
        InteractionIntent.INTIMATE in intents -> InteractionIntent.INTIMATE
        InteractionIntent.FLIRTING in intents -> InteractionIntent.FLIRTING
        InteractionIntent.ROMANTIC in intents -> InteractionIntent.ROMANTIC
        InteractionIntent.ROLEPLAY in intents -> InteractionIntent.ROLEPLAY
        else -> InteractionIntent.GENERAL
    }

    private fun isTechnical(text: String): Boolean {
        val lower = text.lowercase()
        val keywords = listOf(
            "代码", "编程", "bug", "报错", "错误", "api", "函数", "算法", "kotlin", "java",
            "python", "怎么实现", "如何实现", "教程", "原理", "配置", "部署", "编译",
            "数据库", "sql", "框架", "接口", "调试", "技术", "开发", "项目",
        )
        if (keywords.any { it in lower || it in text }) return true
        if (Regex("""[{}();]|\.kt\b|\.java\b|def \w+\(|class \w+""").containsMatchIn(text)) return true
        return false
    }

    private fun isRomantic(text: String): Boolean {
        val patterns = listOf(
            "喜欢我", "爱你", "爱我吗", "在一起", "男朋友", "女朋友", "恋人", "谈恋爱",
            "结婚", "交往", "心动", "爱情", "表白", "告白",
        )
        return patterns.any { it in text }
    }

    private fun isFlirting(text: String): Boolean {
        val patterns = listOf(
            "撩", "暧昧", "撒娇", "宝贝", "亲爱的", "老公", "老婆", "么么", "亲亲",
            "想你", "好甜", "犯规", "心动了",
        )
        return patterns.any { it in text }
    }

    private fun isIntimate(text: String): Boolean {
        val patterns = listOf(
            "亲密", "抱抱", "拥抱", "亲吻", "摸", "床", "脱", "身体", "开放",
            "尺度", "色情", "性爱", "做爱",
        )
        return patterns.any { it in text }
    }

    private fun isRoleplay(text: String): Boolean {
        val patterns = listOf(
            "角色扮演", "扮演", "剧情", "讲个故事", "讲故事", "续写", "设定",
            "你是.*我是", "开始游戏", "rp", "roleplay",
        )
        return patterns.any { it in text } ||
            Regex("""(?i)role\s*play""").containsMatchIn(text)
    }
}
