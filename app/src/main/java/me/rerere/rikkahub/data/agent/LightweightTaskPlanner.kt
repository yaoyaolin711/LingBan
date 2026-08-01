package me.rerere.rikkahub.data.agent

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.accessibility.UnifiedObservation

/**
 * Lightweight [TaskPlanner] + [AgentPlanner].
 *
 * Stage9.1 — orchestration only:
 * ```
 * PlanCache → RulePlanner → HistoryActionStore → LlmTaskPlanner(NoOp)
 * ```
 *
 * [LlmTaskPlanner] is retained for interface/DI, but real LLM planners are never
 * invoked on the Runtime hot path. Only [NoOpLlmTaskPlanner] may occupy slot 4.
 */
class LightweightTaskPlanner(
    private val cache: PlanCache = PlanCache(),
    private val history: HistoryActionStore = HistoryActionStore(),
    private val rulePlanner: RulePlanner = RulePlanner(),
    private val llm: LlmTaskPlanner = NoOpLlmTaskPlanner(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TaskPlanner, AgentPlanner {

    companion object {
        private const val TAG = "TaskPlanner"

        /**
         * Stage gate: must stay false so a non-NoOp [LlmTaskPlanner] cannot enter
         * the planning hot path even if DI is swapped by mistake.
         */
        private const val ENABLE_REAL_LLM_PLANNER = false
    }

    val planCache: PlanCache get() = cache
    val historyStore: HistoryActionStore get() = history

    override suspend fun plan(context: TaskContext): ActionPlan =
        withContext(defaultDispatcher) {
            val tracer = me.rerere.rikkahub.data.agent.trace.AgentTracer.instance
            if (tracer != null) {
                tracer.measureSuspend(me.rerere.rikkahub.data.agent.trace.AgentTrace.PLANNER) {
                    planInternal(context)
                }
            } else {
                planInternal(context)
            }
        }

    override suspend fun plan(
        goal: String,
        observation: UnifiedObservation,
        state: TaskState,
    ): ActionPlan {
        // allowLlm flag is informational for later stages; hot path still NoOp-only.
        val allowLlm = LocalRuleEngine.isComplexGoal(goal)
        return plan(
            TaskContext.of(
                goal = goal,
                state = state,
                observation = observation,
                allowLlm = allowLlm,
            )
        )
    }

    override suspend fun plan(
        goal: String,
        snapshot: UISnapshot,
        state: TaskState,
    ): ActionPlan {
        // Avoid building full UnifiedObservation when rules can decide alone.
        if (LocalRuleEngine.canPlanWithoutFullTree(goal)) {
            return plan(
                TaskContext.of(
                    goal = goal,
                    state = state,
                    observation = null,
                    allowLlm = false,
                )
            )
        }
        return plan(goal, UnifiedObservation.fromSnapshot(snapshot), state)
    }

    /**
     * Source orchestration only — no rule logic / no Executor / no Runtime loop.
     */
    private suspend fun planInternal(context: TaskContext): ActionPlan {
        val goal = context.goal.trim()
        if (goal.isEmpty()) {
            return ActionPlan(
                actions = listOf(AgentAction(AgentAction.FAIL, target = "empty_goal")),
                reasoning = "empty_goal",
            )
        }

        // 1) PlanCache
        cache.get(goal, context.currentState)?.let { hit ->
            Log.d(TAG, "cache hit goal=$goal state=${context.currentState}")
            return hit.withReasoning("cache:${hit.reasoning}")
        }

        // 2) RulePlanner → LocalRuleEngine
        rulePlanner.plan(context)?.let { ruled ->
            cache.put(goal, context.currentState, ruled)
            return ruled
        }

        // 3) HistoryActionStore
        val pageKey = context.currentState.substringBefore("|step=")
        history.lookup(goal, pageKey)?.let { action ->
            val plan = ActionPlan(
                actions = listOf(action),
                reasoning = "history:$pageKey",
            )
            cache.put(goal, context.currentState, plan)
            return plan
        }

        // 4) LlmTaskPlanner(NoOp) — interface/DI retained; real LLM blocked
        tryLlmPlannerNoOpOnly(context, goal)?.let { return it }

        // Soft bootstrap for complex / unknown: one SEE_SCREEN (local), not LLM
        val state = context.taskState
        val perceived = state?.history?.any {
            it.action.action == AgentAction.SEE_SCREEN || it.action.action == AgentAction.DUMP_UI
        } == true
        if (!perceived && !LocalRuleEngine.canPlanWithoutFullTree(goal)) {
            return ActionPlan(
                actions = listOf(
                    AgentAction(
                        AgentAction.SEE_SCREEN,
                        target = "current_screen",
                        params = mapOf("ocr_mode" to "auto"),
                    )
                ),
                reasoning = "bootstrap:see_screen",
            )
        }

        return ActionPlan(
            actions = emptyList(),
            reasoning = "no_rule_no_history_no_llm",
            done = false,
        )
    }

    /**
     * Slot 4 of the planner chain: [LlmTaskPlanner] (NoOp).
     *
     * Stage9.1:
     * - Interface + DI kept (`llm` / `ioDispatcher` retained for later stages)
     * - Only [NoOpLlmTaskPlanner] is accepted on this path
     * - Real LLM never enters the hot path ([ENABLE_REAL_LLM_PLANNER] = false)
     * - NoOp miss: no [LlmTaskPlanner.planComplex] call
     */
    private suspend fun tryLlmPlannerNoOpOnly(context: TaskContext, goal: String): ActionPlan? {
        if (!context.allowLlm) return null
        if (!LocalRuleEngine.isComplexGoal(goal)) return null

        if (llm !is NoOpLlmTaskPlanner) {
            Log.w(TAG, "Blocked non-NoOp LlmTaskPlanner on hot path (Stage9.1)")
            return null
        }

        if (!ENABLE_REAL_LLM_PLANNER) {
            Log.d(TAG, "llm slot=NoOp (hot path disabled) goal=$goal")
            return null
        }

        // Future stage only — unreachable while ENABLE_REAL_LLM_PLANNER == false.
        if (!llm.isAvailable) return null
        val llmPlan = withContext(ioDispatcher) { llm.planComplex(context) }
        if (llmPlan.actions.isEmpty() && !llmPlan.done) return null
        cache.put(goal, context.currentState, llmPlan)
        return llmPlan.withReasoning("llm:${llmPlan.reasoning}")
    }
}
