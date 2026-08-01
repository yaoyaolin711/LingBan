package me.rerere.rikkahub.data.agent.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.agent.AgentPhase
import me.rerere.rikkahub.data.agent.TaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMemoryManagerTest {

    @Test
    fun writeRead_usesShortTermOnly() {
        val mem = AgentMemoryManager(
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            dao = null,
            flushDebounceMs = 10L,
        )
        mem.write("k1", "v1")
        assertEquals("v1", mem.read("k1"))
        assertNull(mem.read("missing"))
    }

    @Test
    fun taskState_roundTrip_shortTerm() {
        val mem = AgentMemoryManager(
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            dao = null,
        )
        val state = TaskState(
            taskId = "t1",
            goal = "打开微信",
            state = AgentPhase.EXECUTING,
            currentStep = 2,
        )
        mem.writeTaskState(state, durable = false)
        val loaded = mem.readTaskState()
        assertEquals("t1", loaded?.taskId)
        assertEquals("打开微信", loaded?.goal)
        assertEquals(2, loaded?.currentStep)
    }

    @Test
    fun readDurable_withoutDao_fallsBackToShortTerm() = runBlocking {
        val mem = AgentMemoryManager(
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            dao = null,
        )
        mem.write("x", "y")
        assertEquals("y", mem.readDurable("x"))
        assertTrue(mem.shortTermSize() >= 1)
    }
}
