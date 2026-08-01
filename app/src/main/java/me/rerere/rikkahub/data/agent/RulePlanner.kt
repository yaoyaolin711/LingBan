package me.rerere.rikkahub.data.agent

/**
 * Rule-based planner: [TaskContext] → [ActionPlan].
 *
 * Owns the planning entry used by [LightweightTaskPlanner].
 * Delegates rule matching to [LocalRuleEngine.tryPlan] so behavior stays identical.
 *
 * Complexity / perception heuristics remain on [LocalRuleEngine]:
 * - [LocalRuleEngine.isComplexGoal]
 * - [LocalRuleEngine.canPlanWithoutFullTree]
 */
class RulePlanner {

    /**
     * @return a rule [ActionPlan], or null when no local rule matches.
     */
    fun plan(context: TaskContext): ActionPlan? =
        LocalRuleEngine.tryPlan(context)
}
