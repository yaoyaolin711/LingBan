package com.agent.chat.data.relationship

import com.agent.chat.domain.model.InteractionStyle
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipProfileCodecTest {

    private val codec = RelationshipProfileCodec()

    @Test
    fun encodeDecode_roundTrip() {
        val original = RelationshipProfile(
            relationshipType = RelationshipType.ROMANTIC_PARTNER,
            intimacyLevel = 88,
            affectionLevel = 72,
            interactionStyle = InteractionStyle.CARING,
            initiativeLevel = 55,
        )
        val decoded = codec.decode(codec.encode(original))
        assertEquals(RelationshipType.ROMANTIC_PARTNER, decoded.relationshipType)
        assertEquals(88, decoded.intimacyLevel)
        assertEquals(72, decoded.affectionLevel)
        assertEquals(InteractionStyle.CARING, decoded.interactionStyle)
        assertEquals(55, decoded.initiativeLevel)
    }

    @Test
    fun decode_blank_returnsDefault() {
        val decoded = codec.decode("")
        assertEquals(RelationshipType.FRIEND, decoded.relationshipType)
        assertEquals(50, decoded.intimacyLevel)
    }

    @Test
    fun clamp_outOfRangeScores() {
        val decoded = codec.decode(
            """{"relationshipType":"mentor","intimacyLevel":150,"affectionLevel":-5,"interactionStyle":"serious","initiativeLevel":999}""",
        )
        assertEquals(100, decoded.intimacyLevel)
        assertEquals(0, decoded.affectionLevel)
        assertEquals(100, decoded.initiativeLevel)
    }
}
