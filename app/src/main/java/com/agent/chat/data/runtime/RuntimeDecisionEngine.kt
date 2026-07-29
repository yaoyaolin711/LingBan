package com.agent.chat.data.runtime

import com.agent.chat.data.interaction.InteractionPreferenceWeightEngine
import com.agent.chat.data.interaction.InteractionTendencyContext
import com.agent.chat.data.interaction.InteractionTendencyContributor
import com.agent.chat.domain.model.BehaviorPlan
import com.agent.chat.domain.model.RuntimeDecisionResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime Decision Layer：在生成回复前，根据结构化上下文加权融合出 [BehaviorPlan]。
 *
 * 权重（Persona 最高）：
 * - Persona 0.40
 * - ConversationState 0.18
 * - Relationship 0.15
 * - Expression 0.12
 * - Memory 0.10
 * - InteractionPreference：倾向权重叠加（非强制裁剪）
 */
@Singleton
class RuntimeDecisionEngine @Inject constructor(
    private val renderer: BehaviorPlanRenderer,
    private val interactionWeightEngine: InteractionPreferenceWeightEngine,
) {

    fun decide(input: RuntimeDecisionInput): RuntimeDecisionResult {
        val weights = mapOf(
            SOURCE_PERSONA to WEIGHT_PERSONA,
            SOURCE_STATE to WEIGHT_STATE,
            SOURCE_RELATIONSHIP to WEIGHT_RELATIONSHIP,
            SOURCE_EXPRESSION to WEIGHT_EXPRESSION,
            SOURCE_MEMORY to WEIGHT_MEMORY,
        )

        var blended = BehaviorSignalSpace()
        blended = blended.addScaled(BehaviorSignalContributors.fromPersona(input), WEIGHT_PERSONA)
        blended = blended.addScaled(BehaviorSignalContributors.fromConversationState(input), WEIGHT_STATE)
        blended = blended.addScaled(BehaviorSignalContributors.fromRelationship(input), WEIGHT_RELATIONSHIP)
        blended = blended.addScaled(BehaviorSignalContributors.fromExpression(input), WEIGHT_EXPRESSION)
        blended = blended.addScaled(BehaviorSignalContributors.fromMemories(input), WEIGHT_MEMORY)

        val tendencies = interactionWeightEngine.resolve(
            InteractionTendencyContext(
                preference = input.interactionPreference,
                userMessage = input.userMessage,
                relationship = input.relationship,
                persona = input.persona,
                conversationState = input.conversationState,
            ),
        )
        blended = InteractionTendencyContributor.apply(blended, tendencies)

        val plan = BehaviorPlanMapper.toPlan(blended)
        val confidence = computeConfidence(input, blended)

        return RuntimeDecisionResult(
            plan = plan,
            confidence = confidence,
            sourceWeights = weights,
        )
    }

    fun buildPromptSection(
        plan: BehaviorPlan,
        personaName: String?,
    ): String = renderer.render(plan, personaName)

    private fun computeConfidence(input: RuntimeDecisionInput, space: BehaviorSignalSpace): Float {
        var confidence = 0.55f
        if (input.persona != null) confidence += 0.15f
        if (input.conversationState.isActive()) {
            confidence += 0.15f * input.conversationState.confidence
        }
        if (input.memories.isNotEmpty()) confidence += 0.05f

        val focusSpread = listOf(
            space.focusGeneral,
            space.focusKnowledge,
            space.focusEmotional,
            space.focusPlayful,
            space.focusRoleplay,
        ).sortedDescending()
        if (focusSpread.size >= 2) {
            val gap = focusSpread[0] - focusSpread[1]
            confidence += (gap * 0.15f).coerceAtMost(0.15f)
        }

        return confidence.coerceIn(0.4f, 1f)
    }

    companion object {
        const val SOURCE_PERSONA = "persona"
        const val SOURCE_STATE = "conversation_state"
        const val SOURCE_RELATIONSHIP = "relationship"
        const val SOURCE_EXPRESSION = "expression"
        const val SOURCE_MEMORY = "memory"

        private const val WEIGHT_PERSONA = 0.40f
        private const val WEIGHT_STATE = 0.18f
        private const val WEIGHT_RELATIONSHIP = 0.15f
        private const val WEIGHT_EXPRESSION = 0.12f
        private const val WEIGHT_MEMORY = 0.10f
    }
}
