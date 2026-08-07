package me.rerere.rikkahub.data.memory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.dao.MemoryIndexDAO
import me.rerere.rikkahub.data.db.entity.MemoryEmbedding
import kotlin.math.sqrt

/**
 * Optional embedding client for semantic recall. Return null/empty to fall back to rules.
 */
fun interface MemoryEmbeddingClient {
    suspend fun embed(texts: List<String>): List<FloatArray>?
}

object MemoryEmbeddingMath {
    private val json = Json { ignoreUnknownKeys = true }

    fun contentHash(content: String): String = content.trim().hashCode().toString(16)

    fun encodeVector(vector: FloatArray): String =
        vector.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toString() }

    fun decodeVector(vectorJson: String): FloatArray? = runCatching {
        json.parseToJsonElement(vectorJson).jsonArray
            .map { it.jsonPrimitive.float }
            .toFloatArray()
    }.getOrNull()

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        val denom = sqrt(na) * sqrt(nb)
        if (denom <= 1e-9) return 0f
        return (dot / denom).toFloat().coerceIn(-1f, 1f)
    }
}

/**
 * Cache + score helpers used by [me.rerere.rikkahub.data.repository.MemoryRepository.recall].
 * No embedding config → no-op (pure rule ranking).
 */
class MemorySemanticIndex(
    private val indexDAO: MemoryIndexDAO,
    private val client: MemoryEmbeddingClient? = null,
) {
    suspend fun ensureCached(memoryId: Int, content: String) {
        val embedder = client ?: return
        val hash = MemoryEmbeddingMath.contentHash(content)
        val existing = indexDAO.getEmbedding(memoryId)
        if (existing != null && existing.contentHash == hash) return
        val vectors = runCatching { embedder.embed(listOf(content.take(800))) }.getOrNull() ?: return
        val vector = vectors.firstOrNull() ?: return
        if (vector.isEmpty()) return
        indexDAO.upsertEmbedding(
            MemoryEmbedding(
                memoryId = memoryId,
                contentHash = hash,
                dims = vector.size,
                vectorJson = MemoryEmbeddingMath.encodeVector(vector),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Returns semantic boost 0..40 per memory id. Empty map if unavailable.
     */
    suspend fun scoreBoosts(query: String, memoryIds: List<Int>): Map<Int, Int> {
        val embedder = client ?: return emptyMap()
        val q = query.trim()
        if (q.isEmpty() || memoryIds.isEmpty()) return emptyMap()
        val cached = indexDAO.getEmbeddings(memoryIds)
        if (cached.isEmpty()) return emptyMap()
        val queryVector = runCatching {
            embedder.embed(listOf(q.take(200)))?.firstOrNull()
        }.getOrNull() ?: return emptyMap()
        if (queryVector.isEmpty()) return emptyMap()
        return cached.mapNotNull { row ->
            val vec = MemoryEmbeddingMath.decodeVector(row.vectorJson) ?: return@mapNotNull null
            val sim = MemoryEmbeddingMath.cosine(queryVector, vec)
            if (sim < 0.25f) return@mapNotNull null
            row.memoryId to ((sim * 40f).toInt().coerceIn(0, 40))
        }.toMap()
    }
}
