package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.companion.model.CompanionCharacterCard
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.policy.CompanionProactivePolicy
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.overlay.pet.COMPANION_BUBBLE_MAX_CHARS

/**
 * 主动发言的人设拼装：优先角色卡，其次助手 systemPrompt。
 * 场景只作「为什么找人」的暗线，禁止写成官方关怀/健康提醒。
 * 输出按悬浮气泡空间强制短句。
 */
object CompanionVoiceBuilder {

    fun personaBlock(assistant: Assistant, character: CompanionCharacterCard?): String {
        val name = character?.name?.takeIf { it.isNotBlank() }
            ?: assistant.name.takeIf { it.isNotBlank() }
            ?: "伴侣"
        return buildString {
            appendLine("你就是「$name」，正在用自己的身份给用户发私聊。")
            appendLine("你不是客服、不是健康助手、不是系统通知员。")
            character?.let { card ->
                if (card.personality.isNotBlank()) {
                    appendLine("性格：${card.personality.trim()}")
                }
                if (card.speakingStyle.isNotBlank()) {
                    appendLine("说话风格：${card.speakingStyle.trim()}")
                }
                if (card.scenario.isNotBlank()) {
                    appendLine("情境：${card.scenario.trim()}")
                }
                if (card.systemPrompt.isNotBlank()) {
                    appendLine(card.systemPrompt.trim())
                }
            }
            val assistantPrompt = assistant.systemPrompt.trim()
            if (assistantPrompt.isNotEmpty() &&
                assistantPrompt != character?.systemPrompt?.trim().orEmpty()
            ) {
                appendLine(assistantPrompt)
            }
            if (toString().lines().count { it.isNotBlank() } <= 2) {
                appendLine("用自然、有人味的口吻跟用户说话，像关系很好的人。")
            }
        }.trim()
    }

    fun reachOutSystemRules(emotion: CompanionEmotionState): String = buildString {
        appendLine("现在你要主动找用户说几句话（私聊口吻）。")
        appendLine(CompanionProactivePolicy.emotionToneHint(emotion))
        appendLine("硬性要求：")
        appendLine("- 严格保持人设口吻与称呼习惯")
        appendLine("- 像真人发微信：口语、可撒娇/吐槽/吃醋/想念，按人设来")
        appendLine("- 极短：整段不超过 ${COMPANION_BUBBLE_MAX_CHARS} 个汉字，最多两句，不要换行、不要列表")
        appendLine("- 禁止：使用关怀、注意休息、保护眼睛、健康提醒、数字健康、系统播报腔")
        appendLine("- 禁止：自我介绍是 AI、emoji 堆砌、标题党、官方客服腔")
        appendLine("- 若已有最近对话：禁止「你好/在吗/早安/晚安」式重新开场")
        appendLine("- 不要复述「连续使用了 X 分钟」这种报告句")
        appendLine("- 只输出要对用户说的话，不要内心独白或旁白")
    }.trim()

    /** 无 LLM 时的兜底：尽量不像官方，仍通用短句 */
    fun awayFallback(
        companionName: String,
        appName: String,
        emotion: CompanionEmotionState,
    ): String = when (emotion) {
        CompanionEmotionState.PLAYFUL ->
            "又在「$appName」泡着？回来理我一下嘛。"
        CompanionEmotionState.WARM ->
            "你在「$appName」好久了……有点想你。"
        CompanionEmotionState.CONCERNED ->
            "还在「$appName」吗？回我一声。"
        CompanionEmotionState.CALM ->
            "嗨，去「$appName」好久了，想听听你。"
    }.let { clipForBubble(it) }

    fun clipForBubble(text: String): String {
        val t = text.trim().replace("\n", " ")
        if (t.length <= COMPANION_BUBBLE_MAX_CHARS) return t
        return t.take(COMPANION_BUBBLE_MAX_CHARS - 1) + "…"
    }
}
