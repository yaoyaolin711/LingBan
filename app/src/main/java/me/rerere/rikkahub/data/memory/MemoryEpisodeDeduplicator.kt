package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntityType

/**
 * Episode deduplication (no embeddings):
 * - First prune by entity overlap (PERSON/PLACE/PREFERENCE)
 * - Fallback to content similarity (character 3-gram Jaccard) among recent candidates
 */
object MemoryEpisodeDeduplicator {
    private const val MAX_CANDIDATES = 25

    // Conservative defaults: avoid accidental merges.
    private const val ENTITY_PRUNE_MIN_OVERLAP = 1
    private const val DUP_SCORE_ENTITY = 0.78
    private const val DUP_SCORE_NO_ENTITY = 0.84

    fun findDuplicateEpisode(
        newContent: String,
        newTopicKey: String?,
        candidates: List<MemoryEntity>,
    ): MemoryEntity? {
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return null

        val newExtracted = MemoryRuleIndexer.extract(trimmed, newTopicKey)
        val newEntities = newExtracted.entities
            .filter { it.type in setOf(MemoryEntityType.PERSON, MemoryEntityType.PLACE, MemoryEntityType.PREFERENCE) }
            .map { it.name }
            .filter { it.isNotBlank() }
            .toSet()

        val recent = candidates.take(MAX_CANDIDATES)
        if (recent.isEmpty()) return null

        val newNgrams = char3grams(normalizeForSimilarity(trimmed))

        var best: MemoryEntity? = null
        var bestScore = 0.0

        for (cand in recent) {
            val candExtracted = MemoryRuleIndexer.extract(cand.content.trim(), cand.topicKey)
            val candEntities = candExtracted.entities
                .filter { it.type in setOf(MemoryEntityType.PERSON, MemoryEntityType.PLACE, MemoryEntityType.PREFERENCE) }
                .map { it.name }
                .filter { it.isNotBlank() }
                .toSet()

            val entityOverlap = if (newEntities.isNotEmpty() && candEntities.isNotEmpty()) {
                (newEntities intersect candEntities).size
            } else {
                0
            }

            // Entity-first prune: if we have entity signal, require overlap for high score candidates.
            if (newEntities.isNotEmpty() && entityOverlap < ENTITY_PRUNE_MIN_OVERLAP) continue

            val candNgrams = char3grams(normalizeForSimilarity(cand.content))
            val score = jaccard(newNgrams, candNgrams)

            val threshold = if (entityOverlap >= ENTITY_PRUNE_MIN_OVERLAP) DUP_SCORE_ENTITY else DUP_SCORE_NO_ENTITY
            if (score >= threshold && score > bestScore) {
                best = cand
                bestScore = score
            }
        }

        // If entity signal existed but nothing matched, allow fallback by similarity only.
        if (best == null && newEntities.isNotEmpty()) {
            for (cand in recent) {
                val candNgrams = char3grams(normalizeForSimilarity(cand.content))
                val score = jaccard(newNgrams, candNgrams)
                if (score >= DUP_SCORE_NO_ENTITY && score > bestScore) {
                    best = cand
                    bestScore = score
                }
            }
        }

        return best
    }

    private fun normalizeForSimilarity(s: String): String =
        s.trim()
            .lowercase()
            .replace(Regex("""[\s\p{Punct}]+"""), "")

    private fun char3grams(s: String): Set<String> {
        val t = s
        if (t.length < 3) return setOf(t)
        val out = HashSet<String>()
        for (i in 0..(t.length - 3)) {
            out.add(t.substring(i, i + 3))
        }
        return out
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a intersect b
        val union = a union b
        return intersection.size.toDouble() / union.size.toDouble()
    }
}

