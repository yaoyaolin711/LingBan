package com.agent.chat.ui.agent

import com.agent.chat.domain.model.Persona

/**
 * 从伙伴设定中提炼能力标签——像认识一位伙伴的特长，而非应用商店功能列表。
 */
object AgentCapabilities {

    fun of(persona: Persona): List<String> {
        val blob = listOf(persona.name, persona.description, persona.systemPrompt)
            .joinToString("\n")
            .lowercase()

        val matched = linkedSetOf<String>()
        fun hit(vararg keys: String, tag: String) {
            if (keys.any { it in blob }) matched += tag
        }

        hit("代码", "编程", "debug", "开发", "code", tag = "代码分析")
        hit("debug", "调试", "bug", "报错", tag = "Debug")
        hit("学习", "讲解", "知识", "教程", "导师", tag = "知识讲解")
        hit("计划", "复习", "考试", "课程", tag = "学习计划")
        hit("设计", "视觉", "ui", "创意", tag = "设计")
        hit("文案", "写作", "脚本", "广告", tag = "文案")
        hit("陪伴", "倾听", "情绪", "安慰", tag = "情感陪伴")
        hit("翻译", "语言", "英语", tag = "语言")
        hit("记忆", "回顾", "总结", tag = "记忆整理")
        hit("工具", "日程", "提醒", "日历", tag = "生活助手")

        if (matched.isEmpty()) {
            if (persona.lorebookEntries.isNotEmpty()) matched += "世界观"
            if (persona.presetMessages.isNotEmpty()) matched += "风格示范"
            matched += "自由对话"
        }
        return matched.take(4).toList()
    }

    fun memoryScopeLabel(memoryCount: Int): String = when {
        memoryCount <= 0 -> "尚未建立专属记忆"
        memoryCount < 5 -> "刚开始了解你 · $memoryCount 条记忆"
        memoryCount < 20 -> "跨会话共享 · $memoryCount 条记忆"
        else -> "深厚了解 · $memoryCount 条记忆"
    }
}
