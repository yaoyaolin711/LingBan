package com.agent.chat.data.runtime

import com.agent.chat.data.interaction.InteractionPreferenceWeightEngine
import com.agent.chat.domain.model.BehaviorFocus
import com.agent.chat.domain.model.ConversationStateKind
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.EmotionalIntensity
import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.Formality
import com.agent.chat.domain.model.Initiative
import com.agent.chat.domain.model.InteractionPreference
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.PersonaCommunication
import com.agent.chat.domain.model.PersonaEmotion
import com.agent.chat.domain.model.PersonaIdentity
import com.agent.chat.domain.model.PersonaPersonality
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PersonaRelationship
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.ResponseTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeDecisionEngineTest {

    private val engine = RuntimeDecisionEngine(
        BehaviorPlanRenderer(),
        InteractionPreferenceWeightEngine(),
    )

    private fun coldProgrammerPersona() = Persona(
        id = "p1",
        name = "阿冷",
        systemPrompt = "高冷程序员",
        profile = PersonaProfile(
            identity = PersonaIdentity(name = "阿冷", role = "程序员"),
            personality = PersonaPersonality(
                warmth = 25,
                humor = 20,
                rationality = 85,
                empathy = 35,
                energy = 40,
            ),
            communication = PersonaCommunication(
                formality = Formality.NEUTRAL,
                initiative = Initiative.PASSIVE,
            ),
            emotion = PersonaEmotion(expressionLevel = 30, dramaticLevel = 10),
            relationship = PersonaRelationship(),
        ),
    )

    @Test
    fun workStress_prefersCaringButStaysReserved() {
        val result = engine.decide(
            RuntimeDecisionInput(
                persona = coldProgrammerPersona(),
                conversationState = ConversationStateSnapshot(
                    currentState = ConversationStateKind.EMOTIONAL_SUPPORT,
                    confidence = 0.75f,
                    trigger = "emotion:stress",
                ),
                userMessage = "最近工作压力好大",
            ),
        )
        assertEquals(EmotionalIntensity.SUPPORT, result.plan.emotionalIntensity)
        assertTrue(
            result.plan.responseTone == ResponseTone.CARING ||
                result.plan.responseTone == ResponseTone.RESERVED ||
                result.plan.responseTone == ResponseTone.CASUAL,
        )
        assertEquals(BehaviorFocus.EMOTIONAL_SUPPORT, result.plan.focus)
    }

    @Test
    fun technicalQuestion_prefersProfessionalKnowledge() {
        val result = engine.decide(
            RuntimeDecisionInput(
                persona = coldProgrammerPersona(),
                conversationState = ConversationStateSnapshot(
                    currentState = ConversationStateKind.KNOWLEDGE,
                    confidence = 0.85f,
                    trigger = "intent:technical",
                ),
                userMessage = "Kotlin 协程怎么取消？",
            ),
        )
        assertEquals(ResponseTone.PROFESSIONAL, result.plan.responseTone)
        assertEquals(BehaviorFocus.KNOWLEDGE, result.plan.focus)
        assertEquals(EmotionalIntensity.NEUTRAL, result.plan.emotionalIntensity)
    }

    @Test
    fun personaWeightDominatesOverPlayfulState() {
        val playfulState = engine.decide(
            RuntimeDecisionInput(
                persona = coldProgrammerPersona(),
                expression = ExpressionProfile(humorLevel = 20),
                conversationState = ConversationStateSnapshot(
                    currentState = ConversationStateKind.PLAYFUL,
                    confidence = 0.8f,
                    trigger = "intent:playful",
                ),
                userMessage = "哈哈哈",
            ),
        )
        assertNotEquals(
            "高冷程序员不应因轻松状态变成高幽默",
            com.agent.chat.domain.model.HumorLevel.HIGH,
            playfulState.plan.humorLevel,
        )
    }

    @Test
    fun interactionPreference_capsExpressiveEmotion() {
        val result = engine.decide(
            RuntimeDecisionInput(
                persona = coldProgrammerPersona(),
                relationship = RelationshipProfile(intimacyLevel = 90, affectionLevel = 90),
                interactionPreference = InteractionPreference(),
                conversationState = ConversationStateSnapshot(
                    currentState = ConversationStateKind.EMOTIONAL_SUPPORT,
                    confidence = 0.8f,
                    trigger = "emotion:sad",
                ),
                userMessage = "好难过",
            ),
        )
        assertNotEquals(EmotionalIntensity.EXPRESSIVE, result.plan.emotionalIntensity)
    }

    @Test
    fun promptSection_isSingleBehaviorPlan_notStackedRules() {
        val plan = engine.decide(
            RuntimeDecisionInput(
                persona = coldProgrammerPersona(),
                conversationState = ConversationStateSnapshot(
                    currentState = ConversationStateKind.EMOTIONAL_SUPPORT,
                    confidence = 0.7f,
                    trigger = "emotion:stress",
                ),
                userMessage = "压力大",
            ),
        ).plan
        val section = engine.buildPromptSection(plan, "阿冷")
        assertTrue(section.contains("Runtime Behavior Plan"))
        assertTrue(section.contains("tone:"))
        assertTrue(section.contains("Persona 身份与人设不变"))
    }
}

class BehaviorPlanPoliciesTest {

    @Test
    fun knowledgeFocus_increasesCharIdealMax() {
        val base = com.agent.chat.data.mode.ModePolicies.response(LingBanChatMode.ASSISTANT)
        val adjusted = BehaviorPlanPolicies.adjustResponsePolicy(
            base,
            com.agent.chat.domain.model.BehaviorPlan(
                focus = BehaviorFocus.KNOWLEDGE,
            ),
        )
        assertTrue(adjusted.charIdealMax >= base.charIdealMax)
    }
}
