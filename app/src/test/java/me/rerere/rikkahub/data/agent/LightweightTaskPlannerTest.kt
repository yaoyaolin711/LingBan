package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightweightTaskPlannerTest {

    private fun planner() = LightweightTaskPlanner(
        llm = NoOpLlmTaskPlanner(),
        defaultDispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun openWechat_usesLocalRule_noLlm() = runBlocking {
        val p = planner()
        val plan = p.plan(
            TaskContext(
                goal = "打开微信",
                currentState = "|",
                allowLlm = true,
            )
        )
        assertEquals(1, plan.actions.size)
        assertEquals(AgentAction.OPEN_APP, plan.actions[0].action)
        assertEquals("com.tencent.mm", plan.actions[0].target)
        assertTrue(plan.reasoning.startsWith("rule:open_app"))
    }

    @Test
    fun sameGoal_hitsCache() = runBlocking {
        val p = planner()
        val ctx = TaskContext(goal = "打开微信", currentState = "com.android.launcher|Launcher|step=0")
        val first = p.plan(ctx)
        val second = p.plan(ctx.copy(currentState = "com.android.launcher|Launcher|step=1"))
        assertEquals(first.actions, second.actions)
        assertTrue(second.reasoning.startsWith("cache:"))
    }

    @Test
    fun complexGoal_notResolvedByOpenRuleAlone() {
        assertTrue(LocalRuleEngine.isComplexGoal("给张三发送消息说你好"))
        assertFalse(LocalRuleEngine.isComplexGoal("打开微信"))
        assertTrue(LocalRuleEngine.canPlanWithoutFullTree("打开支付宝"))
        assertFalse(LocalRuleEngine.isComplexGoal("打开微信，点击发现"))
        assertTrue(LocalRuleEngine.isComplexGoal("打开网易云播放我喜欢的音乐"))
        assertFalse(LocalRuleEngine.isPureOpenAppRequest("打开网易云播放我喜欢的音乐"))
        assertTrue(LocalRuleEngine.isPureOpenAppRequest("打开网易云音乐"))
    }

    @Test
    fun openThenClick_emitsMultiStepPlan() = runBlocking {
        val p = planner()
        val plan = p.plan(
            TaskContext(
                goal = "打开微信，点击发现",
                currentState = "|",
                allowLlm = true,
            )
        )
        assertEquals(3, plan.actions.size)
        assertEquals(AgentAction.OPEN_APP, plan.actions[0].action)
        assertEquals(AgentAction.WAIT_FOR_PAGE, plan.actions[1].action)
        assertEquals(AgentAction.CLICK_NODE, plan.actions[2].action)
        assertEquals("发现", plan.actions[2].target)
        assertTrue(plan.reasoning.contains("open_then_click"))
    }

    @Test
    fun historyReplay_beforeEmptyLlm() = runBlocking {
        val p = planner()
        p.historyStore.record(
            goal = "点发送",
            pageKey = "com.chat|ChatActivity",
            action = AgentAction(AgentAction.CLICK_NODE, target = "发送"),
        )
        val plan = p.plan(
            TaskContext(
                goal = "点发送",
                currentState = "com.chat|ChatActivity|step=0",
                allowLlm = true,
            )
        )
        assertEquals(AgentAction.CLICK_NODE, plan.actions.first().action)
        assertTrue(plan.reasoning.startsWith("history:"))
    }
}
