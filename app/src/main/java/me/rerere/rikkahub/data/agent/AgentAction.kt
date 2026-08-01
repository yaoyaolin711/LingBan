package me.rerere.rikkahub.data.agent

import kotlinx.serialization.Serializable

/**
 * Single step in an [ActionPlan].
 *
 * Includes both structured UI actions and legacy phone-control tool actions
 * (SEE_SCREEN / DUMP_UI / …) so the existing OCR tool path remains an Action source.
 */
@Serializable
data class AgentAction(
    val action: String,
    val target: String = "",
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        // Structured UI actions
        const val CLICK_NODE = "CLICK_NODE"
        const val CLICK_XY = "CLICK_XY"
        const val TYPE_TEXT = "TYPE_TEXT"
        const val SWIPE = "SWIPE"
        const val GLOBAL = "GLOBAL"
        const val OPEN_APP = "OPEN_APP"
        const val WAIT_FOR_TEXT = "WAIT_FOR_TEXT"
        const val WAIT_FOR_PAGE = "WAIT_FOR_PAGE"

        // Legacy tool-calling / OCR perception actions (existing DeviceControlTools path)
        const val SEE_SCREEN = "SEE_SCREEN"
        const val DUMP_UI = "DUMP_UI"

        // Terminal control
        const val DONE = "DONE"
        const val FAIL = "FAIL"
    }
}

/**
 * Planner output: flat [actions] (legacy) + structured [steps] (Stage9+).
 *
 * Construction is normalized via [ActionPlan.invoke]:
 * - only [actions] → [steps] auto-derived as PENDING [PlanStep]s
 * - only [steps] → [actions] derived from step actions
 * - both present → keep both; consumers that understand steps should prefer [steps]
 */
@Serializable
data class ActionPlan private constructor(
    val actions: List<AgentAction>,
    val reasoning: String,
    /** Planner believes the goal is already achieved — skip Act. */
    val done: Boolean,
    val steps: List<PlanStep>,
) {
    /** Public alternative to generated [copy] (avoids exposing private constructor). */
    fun withReasoning(reasoning: String): ActionPlan = normalize(
        actions = actions,
        reasoning = reasoning,
        done = done,
        steps = steps,
    )

    /**
     * Runtime consumption order: prefer [steps]; fall back to deriving from [actions].
     */
    fun preferredSteps(): List<PlanStep> =
        if (steps.isNotEmpty()) steps else actions.toPlanSteps()

    companion object {
        operator fun invoke(
            actions: List<AgentAction> = emptyList(),
            reasoning: String = "",
            done: Boolean = false,
            steps: List<PlanStep> = emptyList(),
        ): ActionPlan = normalize(
            actions = actions,
            reasoning = reasoning,
            done = done,
            steps = steps,
        )

        /**
         * Compatibility normalize:
         * - prefer explicit [steps] when non-empty
         * - otherwise derive steps from [actions]
         * - if [actions] empty but steps present, derive actions from steps
         */
        fun normalize(
            actions: List<AgentAction> = emptyList(),
            reasoning: String = "",
            done: Boolean = false,
            steps: List<PlanStep> = emptyList(),
        ): ActionPlan {
            val resolvedSteps = if (steps.isNotEmpty()) {
                steps
            } else {
                actions.toPlanSteps()
            }
            val resolvedActions = when {
                actions.isNotEmpty() -> actions
                else -> resolvedSteps.map { it.action }
            }
            return ActionPlan(
                actions = resolvedActions,
                reasoning = reasoning,
                done = done,
                steps = resolvedSteps,
            )
        }
    }
}

@Serializable
data class ActionExecuteResult(
    val ok: Boolean,
    val message: String = "",
    val observationSummary: String? = null,
)

@Serializable
data class VerifyResult(
    val success: Boolean,
    val message: String = "",
    /** Goal not done yet but last action looked healthy — continue loop. */
    val continueLoop: Boolean = !success,
)
