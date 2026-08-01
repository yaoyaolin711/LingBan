package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.accessibility.AgentEventBus
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.accessibility.UiBounds
import me.rerere.rikkahub.data.accessibility.UiTreeNode
import me.rerere.rikkahub.data.accessibility.UnifiedObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRuntimeTest {

    private class ScriptedPlanner(
        private val plans: List<ActionPlan>,
    ) : AgentPlanner {
        private var index = 0
        override suspend fun plan(
            goal: String,
            observation: UnifiedObservation,
            state: TaskState,
        ): ActionPlan = plans.getOrElse(index++) {
            ActionPlan(actions = listOf(AgentAction(AgentAction.DONE)))
        }
    }

    private class FakeExecutor(
        private var clickCount: Int = 0,
    ) : AgentActionExecutor {
        val executed = mutableListOf<AgentAction>()

        override fun perceive(maxNodes: Int): UISnapshot {
            val page = if (clickCount > 0) "com.example.ChatActivity" else "com.example.MainActivity"
            val text = if (clickCount > 0) "你好张三" else "发送"
            return UISnapshot(
                page = page,
                packageName = "com.example",
                timestamp = 1L,
                root = UiTreeNode(
                    nodeId = "n0",
                    text = text,
                    editable = clickCount == 0,
                    clickable = true,
                    bounds = UiBounds(0, 0, 100, 50),
                ),
                nodeCount = 1,
            )
        }

        override suspend fun execute(action: AgentAction): ActionExecuteResult {
            executed += action
            if (action.action == AgentAction.CLICK_NODE || action.action == AgentAction.CLICK_XY) {
                clickCount++
            }
            return when (action.action) {
                AgentAction.FAIL -> ActionExecuteResult(false, "fail")
                else -> ActionExecuteResult(true, "ok:${action.action}")
            }
        }

        override fun verify(
            goal: String,
            snapshot: UISnapshot,
            lastAction: AgentAction?,
            lastResult: ActionExecuteResult?,
        ): VerifyResult = VerifyResult(success = false, continueLoop = true)
    }

    private fun runtime(
        planner: AgentPlanner,
        executor: AgentActionExecutor,
        verifier: ActionVerifier = PassThroughActionVerifier(),
    ): AgentRuntime {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return AgentRuntime(
            planner = planner,
            executor = executor,
            verifier = verifier,
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

    @Test
    fun tick_actionSuccess_continuesLoop_thenDone() = runBlocking {
        val executor = FakeExecutor()
        val planner = ScriptedPlanner(
            listOf(
                ActionPlan(actions = listOf(AgentAction(AgentAction.SEE_SCREEN, target = "current_screen"))),
                ActionPlan(actions = listOf(AgentAction(AgentAction.CLICK_NODE, target = "发送"))),
                ActionPlan(actions = listOf(AgentAction(AgentAction.DONE))),
            ),
        )
        val rt = runtime(planner, executor)

        rt.startTask("发送消息给张三")
        assertEquals(AgentPhase.IDLE, rt.tick().state)
        assertEquals(AgentPhase.IDLE, rt.tick().state)
        val done = rt.tick()
        assertEquals(AgentPhase.SUCCESS, done.state)
        assertTrue(executor.executed.any { it.action == AgentAction.CLICK_NODE })
        assertEquals(VerificationStatus.SUCCESS, done.history.last().verification)
    }

    @Test
    fun defaultVerifier_retriesClickUntilUiChanges() = runBlocking {
        val executor = FakeExecutor()
        val planner = ScriptedPlanner(
            listOf(
                ActionPlan(
                    actions = listOf(
                        AgentAction(
                            action = AgentAction.CLICK_NODE,
                            target = "发送",
                            params = mapOf(
                                "expect_page_change" to "true",
                                "max_retries" to "2",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val rt = runtime(planner, executor, DefaultActionVerifier())
        rt.startTask("点发送")
        val after = rt.tick()
        // First execute bumps clickCount → page changes → SUCCESS → IDLE
        assertEquals(AgentPhase.IDLE, after.state)
        assertEquals(VerificationStatus.SUCCESS, after.history.last().verification)
        assertTrue(after.history.last().attempts >= 1)
    }

    @Test
    fun stubPlanner_bootstrapsWithSeeScreen() = runBlocking {
        val planner = StubAgentPlanner()
        val plan = planner.plan(
            goal = "发消息",
            snapshot = UISnapshot(page = "", packageName = "", timestamp = 0L),
            state = TaskState(taskId = "t1", goal = "发消息"),
        )
        assertEquals(1, plan.actions.size)
        assertEquals(AgentAction.SEE_SCREEN, plan.actions[0].action)
    }

    @Test
    fun runUntilDone_emptyPlanEventuallyFailsByMaxFails() = runBlocking {
        val rt = runtime(StubAgentPlanner(), FakeExecutor())
        val result = rt.runUntilDone("测试目标", maxSteps = 20, maxFails = 3)
        assertEquals(AgentPhase.FAILED, result.state)
        assertTrue(result.failCount >= 3 || result.lastError != null)
    }
}
