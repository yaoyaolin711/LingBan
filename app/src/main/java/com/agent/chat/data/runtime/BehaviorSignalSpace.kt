package com.agent.chat.data.runtime

/**
 * 连续信号空间：各维度以浮点累积，最终映射为离散 [BehaviorPlan]。
 * 避免固定 if-else 规则表，采用加权融合。
 */
internal data class BehaviorSignalSpace(
    val toneProfessional: Float = 0f,
    val toneCaring: Float = 0f,
    val toneCasual: Float = 0f,
    val tonePlayful: Float = 0f,
    val toneWarm: Float = 0f,
    val toneReserved: Float = 0f,
    val initiative: Float = 0.5f,
    val emotionNeutral: Float = 0f,
    val emotionSupport: Float = 0f,
    val emotionWarm: Float = 0f,
    val emotionExpressive: Float = 0f,
    val humor: Float = 0.5f,
    val lengthShort: Float = 0f,
    val lengthMedium: Float = 0f,
    val lengthLong: Float = 0f,
    val focusGeneral: Float = 0f,
    val focusKnowledge: Float = 0f,
    val focusEmotional: Float = 0f,
    val focusPlayful: Float = 0f,
    val focusRoleplay: Float = 0f,
) {
    fun addScaled(other: BehaviorSignalSpace, weight: Float): BehaviorSignalSpace {
        val w = weight.coerceIn(0f, 1f)
        return copy(
            toneProfessional = toneProfessional + other.toneProfessional * w,
            toneCaring = toneCaring + other.toneCaring * w,
            toneCasual = toneCasual + other.toneCasual * w,
            tonePlayful = tonePlayful + other.tonePlayful * w,
            toneWarm = toneWarm + other.toneWarm * w,
            toneReserved = toneReserved + other.toneReserved * w,
            initiative = initiative + (other.initiative - 0.5f) * w,
            emotionNeutral = emotionNeutral + other.emotionNeutral * w,
            emotionSupport = emotionSupport + other.emotionSupport * w,
            emotionWarm = emotionWarm + other.emotionWarm * w,
            emotionExpressive = emotionExpressive + other.emotionExpressive * w,
            humor = humor + (other.humor - 0.5f) * w,
            lengthShort = lengthShort + other.lengthShort * w,
            lengthMedium = lengthMedium + other.lengthMedium * w,
            lengthLong = lengthLong + other.lengthLong * w,
            focusGeneral = focusGeneral + other.focusGeneral * w,
            focusKnowledge = focusKnowledge + other.focusKnowledge * w,
            focusEmotional = focusEmotional + other.focusEmotional * w,
            focusPlayful = focusPlayful + other.focusPlayful * w,
            focusRoleplay = focusRoleplay + other.focusRoleplay * w,
        )
    }

    fun clamped(): BehaviorSignalSpace = copy(
        initiative = initiative.coerceIn(0f, 1f),
        humor = humor.coerceIn(0f, 1f),
    )
}

internal object BehaviorSignalContributors {

    fun fromPersona(input: RuntimeDecisionInput): BehaviorSignalSpace {
        val profile = input.persona?.profile ?: return BehaviorSignalSpace(
            toneCasual = 0.5f,
            focusGeneral = 0.5f,
            lengthMedium = 0.5f,
            emotionNeutral = 0.5f,
        )
        val p = profile.personality
        val c = profile.communication
        val e = profile.emotion

        val warmth = p.warmth / 100f
        val rationality = p.rationality / 100f
        val humor = p.humor / 100f
        val empathy = p.empathy / 100f
        val energy = p.energy / 100f
        val expression = e.expressionLevel / 100f

        val initiative = when (c.initiative) {
            com.agent.chat.domain.model.Initiative.PASSIVE -> 0.25f
            com.agent.chat.domain.model.Initiative.BALANCED -> 0.5f
            com.agent.chat.domain.model.Initiative.PROACTIVE -> 0.75f
        }

        val (lengthShort, lengthMedium, lengthLong) = when (c.sentenceLength) {
            com.agent.chat.domain.model.SentenceLength.SHORT -> Triple(0.7f, 0.25f, 0.05f)
            com.agent.chat.domain.model.SentenceLength.MEDIUM -> Triple(0.15f, 0.7f, 0.15f)
            com.agent.chat.domain.model.SentenceLength.LONG -> Triple(0.05f, 0.25f, 0.7f)
        }

        val (toneProfessional, toneCasual, toneReserved) = when (c.formality) {
            com.agent.chat.domain.model.Formality.FORMAL -> Triple(0.75f, 0.1f, 0.5f)
            com.agent.chat.domain.model.Formality.NEUTRAL -> Triple(0.4f, 0.4f, 0.3f)
            com.agent.chat.domain.model.Formality.CASUAL -> Triple(0.15f, 0.7f, 0.1f)
        }

        return BehaviorSignalSpace(
            toneProfessional = toneProfessional + rationality * 0.35f,
            toneCaring = warmth * 0.4f + empathy * 0.35f,
            toneCasual = toneCasual + (1f - rationality) * 0.15f,
            tonePlayful = humor * 0.5f + energy * 0.2f,
            toneWarm = warmth * 0.45f,
            toneReserved = toneReserved + rationality * 0.25f + (1f - warmth) * 0.15f,
            initiative = initiative,
            emotionNeutral = (1f - expression) * 0.4f + rationality * 0.2f,
            emotionSupport = empathy * 0.35f,
            emotionWarm = warmth * 0.3f,
            emotionExpressive = expression * 0.4f,
            humor = humor,
            lengthShort = lengthShort,
            lengthMedium = lengthMedium,
            lengthLong = lengthLong,
            focusGeneral = 0.5f,
        )
    }

