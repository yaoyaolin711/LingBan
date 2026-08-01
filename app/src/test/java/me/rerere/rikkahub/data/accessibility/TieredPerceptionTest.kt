package me.rerere.rikkahub.data.accessibility

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TieredPerceptionTest {

    @Test
    fun observationCache_reusesSameHash() {
        val cache = ObservationCache()
        val obs = UnifiedObservation(
            page = "MainActivity",
            packageName = "com.example",
            fusedElements = emptyList(),
        )
        val inc = IncrementalUISnapshot(
            packageName = "com.example",
            page = "MainActivity",
            treeHash = "abc123",
            changedNodes = listOf(ChangedNode(nodeId = "d1", text = "发送", actionable = true)),
        )
        cache.put(
            ObservationCache.Entry(
                packageName = "com.example",
                page = "MainActivity",
                treeHash = "abc123",
                level = PerceptionLevel.L1_A11Y,
                incremental = inc,
                snapshot = null,
                observation = obs,
            )
        )
        val hit = cache.getIfUnchanged("com.example", "MainActivity", "abc123")
        assertNotNull(hit)
        assertEquals(PerceptionLevel.L1_A11Y, hit!!.level)
        assertEquals(null, cache.getIfUnchanged("com.example", "MainActivity", "other"))
    }

    @Test
    fun afterAction_neverEscalatesBeyondL1() = runBlocking {
        var ocrCalls = 0
        val engine = TieredPerceptionEngine(
            cache = ObservationCache(),
            lightSnapshot = {
                UISnapshot(
                    page = "Chat",
                    packageName = "com.chat",
                    timestamp = 1L,
                    nodeCount = 0,
                )
            },
            ocrProvider = {
                ocrCalls++
                "test" to emptyList()
            },
        )
        // Seed insufficient L1 so OCR would normally fire
        engine.onAgentEvent(
            AgentEvent(
                eventType = AgentEvent.CONTENT_CHANGED,
                packageName = "com.chat",
                activityName = "Chat",
            )
        )
        val result = engine.observe(
            PerceptionRequest(
                maxLevel = PerceptionLevel.L3_VISION,
                afterAction = true,
                minUsefulNodes = 99,
            )
        )
        assertEquals(PerceptionLevel.L1_A11Y, result.level)
        assertEquals(0, ocrCalls)
        assertTrue(result.reason.contains("after_action") || result.reason.contains("l1"))
    }

    @Test
    fun event_buildsIncrementalChangedNodes() {
        val engine = TieredPerceptionEngine(
            cache = ObservationCache(),
            lightSnapshot = {
                UISnapshot(page = "", packageName = "", timestamp = 0L)
            },
        )
        engine.onAgentEvent(
            AgentEvent(
                eventType = AgentEvent.VIEW_CLICKED,
                packageName = "com.app",
                activityName = "com.app.Main",
                affectedNode = AffectedNode(text = "发送", className = "Button"),
            )
        )
        val inc = engine.lastIncrementalSnapshot()
        assertEquals("com.app", inc.packageName)
        assertTrue(inc.changedNodes.any { it.text == "发送" })
        assertTrue(inc.treeHash.isNotBlank())
    }

    @Test
    fun cacheHit_onSecondObserve() = runBlocking {
        val snap = UISnapshot(
            page = "Main",
            packageName = "com.x",
            timestamp = 1L,
            root = UiTreeNode(
                nodeId = "n0",
                text = "OK",
                clickable = true,
                bounds = UiBounds(0, 0, 10, 10),
            ),
            nodeCount = 1,
        )
        val engine = TieredPerceptionEngine(
            cache = ObservationCache(),
            lightSnapshot = { snap },
        )
        val first = engine.observe(PerceptionRequest(maxLevel = PerceptionLevel.L1_A11Y))
        val second = engine.observe(PerceptionRequest(maxLevel = PerceptionLevel.L1_A11Y))
        assertEquals(false, first.fromCache)
        assertEquals(true, second.fromCache)
        assertEquals(first.treeHash, second.treeHash)
    }
}
