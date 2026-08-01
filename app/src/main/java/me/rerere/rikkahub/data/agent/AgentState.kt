package me.rerere.rikkahub.data.agent

import kotlinx.serialization.Serializable

/**
 * Compact UI observation for [AgentState] — never stores a full accessibility tree.
 */
@Serializable
data class CompactObservation(
    val level: ObservationLevel = ObservationLevel.L0,
    val packageName: String = "",
    val activityName: String = "",
    val interactiveCount: Int = 0,
    val keyTexts: List<String> = emptyList(),
    val treeHash: String = "",
    val fromCache: Boolean = false,
    val usedFallback: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class ObservationLevel {
    /** package + activity only (event cache). */
    L0,
    /** Light UI summary: interactive count, key texts, tree hash. */
    L1,
    /** Full UI tree (complex tasks only). */
    L2,
}

/**
 * Unified Runtime state outlet for UI, Memory, and future Planner.
 *
 * ## Responsibility boundary (Stage 8.2)
 * - **This type**: external snapshot — phase, foreground app/activity, structured
 *   observation, last action/result, error count.
 * - **[TaskState]**: internal execution ledger — steps, history, maxFails, lastError,
 *   Runtime-only package/page pointers.
 *
 * Sync is one-way: TaskState changes → [AgentStateManager.syncFromTask] → AgentState
 * → [AgentRuntimeEvent.StateUpdated]. Do not write AgentState back into TaskState.
 *
 * Prefer reading this (via [AgentStateManager.state] / [AgentManager.agentState]) over
 * [AgentRuntime.taskState] for display and higher-level planning.
 *
 * [treeHash] mirrors [CompactObservation.treeHash] for quick fingerprint access;
 * keep both — do not remove.
 */
@Serializable
data class AgentState(
    val taskId: String,
    val goal: String,
    val phase: AgentPhase = AgentPhase.IDLE,
    val currentPackage: String = "",
    val currentActivity: String = "",
    val currentObservation: CompactObservation? = null,
    val lastAction: AgentAction? = null,
    val lastActionResult: ActionExecuteResult? = null,
    val errorCount: Int = 0,
    /** Quick page fingerprint; same source as [currentObservation.treeHash] when present. */
    val treeHash: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun fromTask(
            task: TaskState,
            observation: CompactObservation? = null,
            lastAction: AgentAction? = null,
            lastActionResult: ActionExecuteResult? = null,
        ): AgentState {
            val hist = task.history.lastOrNull()
            return AgentState(
                taskId = task.taskId,
                goal = task.goal,
                phase = task.state,
                currentPackage = task.packageName,
                currentActivity = task.currentPage,
                currentObservation = observation,
                lastAction = lastAction ?: hist?.action,
                lastActionResult = lastActionResult
                    ?: hist?.let { ActionExecuteResult(ok = it.ok, message = it.message) },
                errorCount = task.failCount,
                treeHash = observation?.treeHash.orEmpty(),
                updatedAt = task.updatedAt,
            )
        }
    }
}
