package com.agent.chat.data.interaction

import com.agent.chat.domain.model.ConversationStateKind
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.Formality
import com.agent.chat.domain.model.Initiative
import com.agent.chat.domain.model.InteractionPreference
import com.agent.chat.domain.model.InteractionTendencyWeights
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.PersonaCommunication
import com.agent.chat.domain.model.PersonaEmotion
import com.agent.chat.domain.model.PersonaIdentity
import com.agent.chat.domain.model.PersonaPersonality
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PersonaRelationship
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionIntentDetectorTest {

    @Test
    fun technicalQuestion_detected() {
        val intents = InteractionIntentDetector.detect("Kotlin 里怎么实现 Retrofit 接口？")
        assertTrue(intents.contains(InteractionIntent.TECHNICAL))
        assertEquals(InteractionIntent.TECHNICAL, InteractionIntentDetector.primaryIntent(intents))
    }

    @Test
    fun romanticQuestion_detected() {
        val intents = InteractionIntentDetector.detect("你喜欢我吗")
        assertTrue(intents.contains(InteractionIntent.ROMANTIC))
    }

    @Test
    fun generalChat_noSpecialIntent() {
        val intents = InteractionIntentDetector.detect("今天天气不错")
        assertTrue(intents.contains(InteractionIntent.GENERAL))
    }
}

class InteractionPreferenceWeightEngineTest {

    private val engine = InteractionPreferenceWeightEngine()

    private fun romanticEnabled() = InteractionPreference(romanticConversation = true)

    @Test
    fun romanticEnabled_generalChat_lowWeightNotActive() {
        val weights = engine.resolve(
            InteractionTendencyContext(
                preference = romanticEnabled(),
                userMessage = "你好，今天天气不错",
            ),
        )
        assertTrue(weights.romantic < InteractionTendencyWeights.ACTIVE_THRESHOLD)
        assertFalse(weights.romanticActive())
    }

    @Test
    fun romanticEnabled_userInitiates_highWeightActive() {
        val weights = engine.resolve(
            InteractionTendencyContext(
                preference = romanticEnabled(),
                userMessage = "你喜欢我吗",
                relationship = RelationshipProfile(
                    relationshipType = RelationshipType.ROMANTIC_PARTNER,
                    intimacyLevel = 80,
                    affectionLevel = 75,
                ),
            ),
        )
        assertTrue(weights.romantic >= InteractionTendencyWeights.ACTIVE_THRESHOLD)
        assertTrue(weights.romanticActive())
    }

    @Test
    fun romanticDisabled_userInitiates_zeroWeight() {
        val weights = engine.resolve(
            InteractionTendencyContext(
                preference = InteractionPreference(),
                userMessage = "你喜欢我吗",
            ),
        )
        assertEquals(0f, weights.romantic, 0.001f)
    }

    @Test
    fun technicalMessage_allWeightsZero() {
        val weights = engine.resolve(
            InteractionTendencyContext(
                preference = InteractionPreference(
                    romanticConversation = true,
                    flirting = true,
                    intimateConversation = true,
                    roleplay = true,
                ),
                userMessage = "这段 Kotlin 代码为什么报错？",
            ),
        )
        assertEquals(InteractionTendencyWeights.ZERO, weights)
    }

    @Test
    fun coldPersona_lowersRomanticEvenWhenEnabled() {
        val coldPersona = Persona(
            id = "p1",
            name = "阿冷",
            systemPrompt = "高冷程序员",
            profile = PersonaProfile(
                identity = PersonaIdentity(name = "阿冷"),
                personality = PersonaPersonality(warmth = 20, empathy = 25, rationality = 90),
                communication = PersonaCommunication(
                    formality = Formality.NEUTRAL,
                    initiative = Initiative.PASSIVE,
                ),
                emotion = PersonaEmotion(expressionLevel = 25),
                relationship = PersonaRelationship(),
            ),
        )
        val warmPersona = coldPersona.copy(
            profile = coldPersona.profile!!.copy(
                personality = PersonaPersonality(warmth = 85, empathy = 80),
            ),
        )
        val base = InteractionTendencyContext(
            preference = romanticEnabled(),
            userMessage = "你喜欢我吗",
        )
        val cold = engine.resolve(base.copy(persona = coldPersona)).romantic
        val warm = engine.resolve(base.copy(persona = warmPersona)).romantic
        assertTrue(cold < warm)
    }

    @Test
    fun emotionalSupportState_boostsRomanticWhenTopicMatches() {
        val withoutState = engine.resolve(
            InteractionTendencyContext(
                preference = romanticEnabled(),
                userMessage = "最近有点想你",
            ),
        )
        val withState = engine.resolve(
            InteractionTendencyContext(
                preference = romanticEnabled(),
                userMessage = "最近有点想你",
                conversationState = ConversationStateSnapshot(
                    currentState = ConversationStateKind.EMOTIONAL_SUPPORT,
                    confidence = 0.8f,
                    trigger = "emotion:lonely",
                ),
            ),
        )
        assertTrue(withState.romantic >= withoutState.romantic)
    }
}

class InteractionPreferenceEngineTest {

    private val engine = InteractionPreferenceEngine(InteractionPreferenceWeightEngine())

    @Test
    fun technicalMessage_promptSaysWeightsZero() {
        val section = engine.buildPromptSection(
            InteractionTendencyContext(
                preference = InteractionPreference(romanticConversation = true),
                userMessage = "这段代码为什么报错？",
            ),
        )
        assertTrue(section.contains("倾向权重全部归零") || section.contains("romantic: 0.00"))
        assertTrue(section.contains("行为倾向"))
    }

    @Test
    fun romanticEnabled_generalChat_promptSaysNotActive() {
        val section = engine.buildPromptSection(
            InteractionTendencyContext(
                preference = InteractionPreference(romanticConversation = true),
                userMessage = "你好",
            ),
        )
        assertTrue(section.contains("允许（非强制）"))
        assertTrue(section.contains("倾向权重偏低") || section.contains("保持普通"))
    }

    @Test
    fun romanticDisabled_userRomantic_promptSaysBoundary() {
        val section = engine.buildPromptSection(
            InteractionTendencyContext(
                preference = InteractionPreference(),
                userMessage = "你喜欢我吗",
            ),
        )
        assertTrue(section.contains("未授权") || section.contains("保持边界"))
    }

    @Test
    fun romanticEnabled_userRomantic_promptShowsActivation() {
        val section = engine.buildPromptSection(
            InteractionTendencyContext(
                preference = InteractionPreference(romanticConversation = true),
                userMessage = "你喜欢我吗",
                relationship = RelationshipProfile(
                    relationshipType = RelationshipType.ROMANTIC_PARTNER,
                    affectionLevel = 80,
                ),
            ),
        )
        assertTrue(section.contains("激活") || section.contains("浪漫倾向"))
        assertTrue(section.contains("romantic:"))
    }

    @Test
    fun promptUsesTendencyNotForcedRules() {
        val section = engine.buildPromptSection(
            InteractionTendencyContext(
                preference = InteractionPreference(romanticConversation = true),
                userMessage = "你好",
            ),
        )
        assertTrue(section.contains("不是每句话都要用"))
        assertFalse(section.contains("可以真诚回应好感") && section.contains("你好"))
    }
}
