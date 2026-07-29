package com.agent.chat.data.persona

import com.agent.chat.domain.model.PersonaEmotion
import com.agent.chat.domain.model.PersonaIdentity
import com.agent.chat.domain.model.PersonaPersonality
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PersonaRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaValidatorTest {

    private val validator = PersonaValidator()

    @Test
    fun clampScoresTo_0_100() {
        val raw = PersonaProfile(
            identity = PersonaIdentity(name = "测", role = "companion", description = "测试"),
            personality = PersonaPersonality(
                warmth = 150,
                humor = -10,
                rationality = 50,
                empathy = 200,
                energy = 0,
            ),
            emotion = PersonaEmotion(
                expressionLevel = 999,
                dramaticLevel = -5,
            ),
            relationship = PersonaRelationship(intimacyLevel = 120),
        )
        val ok = validator.validateAndSanitize(raw) as PersonaValidationResult.Ok
        assertEquals(100, ok.profile.personality.warmth)
        assertEquals(0, ok.profile.personality.humor)
        assertEquals(100, ok.profile.personality.empathy)
        assertEquals(100, ok.profile.emotion.expressionLevel)
        assertEquals(0, ok.profile.emotion.dramaticLevel)
        assertEquals(100, ok.profile.relationship.intimacyLevel)
    }

    @Test
    fun runawayInput_lowersDramatic_andAddsConstraints() {
        val input = "我想要一个每天疯狂表达爱意，每句话都要哭的姐姐"
        val raw = PersonaProfile(
            identity = PersonaIdentity(
                name = "姐姐",
                role = "sister",
                description = "疯狂示爱",
            ),
            personality = PersonaPersonality(energy = 90),
            emotion = PersonaEmotion(
                expressionLevel = 95,
                dramaticLevel = 90,
                allowInnerMonologue = true,
            ),
            relationship = PersonaRelationship(intimacyLevel = 95),
        )
        val ok = validator.validateAndSanitize(raw, sourceText = input) as PersonaValidationResult.Ok
        assertTrue(ok.profile.emotion.dramaticLevel <= PersonaValidator.RUNAWAY_DRAMATIC_CAP)
        assertTrue(ok.profile.emotion.expressionLevel <= PersonaValidator.RUNAWAY_EXPRESSION_CAP)
        assertFalse(ok.profile.emotion.allowInnerMonologue)
        assertTrue(ok.profile.emotion.constraints.any { it.contains("哭") || it.contains("爱意") })
        assertTrue(ok.notes.isNotEmpty())
    }

    @Test
    fun illegalMinorContent_blocked() {
        val blocked = validator.checkInput("一个可爱的萝莉小学生角色")
        assertTrue(blocked is PersonaValidationResult.Blocked)
    }

    @Test
    fun gentleSister_keepsWarmDefaults() {
        val input = "我想要一个温柔、成熟、有幽默感的姐姐型AI"
        assertFalse(validator.looksRunaway(input))
        val raw = PersonaProfile(
            identity = PersonaIdentity(name = "姐姐", role = "sister", description = input),
            personality = PersonaPersonality(warmth = 80, humor = 70, empathy = 75),
            emotion = PersonaEmotion(dramaticLevel = 30, expressionLevel = 55),
        )
        val ok = validator.validateAndSanitize(raw, sourceText = input) as PersonaValidationResult.Ok
        assertEquals(80, ok.profile.personality.warmth)
        assertEquals(70, ok.profile.personality.humor)
        assertTrue(ok.profile.emotion.dramaticLevel <= PersonaValidator.HARD_DRAMATIC_CAP)
    }
}
