package me.rerere.rikkahub.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRouterTest {

    @Test
    fun openWechat_routesDeviceTask() {
        val d = TaskRouter.classify("帮我打开微信", phoneControlEnabled = true)
        assertEquals(TaskRoute.DEVICE_TASK, d.route)
        assertEquals(ExecutionMode.RULE, d.executionMode)
    }

    @Test
    fun openWechatAndTellMe_routesHybrid() {
        val d = TaskRouter.classify("打开微信并告诉我现在在哪个页面", phoneControlEnabled = true)
        assertEquals(TaskRoute.HYBRID, d.route)
    }

    @Test
    fun normalChat_routesChat() {
        val d = TaskRouter.classify("今天天气怎么样", phoneControlEnabled = true)
        assertEquals(TaskRoute.CHAT, d.route)
    }

    @Test
    fun phoneDisabled_routesChat() {
        val d = TaskRouter.classify("打开微信", phoneControlEnabled = false)
        assertEquals(TaskRoute.CHAT, d.route)
    }

    @Test
    fun back_routesDeviceTask() {
        val d = TaskRouter.classify("返回", phoneControlEnabled = true)
        assertEquals(TaskRoute.DEVICE_TASK, d.route)
    }

    @Test
    fun complexPhone_staysChatForToolCalling() {
        val d = TaskRouter.classify("打开微信给张三发消息说晚上见面", phoneControlEnabled = true)
        assertEquals(TaskRoute.CHAT, d.route)
    }

    @Test
    fun openThenClick_routesDeviceTask() {
        val d = TaskRouter.classify("打开微信，点击发现", phoneControlEnabled = true)
        assertEquals(TaskRoute.DEVICE_TASK, d.route)
    }

    @Test
    fun openNetEaseAndPlayLiked_routesChatNotOneShotOpen() {
        val goal = "打开网易云播放我喜欢的音乐"
        assertTrue(LocalRuleEngine.isComplexGoal(goal))
        assertFalse(LocalRuleEngine.isPureOpenAppRequest(goal))
        assertNull(LocalRuleEngine.tryPlan(TaskContext(goal = goal, currentState = "|")))
        val d = TaskRouter.classify(goal, phoneControlEnabled = true)
        assertEquals(TaskRoute.CHAT, d.route)
        assertEquals("complex_use_tools", d.reason)
    }

    @Test
    fun pureOpenNetEase_stillDeviceTask() {
        val goal = "打开网易云"
        assertFalse(LocalRuleEngine.isComplexGoal(goal))
        assertTrue(LocalRuleEngine.isPureOpenAppRequest(goal))
        val d = TaskRouter.classify(goal, phoneControlEnabled = true)
        assertEquals(TaskRoute.DEVICE_TASK, d.route)
    }
}
