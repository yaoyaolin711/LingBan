package com.agent.chat.data.mode

import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.PersonaEmotion
import com.agent.chat.domain.model.PersonaPersonality
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.normalized

/**
 * 模式对 Prompt 的影响。
 */
data class ModePromptPolicy(
    val mode: LingBanChatMode,
    /** 是否注入真人/模式对话规则层 */
    val humanRulesEnabled: Boolean,
    /** assets catalog key：human_conversation / human_conversation_assistant / … */
    val humanRulesAssetKey: String,
    /** catalog key：modes/assistant.txt 等 */
    val modeSectionAssetKey: String,
    /** 是否优先注入记忆（助手模式可降权） */
    val memoryEnabled: Boolean,
    val memoryTokenScale: Float,
)

/**
 * 模式对 Response Controller 的阈值。
 */
data class ModeResponsePolicy(
    val mode: LingBanChatMode,
    val dramaticCap: Float,
    val monoCap: Float,
    val humanFloor: Float,
    val lengthFloor: Float,
    val emotionFloor: Float,
    val idealSentenceMax: Int,
    val charIdealMax: Int,
)

/**
 * 模式对 Persona / 采样参数的调整结果。
 */
data class ModePersonaEffect(
    val temperature: Float,
    val profile: PersonaProfile?,
    val allowInnerMonologue: Boolean,
)

object ModePolicies {

    fun prompt(mode: LingBanChatMode): ModePromptPolicy = when (mode) {
        LingBanChatMode.ASSISTANT -> ModePromptPolicy(
            mode = mode,
            humanRulesEnabled = true,
            humanRulesAssetKey = "human_conversation_assistant",
            modeSectionAssetKey = "mode_assistant",
            memoryEnabled = true,
            memoryTokenScale = 0.6f,
        )
        LingBanChatMode.COMPANION -> ModePromptPolicy(
            mode = mode,
            humanRulesEnabled = true,
            humanRulesAssetKey = "human_conversation",
            modeSectionAssetKey = "mode_companion",
            memoryEnabled = true,
            memoryTokenScale = 1f,
        )
        LingBanChatMode.ROLEPLAY -> ModePromptPolicy(
            mode = mode,
            humanRulesEnabled = true,
            humanRulesAssetKey = "human_conversation_roleplay",
            modeSectionAssetKey = "mode_roleplay",
            memoryEnabled = true,
            memoryTokenScale = 0.85f,
        )
    }

    fun response(mode: LingBanChatMode): ModeResponsePolicy = when (mode) {
        LingBanChatMode.ASSISTANT -> ModeResponsePolicy(
            mode = mode,
            dramaticCap = 0.28f,
            monoCap = 0.15f,
            humanFloor = 0.5f,
            lengthFloor = 0.5f,
            emotionFloor = 0.3f,
            idealSentenceMax = 4,
            charIdealMax = 180,
        )
        LingBanChatMode.COMPANION -> ModeResponsePolicy(
            mode = mode,
            dramaticCap = 0.42f,
            monoCap = 0.28f,
            humanFloor = 0.55f,
            lengthFloor = 0.45f,
            emotionFloor = 0.35f,
            idealSentenceMax = 5,
            charIdealMax = 220,
        )
        LingBanChatMode.ROLEPLAY -> ModeResponsePolicy(
            mode = mode,
            dramaticCap = 0.78f,
            monoCap = 0.65f,
            humanFloor = 0.4f,
            lengthFloor = 0.32f,
            emotionFloor = 0.3f,
            idealSentenceMax = 10,
            charIdealMax = 480,
        )
    }

    fun applyPersona(mode: LingBanChatMode, persona: Persona?): ModePersonaEffect {
        val baseTemp = persona?.defaultTemperature ?: when (mode) {
            LingBanChatMode.ASSISTANT -> 0.4f
            LingBanChatMode.COMPANION -> 0.7f
            LingBanChatMode.ROLEPLAY -> 0.9f
        }
        val temperature = when (mode) {
            LingBanChatMode.ASSISTANT -> baseTemp.coerceIn(0.2f, 0.55f)
            LingBanChatMode.COMPANION -> baseTemp.coerceIn(0.45f, 0.9f)
            LingBanChatMode.ROLEPLAY -> baseTemp.coerceIn(0.7f, 1.2f)
        }

        val profile = persona?.profile?.normalized()?.let { p ->
            when (mode) {
                LingBanChatMode.ASSISTANT -> p.copy(
                    personality = p.personality.copy(
                        warmth = (p.personality.warmth - 15).coerceIn(0, 100),
                        rationality = (p.personality.rationality + 20).coerceIn(0, 100),
                        empathy = (p.personality.empathy - 10).coerceIn(0, 100),
                        energy = (p.personality.energy - 10).coerceIn(0, 100),
                        humor = (p.personality.humor - 5).coerceIn(0, 100),
                    ),
                    emotion = p.emotion.copy(
                        expressionLevel = p.emotion.expressionLevel.coerceAtMost(40),
                        dramaticLevel = p.emotion.dramaticLevel.coerceAtMost(20),
                        allowInnerMonologue = false,
                        constraints = (p.emotion.constraints + listOf(
                            "保持理性简洁，少情绪铺陈",
                            "禁止内心独白与戏剧化表演",
                        )).distinct(),
                    ),
                )
                LingBanChatMode.COMPANION -> p.copy(
                    emotion = p.emotion.copy(
                        dramaticLevel = p.emotion.dramaticLevel.coerceAtMost(40),
                        allowInnerMonologue = false,
                        constraints = (p.emotion.constraints + listOf(
                            "自然互动，有温度但不煽情",
                        )).distinct(),
                    ),
                )
                LingBanChatMode.ROLEPLAY -> p.copy(
                    personality = PersonaPersonality(
                        warmth = p.personality.warmth.coerceAtLeast(50),
                        humor = p.personality.humor,
                        rationality = p.personality.rationality,
                        empathy = p.personality.empathy.coerceAtLeast(50),
                        energy = p.personality.energy.coerceAtLeast(45),
                    ),
                    emotion = PersonaEmotion(
                        expressionLevel = p.emotion.expressionLevel.coerceAtLeast(45),
                        dramaticLevel = p.emotion.dramaticLevel.coerceIn(25, 70),
                        allowInnerMonologue = true,
                        constraints = p.emotion.constraints.filterNot {
                            it.contains("禁止内心") || it.contains("不要夸张")
                        },
                    ),
                )
            }.normalized()
        }

        return ModePersonaEffect(
            temperature = temperature,
            profile = profile,
            allowInnerMonologue = mode == LingBanChatMode.ROLEPLAY,
        )
    }
}
