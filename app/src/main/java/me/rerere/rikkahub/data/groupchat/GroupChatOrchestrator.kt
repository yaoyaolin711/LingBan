package me.rerere.rikkahub.data.groupchat

import kotlin.uuid.Uuid

/**
 * Pure orchestration loop: HardGate → SoftScheduler → grant → (caller generates) → repeat.
 * Generation is injected so unit tests do not need GenerationHandler.
 */
class GroupChatOrchestrator(
    private val softScheduler: SoftScheduler,
    private val policy: HardGatePolicy = HardGatePolicy.DEFAULT,
) {
    data class StepResult(
        val decision: SchedulerDecision,
        val floor: FloorState,
        /** Non-null when a speaker was granted and generation should run. */
        val grantedSpeakerId: Uuid? = null,
        val done: Boolean = false,
    )

    /**
     * Decide the next speaker (or end). Does not mutate external state; returns new floor.
     */
    suspend fun nextStep(
        mode: GroupChatMode,
        members: List<GroupMember>,
        floor: FloorState,
        latestMentions: List<Uuid>,
        afterUserMessage: Boolean,
        transcript: List<GroupTranscriptLine>,
    ): StepResult {
        val context = GroupScheduleContext(
            mode = mode,
            members = members,
            floor = floor,
            policy = policy,
            latestMentions = latestMentions,
            afterUserMessage = afterUserMessage,
        )
        val verdict = HardGate.evaluate(context)
        if (!verdict.allowScheduling) {
            return StepResult(
                decision = SchedulerDecision.endRound(verdict.forcedEndReason ?: "hard_gate"),
                floor = floor.pause(),
                done = true,
            )
        }

        val raw = softScheduler.decide(context, verdict.candidates, transcript)
        val decision = HardGate.validateDecision(raw, verdict)
        if (decision.action != SchedulerAction.SPEAK || decision.speakerId == null) {
            return StepResult(
                decision = decision.copy(action = SchedulerAction.END_ROUND),
                floor = floor.pause(),
                done = true,
            )
        }

        val speakerId = decision.speakerId
        val newFloor = HardGate.afterSpeakerGranted(floor, speakerId, afterUserMessage)
        return StepResult(
            decision = decision,
            floor = newFloor,
            grantedSpeakerId = speakerId,
            done = false,
        )
    }

    /**
     * Run until end_round or [maxSteps]. [onSpeak] must append the assistant message and return
     * mentions extracted from that message (for the next chain hop).
     */
    suspend fun runTurn(
        mode: GroupChatMode,
        members: List<GroupMember>,
        initialFloor: FloorState,
        initialMentions: List<Uuid>,
        transcriptProvider: () -> List<GroupTranscriptLine>,
        onSpeak: suspend (speakerId: Uuid) -> List<Uuid>,
        maxSteps: Int = policy.maxSpeakersPerUserTurn,
    ): FloorState {
        var floor = initialFloor
        var mentions = initialMentions
        var afterUser = true
        var steps = 0
        while (steps < maxSteps) {
            val step = nextStep(
                mode = mode,
                members = members,
                floor = floor,
                latestMentions = mentions,
                afterUserMessage = afterUser,
                transcript = transcriptProvider(),
            )
            floor = step.floor
            if (step.done || step.grantedSpeakerId == null) {
                return floor.pause()
            }
            mentions = onSpeak(step.grantedSpeakerId)
            afterUser = false
            steps++
        }
        return floor.pause()
    }
}
