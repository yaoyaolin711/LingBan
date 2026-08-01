package me.rerere.rikkahub.data.agent

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Async action queue: priority + timeout + cancel + throttle.
 *
 * Never runs on the main/UI thread — execution uses a dedicated background dispatcher.
 */
class ActionScheduler(
    private val executor: AgentActionExecutor,
    parentScope: CoroutineScope? = null,
    private val throttleMs: Long = ActionSchedulerDefaults.THROTTLE_MS,
    workerDispatcher: CoroutineDispatcher = dedicatedDispatcher(),
) {
    companion object {
        private const val TAG = "ActionScheduler"

        private fun dedicatedDispatcher(): CoroutineDispatcher =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "agent-action-scheduler").apply { isDaemon = true }
            }.asCoroutineDispatcher()
    }

    private val scope = CoroutineScope(
        SupervisorJob(parentScope?.coroutineContext?.get(Job)) + workerDispatcher,
    )

    private val queueMutex = Mutex()
    private val queue = PriorityBlockingQueue<Queued>(11) { a, b ->
        val pr = a.item.priority.rank.compareTo(b.item.priority.rank)
        if (pr != 0) pr else a.seq.compareTo(b.seq)
    }

    private val seqGen = AtomicLong(0)
    private val lastGestureAt = AtomicLong(0)
    private val stopped = AtomicBoolean(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var currentJob: Job? = null

    private data class Queued(
        val seq: Long,
        val item: ScheduledAction,
        val cont: CompletableDeferred<ActionResult>,
    )

    init {
        scope.launch {
            while (isActive) {
                val next = queueMutex.withLock { queue.poll() }
                if (next == null) {
                    wake.receiveCatching()
                    continue
                }
                if (stopped.get() && next.item.priority != ActionPriority.CRITICAL) {
                    next.cont.complete(
                        ActionResult(
                            success = false,
                            error = "cancelled",
                            cancelled = true,
                            actionId = next.item.id,
                        )
                    )
                    continue
                }
                val job = launch {
                    val result = runCatching { executeOne(next.item) }.getOrElse {
                        ActionResult(
                            success = false,
                            error = it.message ?: "execute_error",
                            actionId = next.item.id,
                        )
                    }
                    next.cont.complete(result)
                }
                currentJob = job
                job.join()
                currentJob = null
            }
        }
    }

    /** Enqueue and await result (suspend; does not block UI thread). */
    suspend fun submit(
        action: AgentAction,
        priority: ActionPriority = ActionSchedulerDefaults.priorityFor(action),
        timeoutMs: Long = ActionSchedulerDefaults.timeoutFor(action),
    ): ActionResult {
        if (stopped.get() && priority != ActionPriority.CRITICAL) {
            return ActionResult(success = false, error = "cancelled", cancelled = true)
        }
        val scheduled = ScheduledAction(
            id = UUID.randomUUID().toString(),
            action = action,
            priority = priority,
            timeoutMs = timeoutMs,
        )
        val deferred = CompletableDeferred<ActionResult>()
        queueMutex.withLock {
            queue.offer(Queued(seqGen.incrementAndGet(), scheduled, deferred))
        }
        wake.trySend(Unit)
        return deferred.await()
    }

    /** Fire-and-forget enqueue; returns action id. */
    fun enqueue(
        action: AgentAction,
        priority: ActionPriority = ActionSchedulerDefaults.priorityFor(action),
        timeoutMs: Long = ActionSchedulerDefaults.timeoutFor(action),
        onResult: ((ActionResult) -> Unit)? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        scope.launch {
            val result = submit(action.copy(params = action.params), priority, timeoutMs)
            onResult?.invoke(result.copy(actionId = if (result.actionId.isBlank()) id else result.actionId))
        }
        return id
    }

    /** User stop: cancel current + drain queue. */
    fun cancelAll(reason: String = "cancelled") {
        stopped.set(true)
        currentJob?.cancel()
        scope.launch {
            queueMutex.withLock {
                while (true) {
                    val q = queue.poll() ?: break
                    q.cont.complete(
                        ActionResult(
                            success = false,
                            error = reason,
                            cancelled = true,
                            actionId = q.item.id,
                        )
                    )
                }
            }
        }
        Log.i(TAG, "cancelAll: $reason")
    }

    fun reset() {
        stopped.set(false)
    }

    fun isStopped(): Boolean = stopped.get()

    fun pendingCount(): Int = queue.size

    private suspend fun executeOne(item: ScheduledAction): ActionResult {
        coroutineContext.ensureActive()
        if (stopped.get() && item.priority != ActionPriority.CRITICAL) {
            return ActionResult(
                success = false,
                error = "cancelled",
                cancelled = true,
                actionId = item.id,
            )
        }

        if (ActionSchedulerDefaults.needsThrottle(item.action)) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastGestureAt.get()
            if (elapsed < throttleMs) {
                delay(throttleMs - elapsed)
            }
        }

        coroutineContext.ensureActive()
        val start = System.currentTimeMillis()
        val raw = withTimeoutOrNull(item.timeoutMs) {
            withContext(Dispatchers.Default) {
                executor.execute(item.action)
            }
        }
        val cost = System.currentTimeMillis() - start
        if (ActionSchedulerDefaults.needsThrottle(item.action)) {
            lastGestureAt.set(System.currentTimeMillis())
        }

        if (raw == null) {
            Log.w(TAG, "timeout ${item.action.action} after ${item.timeoutMs}ms")
            me.rerere.rikkahub.data.agent.trace.AgentTracer.instance?.record(
                me.rerere.rikkahub.data.agent.trace.AgentTrace.ACTION,
                cost,
                "timeout:${item.action.action}",
            )
            return ActionResult(
                success = false,
                costTime = cost,
                error = "timeout:${item.timeoutMs}ms",
                timedOut = true,
                actionId = item.id,
            )
        }
        me.rerere.rikkahub.data.agent.trace.AgentTracer.instance?.record(
            me.rerere.rikkahub.data.agent.trace.AgentTrace.ACTION,
            cost,
            item.action.action,
        )
        return ActionResult.fromExecute(raw, cost, item.id)
    }
}
