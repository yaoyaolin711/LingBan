package me.rerere.rikkahub.data.agent

/**
 * How AgentRuntime plans/executes a device task.
 *
 * Phase 1 only runs [RULE]. [PLANNER] / [LLM_ASSISTED] are reserved.
 */
enum class ExecutionMode {
    /** LocalRuleEngine / PlanCache only — no LLM. */
    RULE,

    /** Structured planner (future). */
    PLANNER,

    /** Planner + LLM assist (future). */
    LLM_ASSISTED,
}
