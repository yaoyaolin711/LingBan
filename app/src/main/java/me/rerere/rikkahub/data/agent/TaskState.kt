package me.rerere.rikkahub.data.agent

import kotlinx.serialization.Serializable

/**
 * Lifecycle phases for the Agent task state machine.
 *
 * Perceive → Plan → Act → Verify
 */
@Serializable
enum class AgentPhase {
    IDLE,
    PERCEIVING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    SUCCESS,
    FAILED,
}

/**
 * Runtime-internal execution ledger for Perceive → Plan → Act → Verify.
 *
 * Dual-written into [AgentState] via [AgentStateManager.syncFromTask].
 * UI / Memory / future Planner should prefer [AgentState], not this type.
 *
 * Environment pointers ([packageName], [currentPage]) are for Runtime tick /
 * [TaskContext.pageKey] / emptySnap only — not for UI display.
 */
@Serializable
data class TaskState(
    val taskId: String,
    val goal: String,
    val currentStep: Int = 0,
    val state: AgentPhase = AgentPhase.IDLE,
    val history: List<ActionRecord> = emptyList(),
    /**
     * Foreground activity / page fingerprint for Runtime only.
     * External consumers: use [AgentState.currentActivity].
     */
    val currentPage: String = "",
    /**
     * Foreground package for Runtime only.
     * External consumers: use [AgentState.currentPackage].
     */
    val packageName: String = "",
    val failCount: Int = 0,
    val maxFails: Int = 5,
    val lastError: String? = null,
    /**
     * Legacy string summary of the last observation.
     * Prefer [AgentState.currentObservation] for structured observation state.
     */
    @Deprecated(
        message = "Use AgentState.currentObservation via AgentStateManager",
        replaceWith = ReplaceWith(
            "AgentStateManager.snapshot()?.currentObservation",
            "me.rerere.rikkahub.data.agent.AgentStateManager",
        ),
        level = DeprecationLevel.WARNING,
    )
    val lastObservationSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isTerminal: Boolean
        get() = state == AgentPhase.SUCCESS || state == AgentPhase.FAILED

    fun withPhase(phase: AgentPhase, error: String? = lastError): TaskState = copy(
        state = phase,
        lastError = error,
        updatedAt = System.currentTimeMillis(),
    )
}

@Serializable
data class ActionRecord(
    val step: Int,
    val action: AgentAction,
    val ok: Boolean,
    val message: String = "",
    val verification: VerificationStatus? = null,
    val verifyMessage: String = "",
    val attempts: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
)
