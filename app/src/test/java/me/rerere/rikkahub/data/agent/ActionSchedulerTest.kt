package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.accessibility.UISnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionSchedulerTest {

    private class SlowExecutor(
        private val delayMs: Long = 0L,
    ) : AgentActionExecutor {
        val executed = mutableListOf<String>()
        override fun perceive(maxNodes: Int): UISnapshot =
            UISnapshot(page = "", packageName = "", timestamp = 0L)

        override suspend fun execute(action: AgentAction): ActionExecuteResult {
            if (delayMs > 0) delay(delayMs)
            executed += action.action
            return ActionExecuteResult(true, "ok")
        }

        override fun verify(
            goal: String,
            snapshot: UISnapshot,
            lastAction: AgentAction?,
            lastResult: ActionExecuteResult?,
        ): VerifyResult = VerifyResult(false, continueLoop = true)
    }

    @Test
    fun submit_returnsCostTime() = runBlocking {
        val exec = SlowExecutor()
        val scheduler = ActionScheduler(
            executor = exec,
            throttleMs = 0L,
            workerDispatcher = Dispatchers.Unconfined,
        )
        val result = scheduler.submit(
            AgentAction(AgentAction.CLICK_NODE, target = "发送", params = mapOf("timeout" to "3")),
        )
        assertTrue(result.success)
        assertTrue(result.costTime >= 0)
        assertEquals(null, result.error)
        assertEquals(AgentAction.CLICK_NODE, exec.executed.first())
    }

    @Test
    fun timeout_marksTimedOut() = runBlocking {
        val exec = SlowExecutor(delayMs = 500)
        val scheduler = ActionScheduler(
            executor = exec,
            throttleMs = 0L,
            workerDispatcher = Dispatchers.Unconfined,
        )
        val result = scheduler.submit(
            AgentAction(AgentAction.CLICK_NODE, target = "x"),
            timeoutMs = 50L,
        )
        assertFalse(result.success)
        assertTrue(result.timedOut)
        assertTrue(result.error?.contains("timeout") == true)
    }

    @Test
    fun cancelAll_rejectsSubsequent() = runBlocking {
        val exec = SlowExecutor()
        val scheduler = ActionScheduler(
            executor = exec,
            throttleMs = 0L,
            workerDispatcher = Dispatchers.Unconfined,
        )
        scheduler.cancelAll("user_stop")
        val result = scheduler.submit(AgentAction(AgentAction.CLICK_NODE, target = "a"))
        assertTrue(result.cancelled)
        assertFalse(result.success)
        scheduler.reset()
        val ok = scheduler.submit(AgentAction(AgentAction.CLICK_NODE, target = "b"))
        assertTrue(ok.success)
    }

    @Test
    fun clickTimeoutDefault_is3s() {
        assertEquals(
            3_000L,
            ActionSchedulerDefaults.timeoutFor(AgentAction(AgentAction.CLICK_NODE)),
        )
    }
}
