package com.agent.chat.data.runtime

import com.agent.chat.data.mode.ModeResponsePolicy
import com.agent.chat.domain.model.BehaviorFocus
import com.agent.chat.domain.model.BehaviorPlan
import com.agent.chat.domain.model.EmotionalIntensity
import com.agent.chat.domain.model.HumorLevel
import com.agent.chat.domain.model.InitiativeLevel
import com.agent.chat.domain.model.ResponseLengthTarget
import com.agent.chat.domain.model.ResponseTone

/**
 * 将 [BehaviorPlan] 映射为 Response Controller 阈值微调。
 */
object BehaviorPlanPolicies {

    fun adjustResponsePolicy(
        base: ModeResponsePolicy,
        plan: BehaviorPlan,
    ): ModeResponsePolicy {
        var policy = base

        policy = when (plan.responseTone) {
            ResponseTone.PROFESSIONAL -> policy.copy(
                dramaticCap = (policy.dramaticCap - 0.1f).coerceAtLeast(0.2f),
                monoCap = (policy.monoCap - 0.08f).coerceAtLeast(0.1f),
            )
            ResponseTone.CARING, ResponseTone.WARM -> policy.copy(
                humanFloor = (policy.humanFloor + 0.06f).coerceAtMost(0.9f),
                emotionFloor = (policy.emotionFloor - 0.06f).coerceAtLeast(0.18f),
            )
            ResponseTone.RESERVED -> policy.copy(
                dramaticCap = (policy.dramaticCap - 0.08f).coerceAtLeast(0.18f),
                charIdealMax = (policy.charIdealMax * 0.9f).toInt(),
            )
            ResponseTone.PLAYFUL -> policy.copy(
                dramaticCap = (policy.dramaticCap + 0.06f).coerceAtMost(0.8f),
            )
            ResponseTone.CASUAL -> policy
        }

        policy = when (plan.emotionalIntensity) {
            EmotionalIntensity.NEUTRAL -> policy.copy(
                emotionFloor = (policy.emotionFloor + 0.05f).coerceAtMost(0.5f),
            )
            EmotionalIntensity.SUPPORT -> policy.copy(
                humanFloor = (policy.humanFloor + 0.04f).coerceAtMost(0.88f),
                emotionFloor = (policy.emotionFloor - 0.05f).coerceAtLeast(0.2f),
            )
            EmotionalIntensity.WARM, EmotionalIntensity.EXPRESSIVE -> policy.copy(
                dramaticCap = (policy.dramaticCap + 0.05f).coerceAtMost(0.82f),
            )
        }

        policy = when (plan.humorLevel) {
            HumorLevel.LOW -> policy
            HumorLevel.HIGH -> policy.copy(
                dramaticCap = (policy.dramaticCap + 0.04f).coerceAtMost(0.85f),
            )
            HumorLevel.MEDIUM -> policy
        }

        policy = when (plan.responseLength) {
            ResponseLengthTarget.SHORT -> policy.copy(
                charIdealMax = (policy.charIdealMax * 0.75f).toInt().coerceAtLeast(80),
                idealSentenceMax = (policy.idealSentenceMax - 1).coerceAtLeast(2),
            )
            ResponseLengthTarget.LONG -> policy.copy(
                charIdealMax = (policy.charIdealMax * 1.2f).toInt(),
                idealSentenceMax = policy.idealSentenceMax + 2,
            )
            ResponseLengthTarget.MEDIUM -> policy
        }

        policy = when (plan.initiativeLevel) {
            InitiativeLevel.LOW -> policy
            InitiativeLevel.HIGH -> policy.copy(
                lengthFloor = (policy.lengthFloor - 0.04f).coerceAtLeast(0.25f),
            )
            InitiativeLevel.MEDIUM -> policy
        }

        policy = when (plan.focus) {
            BehaviorFocus.KNOWLEDGE -> policy.copy(
                dramaticCap = (policy.dramaticCap - 0.08f).coerceAtLeast(0.18f),
                charIdealMax = policy.charIdealMax + 40,
            )
            BehaviorFocus.EMOTIONAL_SUPPORT -> policy.copy(
                humanFloor = (policy.humanFloor + 0.05f).coerceAtMost(0.9f),
            )
            BehaviorFocus.ROLEPLAY -> policy.copy(
                monoCap = (policy.monoCap + 0.06f).coerceAtMost(0.65f),
            )
            BehaviorFocus.PLAYFUL, BehaviorFocus.GENERAL -> policy
        }

        return policy
    }
}
