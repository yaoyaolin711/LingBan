package com.agent.chat.data.interaction

import com.agent.chat.data.conversationstate.ConversationEmotionDetector
import com.agent.chat.data.conversationstate.EmotionSignal

import com.agent.chat.domain.model.ConversationStateKind
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.InteractionPreference
import com.agent.chat.domain.model.InteractionTendencyWeights
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 计算互动倾向权重的输入上下文。
 */
data class InteractionTendencyContext(
    val preference: InteractionPreference,
    val userMessage: String,
    val relationship: RelationshipProfile = RelationshipProfile(),
    val persona: Persona? = null,
    val conversationState: ConversationStateSnapshot = ConversationStateSnapshot.DEFAULT,
)

/**
 * 将 [InteractionPreference] 从「强制规则」转为「行为倾向权重」。
 *
 * 权重公式（单维度）：
 * ```
 * weight = permissionCeiling × (topic×0.50 + emotion×0.15 + relationship×0.20 + persona×0.15)
 * ```
 *
 * - **permissionCeiling**：开关关 = 0；开关开 = 1（允许上限，非强制激活）
 * - **topic**：用户本轮是否涉及该话题（主驱动）
 * - **emotion**：情绪信号（仅在话题相关时放大）
 * - **relationship**：亲密度/情感表达（放大表达程度，不单独触发）
 * - **persona**：人设自然倾向（最高尊重 Persona 底色）
 *
 * 技术类话题：全部倾向归零。
 */
@Singleton
class InteractionPreferenceWeightEngine @Inject constructor() {

    fun resolve(context: InteractionTendencyContext): InteractionTendencyWeights {
        val intents = InteractionIntentDetector.detect(context.userMessage)
        if (InteractionIntent.TECHNICAL in intents) {
            return InteractionTendencyWeights.ZERO
        }

        val emotion = ConversationEmotionDetector.detect(context.userMessage)
        val profile = context.persona?.profile

        return InteractionTendencyWeights(
            romantic = computeDimension(
                enabled = context.preference.romanticConversation,
                intentHit = InteractionIntent.ROMANTIC in intents,
                intentBase = 0.55f,
                idleBaseline = 0.06f,
                emotion = emotion,
                relationship = context.relationship,
                personaWarmth = personaWarmth(profile),
                personaEmpathy = personaEmpathy(profile),
                stateKind = context.conversationState.currentState,
                stateConfidence = context.conversationState.confidence,
                relationshipTypes = setOf(RelationshipType.ROMANTIC_PARTNER, RelationshipType.FAMILY),
            ),
            flirting = computeDimension(
                enabled = context.preference.flirting,
                intentHit = InteractionIntent.FLIRTING in intents,
                intentBase = 0.58f,
                idleBaseline = 0.05f,
                emotion = emotion,
                relationship = context.relationship,
                personaWarmth = personaWarmth(profile),
                personaEmpathy = personaEmpathy(profile),
                stateKind = context.conversationState.currentState,
                stateConfidence = context.conversationState.confidence,
                relationshipTypes = setOf(RelationshipType.ROMANTIC_PARTNER, RelationshipType.FRIEND),
                playfulPersonaBoost = personaHumor(profile),
            ),
            intimate = computeDimension(
                enabled = context.preference.intimateConversation,
                intentHit = InteractionIntent.INTIMATE in intents,
                intentBase = 0.6f,
                idleBaseline = 0.04f,
                emotion = emotion,
                relationship = context.relationship,
                personaWarmth = personaWarmth(profile),
                personaEmpathy = personaEmpathy(profile),
                stateKind = context.conversationState.currentState,
                stateConfidence = context.conversationState.confidence,
                relationshipTypes = setOf(RelationshipType.ROMANTIC_PARTNER),
            ),
            roleplay = computeDimension(
                enabled = context.preference.roleplay,
                intentHit = InteractionIntent.ROLEPLAY in intents,
                intentBase = 0.62f,
                idleBaseline = 0.05f,
                emotion = emotion,
                relationship = context.relationship,
                personaWarmth = personaWarmth(profile),
                personaEmpathy = personaEmpathy(profile),
                stateKind = context.conversationState.currentState,
                stateConfidence = context.conversationState.confidence,
                relationshipTypes = setOf(RelationshipType.ROLEPLAY),
                stateKinds = setOf(ConversationStateKind.ROLEPLAY),
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun computeDimension(
        enabled: Boolean,
        intentHit: Boolean,
        intentBase: Float,
        idleBaseline: Float,
        emotion: EmotionSignal,
        relationship: RelationshipProfile,
        personaWarmth: Float,
        personaEmpathy: Float,
        stateKind: ConversationStateKind,
        stateConfidence: Float,
        relationshipTypes: Set<RelationshipType>,
        playfulPersonaBoost: Float = 0f,
        stateKinds: Set<ConversationStateKind> = emptySet(),
    ): Float {
        if (!enabled) return 0f

        val topic = if (intentHit) intentBase else idleBaseline

        val emotionFactor = if (intentHit || emotion.score >= 0.35f) {
            emotion.score * WEIGHT_EMOTION
        } else {
            0f
        }

        val intimacy = relationship.intimacyLevel / 100f
        val affection = relationship.affectionLevel / 100f
        val relAffinity = if (relationship.relationshipType in relationshipTypes) 0.12f else 0f
        val relationshipFactor = (intimacy * 0.6f + affection * 0.4f + relAffinity) * WEIGHT_RELATIONSHIP
        val relScaled = if (intentHit) relationshipFactor else relationshipFactor * 0.35f

        val personaBase = (personaWarmth * 0.55f + personaEmpathy * 0.45f) / 100f
        val personaFactor = (personaBase + playfulPersonaBoost) * WEIGHT_PERSONA
        val personaScaled = if (intentHit) personaFactor else personaFactor * 0.4f

        var stateFactor = 0f
        if (stateKind in stateKinds && stateConfidence >= 0.35f) {
            stateFactor = stateConfidence * 0.12f
        }

        val activation = (topic * WEIGHT_TOPIC + emotionFactor + relScaled + personaScaled + stateFactor)
            .coerceIn(0f, 1f)

        return activation
    }

    private fun personaWarmth(profile: com.agent.chat.domain.model.PersonaProfile?): Float =
        profile?.personality?.warmth?.toFloat() ?: 50f

    private fun personaEmpathy(profile: com.agent.chat.domain.model.PersonaProfile?): Float =
        profile?.personality?.empathy?.toFloat() ?: 50f

    private fun personaHumor(profile: com.agent.chat.domain.model.PersonaProfile?): Float =
        (profile?.personality?.humor?.toFloat() ?: 40f) / 100f * 0.08f

    companion object {
        private const val WEIGHT_TOPIC = 0.50f
        private const val WEIGHT_EMOTION = 0.15f
        private const val WEIGHT_RELATIONSHIP = 0.20f
        private const val WEIGHT_PERSONA = 0.15f
    }
}
