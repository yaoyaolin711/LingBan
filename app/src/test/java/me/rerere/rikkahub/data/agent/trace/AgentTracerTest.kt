package me.rerere.rikkahub.data.agent.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTracerTest {

    @Test
    fun recordsStepsAndFindsBottleneck() {
        val tracer = AgentTracer()
        tracer.begin("t1", "发送消息")
        tracer.record(AgentTrace.PERCEPTION, 120)
        tracer.record(AgentTrace.PLANNER, 800)
        tracer.record(AgentTrace.ACTION, 200)
        tracer.record(AgentTrace.VERIFY, 50)
        val trace = tracer.finish(logSummary = false)!!
        assertEquals("发送消息", trace.task)
        assertEquals(4, trace.steps.size)
        assertEquals(1170L, trace.steps.sumOf { it.cost })
        // Wall-clock total when finishedAt set
        assertTrue(trace.totalCost >= 0)
        assertEquals(AgentTrace.PLANNER, trace.bottleneck()?.name)
        assertEquals(800L, trace.bottleneck()?.cost)
    }

    @Test
    fun disabled_isNoOp() {
        val tracer = AgentTracer()
        tracer.enabled = false
        tracer.begin("t", "x")
        tracer.record(AgentTrace.ACTION, 999)
        assertEquals(null, tracer.finish(logSummary = false))
    }

    @Test
    fun measure_wrapsBlock() {
        val tracer = AgentTracer()
        tracer.begin("t", "goal")
        val v = tracer.measure(AgentTrace.UI_TREE) { 42 }
        assertEquals(42, v)
        val trace = tracer.finish(logSummary = false)!!
        assertTrue(trace.steps.any { it.name == AgentTrace.UI_TREE })
    }
}