    fun fromRelationship(input: RuntimeDecisionInput): BehaviorSignalSpace {
        val r = input.relationship
        val intimacy = r.intimacyLevel / 100f
        val affection = r.affectionLevel / 100f
        val initiative = r.initiativeLevel / 100f

        val styleSignals = when (r.interactionStyle) {
            com.agent.chat.domain.model.InteractionStyle.CASUAL -> BehaviorSignalSpace(toneCasual = 0.6f)
            com.agent.chat.domain.model.InteractionStyle.CARING -> BehaviorSignalSpace(
                toneCaring = 0.65f,
                emotionSupport = 0.4f,
            )
            com.agent.chat.domain.model.InteractionStyle.PLAYFUL -> BehaviorSignalSpace(
                tonePlayful = 0.6f,
                humor = 0.65f,
            )
            com.agent.chat.domain.model.InteractionStyle.SERIOUS -> BehaviorSignalSpace(
                toneProfessional = 0.5f,
                toneReserved = 0.35f,
            )
        }

        val typeSignals = when (r.relationshipType) {
            com.agent.chat.domain.model.RelationshipType.MENTOR -> BehaviorSignalSpace(
                toneProfessional = 0.55f,
                focusKnowledge = 0.35f,
            )
            com.agent.chat.domain.model.RelationshipType.ROMANTIC_PARTNER -> BehaviorSignalSpace(
                toneWarm = 0.5f,
                emotionWarm = 0.45f,
                emotionExpressive = 0.3f,
            )
            com.agent.chat.domain.model.RelationshipType.FAMILY -> BehaviorSignalSpace(
                toneCaring = 0.45f,
                emotionWarm = 0.35f,
            )
            com.agent.chat.domain.model.RelationshipType.ROLEPLAY -> BehaviorSignalSpace(
                focusRoleplay = 0.55f,
            )
            com.agent.chat.domain.model.RelationshipType.FRIEND -> BehaviorSignalSpace(toneCasual = 0.4f)
        }

        return styleSignals
            .addScaled(typeSignals, 0.6f)
            .addScaled(
                BehaviorSignalSpace(
                    toneWarm = intimacy * 0.35f,
                    emotionWarm = affection * 0.35f,
                    emotionExpressive = affection * 0.2f,
                    initiative = 0.25f + initiative * 0.5f,
                ),
                1f,
            )
    }

    fun fromExpression(input: RuntimeDecisionInput): BehaviorSignalSpace {
        val e = input.expression
        val humor = e.humorLevel / 100f
        val natural = e.naturalness / 100f
        val dramatic = e.dramaticLevel / 100f
        val poetic = e.poeticLevel / 100f

        val (lengthShort, lengthMedium, lengthLong) = when (e.sentenceLength) {
            com.agent.chat.domain.model.SentenceLength.SHORT -> Triple(0.75f, 0.2f, 0.05f)
            com.agent.chat.domain.model.SentenceLength.MEDIUM -> Triple(0.15f, 0.7f, 0.15f)
            com.agent.chat.domain.model.SentenceLength.LONG -> Triple(0.05f, 0.2f, 0.75f)
        }

        return BehaviorSignalSpace(
            toneCasual = natural * 0.5f,
            tonePlayful = humor * 0.35f,
            humor = humor,
            emotionExpressive = (dramatic + poetic) / 200f,
            lengthShort = lengthShort,
            lengthMedium = lengthMedium,
            lengthLong = lengthLong,
        )
    }

    fun fromConversationState(input: RuntimeDecisionInput): BehaviorSignalSpace {
        val state = input.conversationState
        if (!state.isActive()) {
            return BehaviorSignalSpace(focusGeneral = 0.3f)
        }
        val boost = state.confidence.coerceIn(0.35f, 1f)

        return when (state.currentState) {
            com.agent.chat.domain.model.ConversationStateKind.EMOTIONAL_SUPPORT -> BehaviorSignalSpace(
                toneCaring = 0.7f * boost,
                emotionSupport = 0.75f * boost,
                focusEmotional = 0.8f * boost,
                initiative = 0.45f,
            )
            com.agent.chat.domain.model.ConversationStateKind.KNOWLEDGE -> BehaviorSignalSpace(
                toneProfessional = 0.75f * boost,
                toneReserved = 0.3f * boost,
                emotionNeutral = 0.6f * boost,
                focusKnowledge = 0.85f * boost,
                lengthLong = 0.4f * boost,
            )
            com.agent.chat.domain.model.ConversationStateKind.PLAYFUL -> BehaviorSignalSpace(
                tonePlayful = 0.7f * boost,
                humor = 0.55f + 0.25f * boost,
                focusPlayful = 0.8f * boost,
            )
            com.agent.chat.domain.model.ConversationStateKind.ROLEPLAY -> BehaviorSignalSpace(
                focusRoleplay = 0.85f * boost,
                emotionExpressive = 0.35f * boost,
            )
            com.agent.chat.domain.model.ConversationStateKind.NORMAL -> BehaviorSignalSpace(
                focusGeneral = 0.4f,
            )
        }
    }

