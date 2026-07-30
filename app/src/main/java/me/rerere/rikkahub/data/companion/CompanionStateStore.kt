package me.rerere.rikkahub.data.companion

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.companion.model.CompanionState
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class CompanionStateStore(
    context: Context,
    private val json: Json,
) {
    private val stateDir = File(context.filesDir, "companion/states").apply { mkdirs() }
    private val mutexMap = ConcurrentHashMap<String, Mutex>()
    private val memoryCache = ConcurrentHashMap<String, CompanionState>()

    suspend fun getState(conversationId: Uuid): CompanionState {
        val key = conversationId.toString()
        memoryCache[key]?.let { return it }
        return mutexFor(key).withLock {
            memoryCache[key] ?: loadFromDisk(key).also { memoryCache[key] = it }
        }
    }

    suspend fun saveState(conversationId: Uuid, state: CompanionState) {
        val key = conversationId.toString()
        mutexFor(key).withLock {
            val file = stateFile(key)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(CompanionState.serializer(), state))
            memoryCache[key] = state
        }
    }

    suspend fun deleteState(conversationId: Uuid) {
        val key = conversationId.toString()
        mutexFor(key).withLock {
            stateFile(key).delete()
            memoryCache.remove(key)
        }
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

    private fun stateFile(key: String) = File(stateDir, "$key.json")

    private fun mutexFor(key: String): Mutex {
        return mutexMap.getOrPut(key) { Mutex() }
    }
}
