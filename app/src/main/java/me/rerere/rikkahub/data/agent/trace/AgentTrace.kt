package me.rerere.rikkahub.data.agent.trace

import android.os.SystemClock
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One timed step inside an [AgentTrace].
 */
@Serializable
data class TraceStep(
    val name: String,
    val cost: Long,
    val detail: String = "",
)

/**
 * Per-task performance timeline for bottleneck analysis.
 *
 * Example:
 * ```
 * { "task":"发送消息", "steps":[{"name":"perception","cost":120}, ...] }
 * ```
 */
@Serializable
data class AgentTrace(
    val task: String,
    val taskId: String = "",
    val steps: List<TraceStep> = emptyList(),
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
) {
    val totalCost: Long
        get() = if (finishedAt > startedAt) finishedAt - startedAt else steps.sumOf { it.cost }

    fun bottleneck(): TraceStep? = steps.maxByOrNull { it.cost }

    fun toJson(): String = TraceJson.json.encodeToString(this)

    companion object {
        const val UI_TREE = "ui_tree"
        const val OCR = "ocr"
        const val LLM = "llm"
        const val PERCEPTION = "perception"
        const val PLANNER = "planner"
        const val ACTION = "action"
        const val VERIFY = "verify"
    }
}

internal object TraceJson {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

/**
 * Ultra-light tracer: [SystemClock.elapsedRealtime] + in-memory ring buffer.
 * Disable via [enabled] for zero instrumentation cost beyond a volatile read.
 */
class AgentTracer(
    private val maxRecent: Int = 16,
    private val maxStepsPerTask: Int = 64,
) {
    private val enabledFlag = AtomicBoolean(true)
    private val lock = Any()
    private var currentTask: String = ""
    private var currentTaskId: String = ""
    private var currentStartedAt: Long = 0L
    private val currentSteps = ArrayList<TraceStep>(32)
    private val recent = ArrayDeque<AgentTrace>(maxRecent)

    @Volatile
    var lastTrace: AgentTrace? = null
        private set

    var enabled: Boolean
        get() = enabledFlag.get()
        set(value) {
            enabledFlag.set(value)
        }

    fun begin(taskId: String, task: String) {
        if (!enabledFlag.get()) return
        synchronized(lock) {
            currentTaskId = taskId
            currentTask = task
            currentStartedAt = SystemClock.elapsedRealtime()
            currentSteps.clear()
        }
    }

    fun record(name: String, costMs: Long, detail: String = "") {
        if (!enabledFlag.get()) return
        if (costMs < 0) return
        synchronized(lock) {
            if (currentSteps.size >= maxStepsPerTask) return
            currentSteps += TraceStep(name = name, cost = costMs, detail = detail)
        }
    }

    inline fun <T> measure(name: String, detail: String = "", block: () -> T): T {
        if (!enabled) return block()
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            record(name, SystemClock.elapsedRealtime() - start, detail)
        }
    }

    suspend inline fun <T> measureSuspend(name: String, detail: String = "", block: suspend () -> T): T {
        if (!enabled) return block()
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            record(name, SystemClock.elapsedRealtime() - start, detail)
        }
    }

    fun finish(logSummary: Boolean = true): AgentTrace? {
        if (!enabledFlag.get()) return null
        val trace = synchronized(lock) {
            if (currentTask.isEmpty() && currentSteps.isEmpty()) return null
            val t = AgentTrace(
                task = currentTask,
                taskId = currentTaskId,
                steps = currentSteps.toList(),
                startedAt = currentStartedAt,
                finishedAt = SystemClock.elapsedRealtime(),
            )
            currentTask = ""
            currentTaskId = ""
            currentSteps.clear()
            while (recent.size >= maxRecent) recent.removeFirst()
            recent.addLast(t)
            lastTrace = t
            t
        }
        if (logSummary && trace != null) {
            val top = trace.bottleneck()
            Log.i(
                TAG,
                "trace task=${trace.task} total=${trace.totalCost}ms " +
                    "bottleneck=${top?.name}:${top?.cost}ms steps=${trace.steps.size}",
            )
        }
        return trace
    }

    fun recentTraces(): List<AgentTrace> = synchronized(lock) { recent.toList() }

    fun clear() = synchronized(lock) {
        currentSteps.clear()
        currentTask = ""
        recent.clear()
        lastTrace = null
    }

    companion object {
        private const val TAG = "AgentTrace"

        @Volatile
        var instance: AgentTracer? = null
            private set
    }

    init {
        instance = this
    }
}
