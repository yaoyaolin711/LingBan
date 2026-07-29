package com.agent.chat.data.expression

import com.agent.chat.domain.model.InteractionStyle
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionDefaultsTest {

    @Test
    fun romanticPartner_recommendsNatural90_dramatic30() {
        val profile = ExpressionDefaults.recommend(
            RelationshipProfile(relationshipType = RelationshipType.ROMANTIC_PARTNER),
        )
        assertEquals(90, profile.naturalness)
        assertEquals(30, profile.dramaticLevel)
    }

    @Test
    fun mentor_recommendsNatural90_dramatic5() {
        val profile = ExpressionDefaults.recommend(
            RelationshipProfile(relationshipType = RelationshipType.MENTOR),
        )
        assertEquals(90, profile.naturalness)
        assertEquals(5, profile.dramaticLevel)
    }

    @Test
    fun playfulStyle_increasesHumor() {
        val base = ExpressionDefaults.recommend(
            RelationshipProfile(
                relationshipType = RelationshipType.FRIEND,
                interactionStyle = InteractionStyle.CASUAL,
            ),
        )
        val playful = ExpressionDefaults.recommend(
            RelationshipProfile(
                relationshipType = RelationshipType.FRIEND,
                interactionStyle = InteractionStyle.PLAYFUL,
            ),
        )
        assertTrue(playful.humorLevel > base.humorLevel)
    }
}

class ExpressionPoliciesTest {

    @Test
    fun lowPoetic_capIsTight() {
        val policy = ExpressionPolicies.fromProfile(
            com.agent.chat.domain.model.ExpressionProfile(
                naturalness = 90,
                dramaticLevel = 20,
                poeticLevel = 10,
            ),
        )
        assertTrue(policy.poeticCap <= 0.25f)
        assertTrue(policy.dramaticCap <= 0.35f)
    }

    @Test
    fun highRoleplayStyle_relaxesCaps() {
        val low = ExpressionPolicies.fromProfile(
            com.agent.chat.domain.model.ExpressionProfile(poeticLevel = 10, dramaticLevel = 10),
        )
        val high = ExpressionPolicies.fromProfile(
            com.agent.chat.domain.model.ExpressionProfile(poeticLevel = 60, dramaticLevel = 55),
        )
        assertTrue(high.poeticCap > low.poeticCap)
        assertTrue(high.dramaticCap > low.dramaticCap)
    }
}
