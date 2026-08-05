package me.rerere.rikkahub.data.groupchat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** How the floor scheduler behaves for a group conversation. */
@Serializable
enum class GroupChatMode {
    /** Only @-mentioned members speak; otherwise SoftScheduler picks at most one, then often ends. */
    @SerialName("mention_first")
    MENTION_FIRST,

    /** Allow AI→AI chaining subject to HardGate depth/budget. */
    @SerialName("free_discussion")
    FREE_DISCUSSION,
}

@Serializable
data class GroupMember(
    val assistantId: Uuid,
    val displayName: String = "",
    /** Optional per-member chat model override; null = assistant/default chat model. */
    val chatModelId: Uuid? = null,
)

@Serializable
data class HardGatePolicy(
    val maxChainDepth: Int = 2,
    val maxSpeakersPerUserTurn: Int = 3,
    val perAssistantCooldownMessages: Int = 1,
) {
    companion object {
        val DEFAULT = HardGatePolicy()
    }
}

/**
 * Runtime floor accounting for one user turn (reset when the user sends a message).
 */
@Serializable
data class FloorState(
    val chainDepth: Int = 0,
    val speakersThisUserTurn: Int = 0,
    val lastSpeakerId: Uuid? = null,
    val pendingMentions: List<Uuid> = emptyList(),
    /** True while an AI chain is allowed to continue; false after pause / end_round. */
    val active: Boolean = false,
) {
    fun remainingChainDepth(policy: HardGatePolicy = HardGatePolicy.DEFAULT): Int =
        (policy.maxChainDepth - chainDepth).coerceAtLeast(0)

    fun remainingSpeakerBudget(policy: HardGatePolicy = HardGatePolicy.DEFAULT): Int =
        (policy.maxSpeakersPerUserTurn - speakersThisUserTurn).coerceAtLeast(0)

    fun resetForUserTurn(mentions: List<Uuid> = emptyList()): FloorState =
        FloorState(
            chainDepth = 0,
            speakersThisUserTurn = 0,
            lastSpeakerId = null,
            pendingMentions = mentions,
            active = true,
        )

    fun pause(): FloorState = copy(active = false, pendingMentions = emptyList())
}

@Serializable
enum class SchedulerAction {
    @SerialName("speak")
    SPEAK,

    @SerialName("end_round")
    END_ROUND,
}

@Serializable
data class SchedulerDecision(
    val action: SchedulerAction = SchedulerAction.END_ROUND,
    val speakerId: Uuid? = null,
    val reason: String = "",
    val addressedTo: List<String> = emptyList(),
) {
    companion object {
        fun endRound(reason: String = "end") = SchedulerDecision(
            action = SchedulerAction.END_ROUND,
            speakerId = null,
            reason = reason,
        )
    }
}

data class HardGateVerdict(
    val allowScheduling: Boolean,
    val candidates: List<Uuid>,
    val forcedEndReason: String? = null,
)

data class GroupScheduleContext(
    val mode: GroupChatMode,
    val members: List<GroupMember>,
    val floor: FloorState,
    val policy: HardGatePolicy = HardGatePolicy.DEFAULT,
    /** Explicit @ targets from the latest message (user or agent). */
    val latestMentions: List<Uuid> = emptyList(),
    /** True when the event that triggered scheduling is from the user. */
    val afterUserMessage: Boolean = false,
)