    fun fromMemories(input: RuntimeDecisionInput): BehaviorSignalSpace {
        if (input.memories.isEmpty()) return BehaviorSignalSpace()

        var support = 0f
        var playful = 0f
        var knowledge = 0f
        var preferenceHumor = 0f

        input.memories.forEach { memory ->
            when (memory.category) {
                com.agent.chat.domain.model.MemoryCategory.EMOTION -> support += 0.2f
                com.agent.chat.domain.model.MemoryCategory.PREFERENCE -> {
                    val text = memory.content.lowercase()
                    if ("幽默" in text || "搞笑" in text) preferenceHumor += 0.15f
                    if ("简短" in text || "简洁" in text) { /* length handled elsewhere */ }
                }
                com.agent.chat.domain.model.MemoryCategory.CORE -> knowledge += 0.05f
                com.agent.chat.domain.model.MemoryCategory.EVENT -> { /* neutral */ }
            }
        }

        val count = input.memories.size.coerceAtMost(5)
        val scale = (count / 5f).coerceIn(0.2f, 1f)

        return BehaviorSignalSpace(
            emotionSupport = support * scale,
            tonePlayful = playful,
            focusKnowledge = knowledge * scale,
            humor = 0.5f + preferenceHumor * scale,
        )
    }
}

internal object BehaviorPlanMapper {

    fun toPlan(space: BehaviorSignalSpace): com.agent.chat.domain.model.BehaviorPlan {
        val s = space.clamped()

        val tone = listOf(
            com.agent.chat.domain.model.ResponseTone.PROFESSIONAL to s.toneProfessional,
            com.agent.chat.domain.model.ResponseTone.CARING to s.toneCaring,
            com.agent.chat.domain.model.ResponseTone.CASUAL to s.toneCasual,
            com.agent.chat.domain.model.ResponseTone.PLAYFUL to s.tonePlayful,
            com.agent.chat.domain.model.ResponseTone.WARM to s.toneWarm,
            com.agent.chat.domain.model.ResponseTone.RESERVED to s.toneReserved,
        ).maxBy { it.second }.first

        val initiative = when {
            s.initiative < 0.35f -> com.agent.chat.domain.model.InitiativeLevel.LOW
            s.initiative > 0.65f -> com.agent.chat.domain.model.InitiativeLevel.HIGH
            else -> com.agent.chat.domain.model.InitiativeLevel.MEDIUM
        }

        val emotion = listOf(
            com.agent.chat.domain.model.EmotionalIntensity.NEUTRAL to s.emotionNeutral,
            com.agent.chat.domain.model.EmotionalIntensity.SUPPORT to s.emotionSupport,
            com.agent.chat.domain.model.EmotionalIntensity.WARM to s.emotionWarm,
            com.agent.chat.domain.model.EmotionalIntensity.EXPRESSIVE to s.emotionExpressive,
        ).maxBy { it.second }.first

        val humor = when {
            s.humor < 0.35f -> com.agent.chat.domain.model.HumorLevel.LOW
            s.humor > 0.65f -> com.agent.chat.domain.model.HumorLevel.HIGH
            else -> com.agent.chat.domain.model.HumorLevel.MEDIUM
        }

        val length = listOf(
            com.agent.chat.domain.model.ResponseLengthTarget.SHORT to s.lengthShort,
            com.agent.chat.domain.model.ResponseLengthTarget.MEDIUM to s.lengthMedium,
            com.agent.chat.domain.model.ResponseLengthTarget.LONG to s.lengthLong,
        ).maxBy { it.second }.first

        val focus = listOf(
            com.agent.chat.domain.model.BehaviorFocus.GENERAL to s.focusGeneral,
            com.agent.chat.domain.model.BehaviorFocus.KNOWLEDGE to s.focusKnowledge,
            com.agent.chat.domain.model.BehaviorFocus.EMOTIONAL_SUPPORT to s.focusEmotional,
            com.agent.chat.domain.model.BehaviorFocus.PLAYFUL to s.focusPlayful,
            com.agent.chat.domain.model.BehaviorFocus.ROLEPLAY to s.focusRoleplay,
        ).maxBy { it.second }.first

        return com.agent.chat.domain.model.BehaviorPlan(
            responseTone = tone,
            initiativeLevel = initiative,
            emotionalIntensity = emotion,
            humorLevel = humor,
            responseLength = length,
            focus = focus,
        )
    }
}
