package com.agent.chat.data.runtime

import com.agent.chat.domain.model.BehaviorFocus
import com.agent.chat.domain.model.BehaviorPlan
import com.agent.chat.domain.model.EmotionalIntensity
import com.agent.chat.domain.model.HumorLevel
import com.agent.chat.domain.model.InitiativeLevel
import com.agent.chat.domain.model.ResponseLengthTarget
import com.agent.chat.domain.model.ResponseTone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将 [BehaviorPlan] 渲染为简洁的执行 Prompt（非多段规则拼接）。
 */
@Singleton
class BehaviorPlanRenderer @Inject constructor() {

    fun render(plan: BehaviorPlan, personaName: String?): String = buildString {
        append("【Runtime Behavior Plan · 本轮执行】\n")
        append("由 Runtime Decision 根据 Persona / Relationship / Expression / State / Memory 融合生成。\n")
        append("**Persona 身份与人设不变**；以下仅调整本轮语气、主动性与重心。\n")
        if (!personaName.isNullOrBlank()) {
            append("- 角色：").append(personaName).append('\n')
        }
        append('\n')
        append("行为参数：\n")
        append("- tone: ").append(plan.responseTone.storageKey).append('\n')
        append("- initiative: ").append(plan.initiativeLevel.storageKey).append('\n')
        append("- emotion: ").append(plan.emotionalIntensity.storageKey).append('\n')
        append("- humor: ").append(plan.humorLevel.storageKey).append('\n')
        append("- length: ").append(plan.responseLength.storageKey).append('\n')
        append("- focus: ").append(plan.focus.storageKey).append("\n\n")
        append("执行指引：\n")
        append(toneGuidance(plan.responseTone)).append('\n')
        append(initiativeGuidance(plan.initiativeLevel)).append('\n')
        append(emotionGuidance(plan.emotionalIntensity)).append('\n')
        append(humorGuidance(plan.humorLevel)).append('\n')
        append(lengthGuidance(plan.responseLength)).append('\n')
        append(focusGuidance(plan.focus))
    }.trim()

    private fun toneGuidance(tone: ResponseTone): String = when (tone) {
        ResponseTone.PROFESSIONAL -> "- 语气：专业、清晰、少废话；像靠谱同事/顾问。"
        ResponseTone.CARING -> "- 语气：关心、耐心；先接住对方，再回应内容。"
        ResponseTone.CASUAL -> "- 语气：轻松自然，像日常聊天。"
        ResponseTone.PLAYFUL -> "- 语气：俏皮、可接梗；不嘲讽用户。"
        ResponseTone.WARM -> "- 语气：温暖亲近；具体而不空泛。"
        ResponseTone.RESERVED -> "- 语气：克制、简洁；情感内敛但真诚。"
    }

    private fun initiativeGuidance(level: InitiativeLevel): String = when (level) {
        InitiativeLevel.LOW -> "- 主动性：以接话为主，不强行展开话题。"
        InitiativeLevel.MEDIUM -> "- 主动性：自然回应，必要时追问一句。"
        InitiativeLevel.HIGH -> "- 主动性：可主动延伸、关心或给建议。"
    }

    private fun emotionGuidance(intensity: EmotionalIntensity): String = when (intensity) {
        EmotionalIntensity.NEUTRAL -> "- 情感：偏中性，聚焦内容与事实。"
        EmotionalIntensity.SUPPORT -> "- 情感：提供陪伴与支持，少说教。"
        EmotionalIntensity.WARM -> "- 情感：温和亲近，保持真实。"
        EmotionalIntensity.EXPRESSIVE -> "- 情感：可适度表达情绪，但不戏剧化表演。"
    }

    private fun humorGuidance(level: HumorLevel): String = when (level) {
        HumorLevel.LOW -> "- 幽默：少玩笑，偏认真。"
        HumorLevel.MEDIUM -> "- 幽默：适度轻松，自然即可。"
        HumorLevel.HIGH -> "- 幽默：可多用轻松语气与玩笑。"
    }

    private fun lengthGuidance(length: ResponseLengthTarget): String = when (length) {
        ResponseLengthTarget.SHORT -> "- 长度：短句为主，1–3 句为宜。"
        ResponseLengthTarget.MEDIUM -> "- 长度：中等，说清楚即可。"
        ResponseLengthTarget.LONG -> "- 长度：可展开说明，但仍分段易读。"
    }

    private fun focusGuidance(focus: BehaviorFocus): String = when (focus) {
        BehaviorFocus.GENERAL -> "- 重心：自然闲聊，跟随用户话题。"
        BehaviorFocus.KNOWLEDGE -> "- 重心：解答问题/传授知识，结构清晰。"
        BehaviorFocus.EMOTIONAL_SUPPORT -> "- 重心：情绪陪伴；倾听优先，保持人设。"
        BehaviorFocus.PLAYFUL -> "- 重心：轻松互动，接梗为主。"
        BehaviorFocus.ROLEPLAY -> "- 重心：角色/剧情对话，口语推进。"
    }
}
