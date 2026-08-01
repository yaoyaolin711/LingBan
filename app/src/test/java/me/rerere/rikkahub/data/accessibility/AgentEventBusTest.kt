package me.rerere.rikkahub.data.accessibility

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEventBusTest {

    @Test
    fun affectedNode_matchesText() {
        val node = AffectedNode(text = "发送", contentDescription = "send button")
        assertTrue(node.matchesText("发送"))
        assertTrue(node.matchesText("send", partial = true))
        assertFalse(node.matchesText("发送全部", partial = false))
    }

    @Test
    fun eventBus_deliversEvents() = runBlocking {
        val bus = AgentEventBus()
        val emitted = AgentEvent(
            eventType = AgentEvent.PAGE_CHANGED,
            packageName = "com.example",
            activityName = "com.example.MainActivity",
            timestamp = 1L,
        )
        val job = async {
            withTimeout(1_000) {
                bus.events.first { it.eventType == AgentEvent.PAGE_CHANGED }
            }
        }
        delay(30)
        assertTrue(bus.tryEmit(emitted))
        val received = job.await()
        assertEquals("com.example", received.packageName)
        assertEquals("com.example.MainActivity", received.activityName)
    }

    @Test
    fun waitFor_pageChanged_matchesTarget() = runBlocking {
        val bus = AgentEventBus()
        val mgr = AccessibilityEventManager(bus)
        val waiter = async {
            mgr.waitFor(
                WaitCondition.PageChanged(
                    packageName = "com.chat",
                    activityName = "com.chat.ChatActivity",
                ),
                timeoutMs = 2_000L,
            )
        }
        delay(50)
        bus.tryEmit(
            AgentEvent(
                eventType = AgentEvent.PAGE_CHANGED,
                packageName = "com.other",
                activityName = "com.other.OtherActivity",
            )
        )
        bus.tryEmit(
            AgentEvent(
                eventType = AgentEvent.PAGE_CHANGED,
                packageName = "com.chat",
                activityName = "com.chat.ChatActivity",
            )
        )
        val result = waiter.await()
        assertTrue(result.ok)
        assertTrue(result is WaitResult.Success)
        assertEquals("com.chat", (result as WaitResult.Success).event?.packageName)
    }

    @Test
    fun waitFor_textAppears_viaProbe() = runBlocking {
        val bus = AgentEventBus()
        val mgr = AccessibilityEventManager(bus)
        var visible = false
        mgr.attachTextProbe { query, _ -> visible && query == "发送" }

        val waiter = async {
            mgr.waitFor(WaitCondition.TextAppears("发送"), timeoutMs = 2_000L, pollMs = 50L)
        }
        delay(80)
        visible = true
        bus.tryEmit(
            AgentEvent(
                eventType = AgentEvent.CONTENT_CHANGED,
                packageName = "com.chat",
            )
        )
        val result = waiter.await()
        assertTrue(result.ok)
    }

    @Test
    fun waitFor_timeout() = runBlocking {
        val bus = AgentEventBus()
        val mgr = AccessibilityEventManager(bus)
        val result = mgr.waitFor(
            WaitCondition.EventOfType(AgentEvent.VIEW_CLICKED),
            timeoutMs = 200L,
        )
        assertFalse(result.ok)
        assertTrue(result is WaitResult.Timeout)
    }
}
