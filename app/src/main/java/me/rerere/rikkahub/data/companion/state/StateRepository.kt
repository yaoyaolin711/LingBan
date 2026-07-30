package me.rerere.rikkahub.data.companion.state

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

interface StateRepository {
    suspend fun get(conversationId: Uuid): CompanionState

    suspend fun save(
        conversationId: Uuid,
        state: CompanionState,
    )

    suspend fun delete(conversationId: Uuid)

    suspend fun clearCache(conversationId: Uuid? = null)
}

class JsonFileStateRepository(
    context: Context,
    private val json: Json,
) : StateRepository {
    private val stateDir = File(context.filesDir, "companion/state-engine").apply { mkdirs() }
    private val memoryCache = ConcurrentHashMap<String, CompanionState>()
    private val mutexMap = ConcurrentHashMap<String, Mutex>()

    override suspend fun get(conversationId: Uuid): CompanionState {
        val key = conversationId.toString()
        memoryCache[key]?.let { return it }
        return mutexFor(key).withLock {
            memoryCache[key] ?: loadFromDisk(key).also { memoryCache[key] = it }
        }
    }

    override suspend fun save(
        conversationId: Uuid,
        state: CompanionState,
    ) {
        val key = conversationId.toString()
        mutexFor(key).withLock {
            val file = stateFile(key)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(CompanionState.serializer(), state))
            memoryCache[key] = state
        }
    }

    override suspend fun delete(conversationId: Uuid) {
        val key = conversationId.toString()
        mutexFor(key).withLock {
            stateFile(key).delete()
            memoryCache.remove(key)
        }
    }

    override suspend fun clearCache(conversationId: Uuid?) {
        if (conversationId == null) {
            memoryCache.clear()
            return
        }
        memoryCache.remove(conversationId.toString())
    }

    private fun loadFromDisk(key: String): CompanionState {
        val file = stateFile(key)
        if (!file.exists()) return CompanionState()
        return runCatching {
            json.decodeFromString(CompanionState.serializer(), file.readText())
        }.getOrElse {
            CompanionState()
        }
    }

    private fun stateFile(key: String): File = File(stateDir, "$key.json")

    private fun mutexFor(key: String): Mutex {
        return mutexMap.getOrPut(key) { Mutex() }
    }
}
