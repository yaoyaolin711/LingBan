package me.rerere.rikkahub.data.agent

import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.accessibility.UnifiedObservation

/**
 * Planner interface — produces the next [ActionPlan] from goal + multimodal perception.
 *
 * Implementations must NOT be hard-wired to a single LLM vendor.
 * Future backends: GPT / Claude / DeepSeek / local heuristic.
 *
 * Prefer [plan] with [UnifiedObservation] (Accessibility > OCR > Vision fusion).
 */
interface AgentPlanner {
    /**
     * @param goal user goal, e.g. "发送消息给张三"
     * @param observation multimodal fused observation for planning
     * @param state running task context (history / failCount / page)
     */
    suspend fun plan(
        goal: String,
        observation: UnifiedObservation,
        state: TaskState,
    ): ActionPlan

    /**
     * Backward-compatible entry: wraps [UISnapshot] into [UnifiedObservation].
     */
    suspend fun plan(
        goal: String,
        snapshot: UISnapshot,
        state: TaskState,
    ): ActionPlan = plan(goal, UnifiedObservation.fromSnapshot(snapshot), state)
}

/**
 * Stub planner — no LLM call.
 * Bootstraps with legacy [AgentAction.SEE_SCREEN] so OCR / vision remain an Action source.
 * Returns empty non-done plans afterward until a real planner is injected.
 */
class StubAgentPlanner : AgentPlanner {
    override suspend fun plan(
        goal: String,
        observation: UnifiedObservation,
        state: TaskState,
    ): ActionPlan {
        if (goal.isBlank()) {
            return ActionPlan(
                actions = listOf(AgentAction(AgentAction.FAIL, target = "empty_goal")),
                reasoning = "Goal is blank",
            )
        }
        val hasPerceived = state.history.any {
            it.action.action == AgentAction.SEE_SCREEN || it.action.action == AgentAction.DUMP_UI
        }
        if (!hasPerceived) {
            return ActionPlan(
                actions = listOf(
                    AgentAction(
                        action = AgentAction.SEE_SCREEN,
                        target = "current_screen",
                        params = mapOf("ocr_mode" to "auto"),
                    )
                ),
                reasoning = "Stub: bootstrap SEE_SCREEN for multimodal perception (a11y+OCR+vision)",
            )
        }
        val actionable = observation.actionableElements.size
        val fused = observation.fusedElements.size
        return ActionPlan(
            actions = emptyList(),
            reasoning = "StubAgentPlanner: fused=$fused actionable=$actionable. " +
                "No LLM planner wired yet. Inject GPT/Claude/DeepSeek using UnifiedObservation.",
            done = false,
        )
    }
}
