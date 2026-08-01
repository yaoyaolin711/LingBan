package me.rerere.rikkahub.data.agent

import kotlinx.serialization.Serializable

/**
 * Execution status of a single [PlanStep] inside an [ActionPlan].
 */
@Serializable
enum class StepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    SKIPPED,
}

/**
 * One step in an [ActionPlan].
 *
 * Wraps an [AgentAction] with index/status metadata for Rule Planner upgrades.
 * Does not change Executor semantics — [action] remains the executable unit.
 */
@Serializable
data class PlanStep(
    val index: Int,
    val action: AgentAction,
    val status: StepStatus = StepStatus.PENDING,
    val ruleId: String? = null,
    val source: String? = null,
)

/** Derive PENDING [PlanStep]s from a flat action list (legacy ActionPlan shape). */
fun List<AgentAction>.toPlanSteps(
    ruleId: String? = null,
    source: String? = null,
): List<PlanStep> = mapIndexed { index, action ->
    PlanStep(
        index = index,
        action = action,
        status = StepStatus.PENDING,
        ruleId = ruleId,
        source = source,
    )
}
