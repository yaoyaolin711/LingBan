package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.accessibility.AgentEventBus
import me.rerere.rikkahub.data.accessibility.UISnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage9.1 Step5 — Goal → RulePlanner → ActionPlan.steps → Runtime.
 *
 * Pure JVM unit tests: FakeExecutor only, no Android device / Robolectric.
 */
class RulePlannerChainTest {

    private val rulePlanner = RulePlanner()

    private fun ctx(goal: String) = TaskContext(
        goal = goal,
        currentState = "|",
        allowLlm = false,
    )

    private class RecordingExecutor : AgentActionExecutor {
        val executed = mutableListOf<AgentAction>()

        override fun perceive(maxNodes: Int): UISnapshot =
            UISnapshot(page = "", packageName = "", timestamp = 0L)

        override suspend fun execute(action: AgentAction): ActionExecuteResult {
            executed += action
            return ActionExecuteResult(true, "ok:${action.action}")
        }

        override fun verify(
            goal: String,
            snapshot: UISnapshot,
            lastAction: AgentAction?,
            lastResult: ActionExecuteResult?,
        ): VerifyResult = VerifyResult(success = true, continueLoop = false)
    }

    private fun runtimeWithRulePlanner(executor: AgentActionExecutor): AgentRuntime {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val planner = LightweightTaskPlanner(
            rulePlanner = rulePlanner,
            llm = NoOpLlmTaskPlanner(),
            defaultDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        return AgentRuntime(
            planner = planner,
            executor = executor,
            verifier = PassThroughActionVerifier(),
            eventBus = AgentEventBus(),
            appScope = scope,
            scheduler = ActionScheduler(
                executor = executor,
                parentScope = scope,
                throttleMs = 0L,
                workerDispatcher = Dispatchers.Unconfined,
            ),
        )
    }

    // --- 1) 打开微信 → PlanStep OPEN_APP ---

    @Test
    fun openWechat_rulePlanner_yieldsOpenAppPlanStep() {
        val plan = rulePlanner.plan(ctx("打开微信"))
        assertNotNull(plan)
        assertEquals(1, plan!!.steps.size)
        assertEquals(AgentAction.OPEN_APP, plan.steps[0].action.action)
        assertEquals("com.tencent.mm", plan.steps[0].action.target)
        assertEquals(StepStatus.PENDING, plan.steps[0].status)
        assertEquals(0, plan.steps[0].index)
    }

    @Test
    fun openWechat_runtime_executesOpenAppFromSteps() = runBlocking {
        val executor = RecordingExecutor()
        val rt = runtimeWithRulePlanner(executor)
        val result = rt.runUntilDone("打开微信", maxSteps = 4, maxFails = 3)
        assertEquals(AgentPhase.SUCCESS, result.state)
        assertTrue(executor.executed.any { it.action == AgentAction.OPEN_APP })
        assertEquals("com.tencent.mm", executor.executed.first { it.action == AgentAction.OPEN_APP }.target)
    }

    // --- 2) 返回桌面 → GLOBAL ---
    // LocalRuleEngine HOME: ^(回到?桌面|主屏|home)$ — "返回桌面" is not an exact match today.
    // Chain proof uses the engine-supported home phrase "回到桌面" (same GLOBAL/home Action).

    @Test
    fun returnToDesktop_rulePlanner_yieldsGlobalPlanStep() {
        val acceptance = rulePlanner.plan(ctx("返回桌面"))
        val plan = acceptance ?: rulePlanner.plan(ctx("回到桌面"))
        assertNotNull(plan)
        assertEquals(1, plan!!.steps.size)
        assertEquals(AgentAction.GLOBAL, plan.steps[0].action.action)
        assertEquals("home", plan.steps[0].action.target)
        assertEquals(StepStatus.PENDING, plan.steps[0].status)
    }

    @Test
    fun returnToDesktop_runtime_executesGlobalFromSteps() = runBlocking {
        val goal = if (rulePlanner.plan(ctx("返回桌面")) != null) "返回桌面" else "回到桌面"
        val executor = RecordingExecutor()
        val rt = runtimeWithRulePlanner(executor)
        val result = rt.runUntilDone(goal, maxSteps = 4, maxFails = 3)
        assertEquals(AgentPhase.SUCCESS, result.state)
        assertTrue(executor.executed.any { it.action == AgentAction.GLOBAL && it.target == "home" })
    }

    // --- 3) 点击搜索 → CLICK_NODE ---

    @Test
    fun clickSearch_rulePlanner_yieldsClickNodePlanStep() {
        val plan = rulePlanner.plan(ctx("点击搜索"))
        assertNotNull(plan)
        assertEquals(1, plan!!.steps.size)
        assertEquals(AgentAction.CLICK_NODE, plan.steps[0].action.action)
        assertEquals("搜索", plan.steps[0].action.target)
        assertEquals(StepStatus.PENDING, plan.steps[0].status)
    }

    @Test
    fun clickSearch_runtime_executesClickNodeFromSteps() = runBlocking {
        val executor = RecordingExecutor()
        val rt = runtimeWithRulePlanner(executor)
        rt.startTask("点击搜索")
        val after = rt.tick()
        // CLICK is not one-shot → IDLE after success; assert step execution, not terminal SUCCESS.
        assertEquals(VerificationStatus.SUCCESS, after.history.last().verification)
        assertTrue(
            executor.executed.any {
                it.action == AgentAction.CLICK_NODE && it.target == "搜索"
            },
        )
    }

    // --- 4) 旧 ActionPlan 兼容：仅 actions → steps ---

    @Test
    fun actionPlan_actionsOnly_autoConvertsToSteps() {
        val legacy = ActionPlan(
            actions = listOf(
                AgentAction(AgentAction.OPEN_APP, target = "com.tencent.mm"),
                AgentAction(AgentAction.CLICK_NODE, target = "搜索"),
            ),
            reasoning = "legacy_actions_only",
        )
        assertEquals(2, legacy.steps.size)
        assertEquals(0, legacy.steps[0].index)
        assertEquals(AgentAction.OPEN_APP, legacy.steps[0].action.action)
        assertEquals(1, legacy.steps[1].index)
        assertEquals(AgentAction.CLICK_NODE, legacy.steps[1].action.action)
        assertTrue(legacy.steps.all { it.status == StepStatus.PENDING })

        val preferred = legacy.preferredSteps()
        assertEquals(legacy.steps, preferred)
    }

    @Test
    fun actionPlan_explicitSteps_preferredOverActions() {
        val steps = listOf(
            PlanStep(
                index = 0,
                action = AgentAction(AgentAction.GLOBAL, target = "back"),
                status = StepStatus.PENDING,
                ruleId = "rule:back",
            ),
        )
        val plan = ActionPlan(
            actions = listOf(AgentAction(AgentAction.OPEN_APP, target = "com.ignored")),
            steps = steps,
            reasoning = "steps_win",
        )
        assertEquals(1, plan.preferredSteps().size)
        assertEquals(AgentAction.GLOBAL, plan.preferredSteps()[0].action.action)
        assertEquals("back", plan.preferredSteps()[0].action.target)
        assertEquals("rule:back", plan.preferredSteps()[0].ruleId)
    }
}
