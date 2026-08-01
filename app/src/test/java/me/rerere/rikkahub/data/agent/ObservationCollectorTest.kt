package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.accessibility.ObservationCache
import me.rerere.rikkahub.data.accessibility.UISnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationCollectorTest {

    @Test
    fun afterAction_samePage_staysL0_noSnapshot() = runBlocking {
        val src = ForegroundCacheSource(
            packageName = { "com.tencent.mm" },
            activityName = { "LauncherUI" },
            isFresh = { true },
        )
        var lightCalls = 0
        val collector = ObservationCollector(
            foregroundSource = { src },
            lightSnapshot = {
                lightCalls++
                error("should not dump on L0 same page")
            },
            observationCacheProvider = { ObservationCache(maxSize = 4) },
        )
        val result = collector.collectAfterAction(
            previousPackage = "com.tencent.mm",
            previousActivity = "LauncherUI",
        )
        assertEquals(ObservationLevel.L0, result.compact.level)
        assertEquals("com.tencent.mm", result.compact.packageName)
        assertNull(result.snapshot)
        assertEquals(0, lightCalls)
    }

    @Test
    fun afterAction_packageChange_escalatesL1_withSnapshot() = runBlocking {
        val src = ForegroundCacheSource(
            packageName = { "com.tencent.mm" },
            activityName = { "com.tencent.mm.ui.LauncherUI" },
            isFresh = { true },
        )
        var lightCalls = 0
        val shared = ObservationCache(maxSize = 4)
        val collector = ObservationCollector(
            foregroundSource = { src },
            lightSnapshot = {
                lightCalls++
                UISnapshot(
                    page = "com.tencent.mm.ui.LauncherUI",
                    packageName = "com.tencent.mm",
                    timestamp = 1L,
                    nodeCount = 3,
                )
            },
            observationCacheProvider = { shared },
        )
        val result = collector.collectAfterAction(
            previousPackage = "com.android.launcher3",
            previousActivity = "Launcher",
        )
        assertEquals(ObservationLevel.L1, result.compact.level)
        assertEquals("com.tencent.mm", result.compact.packageName)
        assertTrue(result.snapshot != null)
        assertEquals(1, lightCalls)

        // Shared ObservationCache hit — no extra dump
        val cached = collector.collect(
            ObservationCollector.Request(maxLevel = ObservationLevel.L1)
        )
        assertTrue(cached.compact.fromCache)
        assertEquals(1, lightCalls)
    }

    @Test
    fun agentStateManager_syncFromTask_updatesOutlet() = runBlocking {
        val bus = AgentRuntimeEventBus()
        val mgr = AgentStateManager(runtimeEventBus = bus)
        val task = TaskState(
            taskId = "t1",
            goal = "打开微信",
            state = AgentPhase.EXECUTING,
            packageName = "com.tencent.mm",
            currentPage = "LauncherUI",
        )
        mgr.syncFromTask(
            task = task,
            observation = CompactObservation(
                level = ObservationLevel.L0,
                packageName = "com.tencent.mm",
                activityName = "LauncherUI",
            ),
            lastAction = AgentAction(AgentAction.OPEN_APP, target = "com.tencent.mm"),
            lastActionResult = ActionExecuteResult(true, "Launched"),
            emitEvent = true,
        )
        val snap = mgr.snapshot()!!
        assertEquals("t1", snap.taskId)
        assertEquals("com.tencent.mm", snap.currentPackage)
        assertEquals(AgentPhase.EXECUTING, snap.phase)
        assertEquals(AgentAction.OPEN_APP, snap.lastAction?.action)
    }
}
