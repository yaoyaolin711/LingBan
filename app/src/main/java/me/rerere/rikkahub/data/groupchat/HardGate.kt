package me.rerere.rikkahub.data.groupchat

import kotlin.uuid.Uuid

/**
 * Code-level floor gate. Must run before SoftScheduler and validate after it.
 * This layer alone is enough to prevent infinite AI↔AI loops.
 */
object HardGate {
    fun evaluate(context: GroupScheduleContext): HardGateVerdict {
        val floor = context.floor
        if (!floor.active) {
            return HardGateVerdict(
                allowScheduling = false,
                candidates = emptyList(),
                forcedEndReason = "paused",
            )
        }
        if (floor.speakersThisUserTurn >= context.policy.maxSpeakersPerUserTurn) {
            return HardGateVerdict(
                allowScheduling = false,
                candidates = emptyList(),
                forcedEndReason = "speaker_budget_exhausted",
            )
        }
        // AI→AI chain: only enforce depth after at least one assistant has spoken
        if (!context.afterUserMessage &&
            floor.speakersThisUserTurn > 0 &&
            floor.chainDepth >= context.policy.maxChainDepth
        ) {
            return HardGateVerdict(
                allowScheduling = false,
                candidates = emptyList(),
                forcedEndReason = "chain_depth_exhausted",
            )
        }

        val memberIds = context.members.map { it.assistantId }.toSet()
        val mentions = (context.latestMentions + floor.pendingMentions)
            .filter { it in memberIds }
            .distinct()

        var candidates = when {
            mentions.isNotEmpty() -> mentions
            context.mode == GroupChatMode.MENTION_FIRST && context.afterUserMessage ->
                context.members.map { it.assistantId }
            context.mode == GroupChatMode.MENTION_FIRST && !context.afterUserMessage ->
                // No free AI→AI chat in mention-first unless explicitly @'d
                emptyList()
            else -> context.members.map { it.assistantId }
        }

        // Cooldown: block last speaker unless they were explicitly @-mentioned this event
        val last = floor.lastSpeakerId
        if (last != null &&
            context.policy.perAssistantCooldownMessages > 0 &&
            last !in context.latestMentions
        ) {
            candidates = candidates.filter { it != last }
        }

        if (candidates.isEmpty()) {
            return HardGateVerdict(
                allowScheduling = false,
                candidates = emptyList(),
                forcedEndReason = "no_candidates",
            )
        }

        return HardGateVerdict(
            allowScheduling = true,
            candidates = candidates,
            forcedEndReason = null,
        )
    }

    /**
     * Validate SoftScheduler output. Illegal choices become end_round.
     */
    fun validateDecision(
        decision: SchedulerDecision,
        verdict: HardGateVerdict,
    ): SchedulerDecision {
        if (!verdict.allowScheduling) {
            return SchedulerDecision.endRound(verdict.forcedEndReason ?: "hard_gate")
        }
        if (decision.action != SchedulerAction.SPEAK) {
            return decision.copy(action = SchedulerAction.END_ROUND, speakerId = null)
        }
        val speaker = decision.speakerId
        if (speaker == null || speaker !in verdict.candidates) {
            return SchedulerDecision.endRound("invalid_speaker")
        }
        return decision.copy(action = SchedulerAction.SPEAK, speakerId = speaker)
    }

    fun afterSpeakerGranted(floor: FloorState, speakerId: Uuid, afterUserMessage: Boolean): FloorState {
        val nextDepth = if (afterUserMessage) {
            0
        } else {
            floor.chainDepth + 1
        }
        return floor.copy(
            chainDepth = nextDepth,
            speakersThisUserTurn = floor.speakersThisUserTurn + 1,
            lastSpeakerId = speakerId,
            pendingMentions = emptyList(),
            active = true,
        )
    }
}
