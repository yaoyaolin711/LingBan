package me.rerere.rikkahub.data.agent.memory

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.agent.TaskState
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight Agent memory API.
 *
 * - [write]/[read] hit in-memory short-term cache only (hot path safe).
 * - Durable persistence is async and never blocks Action execution.
 * - Do NOT call [readDurable] / DB on every Action.
 */
interface MemoryManager {
    /** Immediate short-term write. Optionally schedules async durable flush. */
    fun write(key: String, value: String, durable: Boolean = false)

    /** Short-term read only — never touches SQLite. */
    fun read(key: String): String?

    /** Fire-and-forget durable save (updates short-term first). */
    fun writeAsync(key: String, value: String)

    /**
     * Explicit long-term read (IO). Use sparingly — e.g. once at task start.
     * Never call from Action hot path.
     */
    suspend fun readDurable(key: String): String?

    fun clearShortTerm()

    fun writeTaskState(state: TaskState, durable: Boolean = false)

    fun readTaskState(): TaskState?
}

data class MemoryEntry(
    val value: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Short-term: ConcurrentHashMap.
 * Long-term: Room SQLite on [Dispatchers.IO], debounced.
 */
class AgentMemoryManager(
    private val scope: CoroutineScope,
    private val dao: AgentMemoryDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val flushDebounceMs: Long = 400L,
) : MemoryManager {

    companion object {
        private const val TAG = "AgentMemory"
        const val KEY_TASK_CURRENT = "task:current"
        const val CATEGORY_TASK = "task"
        const val CATEGORY_GENERAL = "general"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val shortTerm = ConcurrentHashMap<String, MemoryEntry>()
    private val pendingDurable = ConcurrentHashMap<String, String>()
    private val flushMutex = Mutex()
    private var flushJob: Job? = null

    override fun write(key: String, value: String, durable: Boolean) {
        shortTerm[key] = MemoryEntry(value)
        if (durable) {
            pendingDurable[key] = value
            scheduleFlush()
        }
    }

    override fun read(key: String): String? = shortTerm[key]?.value

    override fun writeAsync(key: String, value: String) {
        write(key, value, durable = true)
    }

    override suspend fun readDurable(key: String): String? {
        shortTerm[key]?.value?.let { return it }
        val db = dao ?: return null
        return runCatching {
            withContextIo {
                db.getByKey(key)?.value?.also { loaded ->
                    shortTerm.putIfAbsent(key, MemoryEntry(loaded))
                }
            }
        }.getOrNull()
    }

    override fun clearShortTerm() {
        shortTerm.clear()
    }

    override fun writeTaskState(state: TaskState, durable: Boolean) {
        val payload = runCatching { json.encodeToString(state) }.getOrElse {
            Log.w(TAG, "encode TaskState failed", it)
            return
        }
        write(KEY_TASK_CURRENT, payload, durable = durable)
    }

    override fun readTaskState(): TaskState? {
        val raw = read(KEY_TASK_CURRENT) ?: return null
        return runCatching { json.decodeFromString<TaskState>(raw) }.getOrNull()
    }

    /** Snapshot size for diagnostics — does not hit DB. */
    fun shortTermSize(): Int = shortTerm.size

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = scope.launch(ioDispatcher) {
            delay(flushDebounceMs)
            flushPending()
        }
    }

    private suspend fun flushPending() {
        val db = dao ?: return
        flushMutex.withLock {
            if (pendingDurable.isEmpty()) return
            val batch = HashMap(pendingDurable)
            pendingDurable.keys.removeAll(batch.keys)
            batch.forEach { (key, value) ->
                runCatching {
                    db.upsert(
                        AgentMemoryEntity(
                            key = key,
                            value = value,
                            category = if (key.startsWith("task:")) CATEGORY_TASK else CATEGORY_GENERAL,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }.onFailure {
                    Log.w(TAG, "durable write failed key=$key", it)
                    pendingDurable.putIfAbsent(key, value)
                }
            }
        }
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(ioDispatcher) { block() }
}
