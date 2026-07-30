package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.companion.model.CompanionPromptBundle
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class PromptCache {
    private val cache = ConcurrentHashMap<String, CompanionPromptBundle>()
    private val conversationKeys = ConcurrentHashMap<Uuid, String>()

    fun get(cacheKey: String): CompanionPromptBundle? = cache[cacheKey]

    fun put(conversationId: Uuid, bundle: CompanionPromptBundle): CompanionPromptBundle {
        cache[bundle.cacheKey] = bundle
        conversationKeys[conversationId]?.let { previousKey ->
            if (previousKey != bundle.cacheKey) {
                cache.remove(previousKey)
            }
        }
        conversationKeys[conversationId] = bundle.cacheKey
        return bundle
    }

    fun invalidate(conversationId: Uuid) {
        conversationKeys.remove(conversationId)?.let(cache::remove)
    }
}
