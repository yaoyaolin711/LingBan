package com.agent.chat.data.relationship

import com.agent.chat.data.mode.ModePolicies
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipPoliciesTest {

    @Test
    fun romanticPartner_allowsMoreEmotionThanFriend() {
        val base = ModePolicies.response(LingBanChatMode.COMPANION)
        val friend = RelationshipPolicies.adjustResponsePolicy(
            base,
            RelationshipProfile(relationshipType = RelationshipType.FRIEND),
            LingBanChatMode.COMPANION,
        )
        val romantic = RelationshipPolicies.adjustResponsePolicy(
            base,
            RelationshipProfile(
                relationshipType = RelationshipType.ROMANTIC_PARTNER,
                intimacyLevel = 85,
                affectionLevel = 80,
            ),
            LingBanChatMode.COMPANION,
        )
        assertTrue(romantic.dramaticCap >= friend.dramaticCap)
        assertTrue(romantic.emotionFloor <= friend.emotionFloor)
    }

    @Test
    fun roleplayRelationship_relaxesLengthPolicy() {
        val base = ModePolicies.response(LingBanChatMode.COMPANION)
        val adjusted = RelationshipPolicies.adjustResponsePolicy(
            base,
            RelationshipProfile(
                relationshipType = RelationshipType.ROLEPLAY,
                intimacyLevel = 80,
            ),
            LingBanChatMode.COMPANION,
        )
        assertTrue(adjusted.charIdealMax > base.charIdealMax)
        assertTrue(adjusted.lengthFloor <= base.lengthFloor)
    }

    @Test
    fun mentor_keepsLowDramaCap() {
        val base = ModePolicies.response(LingBanChatMode.COMPANION)
        val mentor = RelationshipPolicies.adjustResponsePolicy(
            base,
            RelationshipProfile(relationshipType = RelationshipType.MENTOR),
            LingBanChatMode.COMPANION,
        )
        assertTrue(mentor.dramaticCap <= 0.38f)
    }
}
