package me.rerere.rikkahub.data.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTreeNodeTest {

    private fun sampleTree(): UiTreeNode {
        val childA = UiTreeNode(
            nodeId = "n1",
            text = "OK",
            className = "android.widget.Button",
            clickable = true,
            bounds = UiBounds(10, 20, 110, 70),
            parentNodeId = "n0",
        )
        val childB = UiTreeNode(
            nodeId = "n2",
            text = "Name",
            className = "android.widget.EditText",
            editable = true,
            focused = true,
            bounds = UiBounds(10, 80, 300, 140),
            parentNodeId = "n0",
        )
        return UiTreeNode(
            nodeId = "n0",
            className = "android.widget.FrameLayout",
            packageName = "com.example.app",
            bounds = UiBounds(0, 0, 1080, 2400),
            children = listOf(childA, childB),
        )
    }

    @Test
    fun flatten_preservesHierarchyOrder() {
        val flat = sampleTree().flatten()
        assertEquals(listOf("n0", "n1", "n2"), flat.map { it.nodeId })
    }

    @Test
    fun findByViewId_and_nodeId() {
        val root = sampleTree().copy(
            children = sampleTree().children.map {
                if (it.nodeId == "n1") it.copy(viewId = "com.example.app:id/btn_ok") else it
            },
        )
        assertEquals("n1", root.findById("n1")?.nodeId)
        assertEquals("n1", root.findByViewId("btn_ok")?.nodeId)
        assertNull(root.findById("missing"))
    }

    @Test
    fun snapshot_toObservation_mapsElements() {
        val snapshot = UISnapshot(
            page = "com.example.app.MainActivity",
            packageName = "com.example.app",
            timestamp = 123L,
            root = sampleTree(),
            nodeCount = 3,
        )
        val obs = snapshot.toObservation()
        assertEquals(UIObservation.SOURCE_ACCESSIBILITY, obs.source)
        assertEquals(3, obs.elements.size)
        assertEquals("com.example.app.MainActivity", obs.page)
        assertEquals(123L, obs.timestamp)
        assertTrue(obs.elements.any { it.clickable && it.text == "OK" })
        assertEquals(60, obs.elements.first { it.id == "n1" }.x) // (10+110)/2
        assertEquals(45, obs.elements.first { it.id == "n1" }.y) // (20+70)/2
    }

    @Test
    fun fuse_combinesAccessibilityAndOcr() {
        val a11y = UIObservation(
            source = UIObservation.SOURCE_ACCESSIBILITY,
            elements = listOf(
                UIElement(id = "n0", text = "Login", clickable = true, x = 100, y = 200),
            ),
            page = "com.example.LoginActivity",
            packageName = "com.example",
            timestamp = 10L,
        )
        val ocr = UIObservation.fromOcrElements(
            elements = listOf(
                UIElement(id = "ocr0", text = "Forgot password?", x = 100, y = 400),
            ),
            timestamp = 20L,
        )
        val fused = UIObservation.fuse(a11y, ocr)
        assertEquals(UIObservation.SOURCE_FUSED, fused.source)
        assertEquals(2, fused.elements.size)
        assertEquals(UIObservation.SOURCE_OCR, fused.elements[1].source)
        assertEquals(20L, fused.timestamp)
        assertEquals("com.example.LoginActivity", fused.page)
    }

    @Test
    fun observationJson_containsSourceAndElements() {
        val obs = UIObservation(
            source = UIObservation.SOURCE_ACCESSIBILITY,
            elements = listOf(
                UIElement(
                    id = "n0",
                    text = "Hi",
                    bounds = UiBounds(1, 2, 3, 4),
                    x = 2,
                    y = 3,
                ),
            ),
            page = "com.foo.Bar",
            packageName = "com.foo",
            timestamp = 99L,
        )
        val raw = AccessibilityJson.observationForAgent(obs, includeTree = false)
        assertTrue(raw.contains("\"source\":\"accessibility\""))
        assertTrue(raw.contains("\"elements\""))
        assertTrue(raw.contains("com.foo.Bar"))
        assertFalse(raw.contains("\"tree\""))
    }

    @Test
    fun snapshotForAgent_exposesPageTimestampNodes() {
        val snapshot = UISnapshot(
            page = "com.example.MainActivity",
            packageName = "com.example",
            timestamp = 42L,
            root = sampleTree(),
            nodeCount = 3,
        )
        val raw = AccessibilityJson.snapshotForAgent(snapshot)
        assertTrue(raw.contains("\"page\":\"com.example.MainActivity\""))
        assertTrue(raw.contains("\"timestamp\":42"))
        assertTrue(raw.contains("\"nodes\""))
        assertTrue(raw.contains("\"children\""))
    }
}
