package me.rerere.rikkahub.data.accessibility

import java.util.LinkedHashMap

/**
 * Caches recent page observations keyed by package + activity + tree hash.
 * Reuse when the surface fingerprint is unchanged.
 */
class ObservationCache(
    private val maxSize: Int = 24,
) {
    data class Entry(
        val packageName: String,
        val page: String,
        val treeHash: String,
        val level: PerceptionLevel,
        val incremental: IncrementalUISnapshot,
        val snapshot: UISnapshot?,
        val observation: UnifiedObservation,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private val map = object : LinkedHashMap<String, Entry>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxSize
    }

    fun cacheKey(packageName: String, page: String, treeHash: String): String =
        "${packageName.trim()}|${page.trim()}|${treeHash.trim()}"

    fun get(packageName: String, page: String, treeHash: String): Entry? = synchronized(lock) {
        if (treeHash.isBlank()) return null
        map[cacheKey(packageName, page, treeHash)]
    }

    /**
     * Return cached entry when package+page match and hash equals last known for that page.
     */
    fun getIfUnchanged(packageName: String, page: String, treeHash: String): Entry? {
        if (treeHash.isBlank()) return null
        return get(packageName, page, treeHash)
    }

    fun put(entry: Entry) = synchronized(lock) {
        map[cacheKey(entry.packageName, entry.page, entry.treeHash)] = entry
    }

    fun clear() = synchronized(lock) { map.clear() }

    fun size(): Int = synchronized(lock) { map.size }

    companion object {
        /**
         * Cheap fingerprint — does not require walking a huge tree.
         * Uses package, page, node count, and a short sample of labels/ids.
         */
        fun computeTreeHash(
            packageName: String,
            page: String,
            nodeCount: Int,
            sample: List<String>,
        ): String {
            var h = 2166136261L
            fun mix(s: String) {
                for (c in s) {
                    h = h xor c.code.toLong()
                    h *= 16777619L
                }
                h = h xor 0x9eL
            }
            mix(packageName)
            mix(page)
            mix(nodeCount.toString())
            sample.take(12).forEach(::mix)
            return h.toULong().toString(16)
        }

        fun sampleFromSnapshot(snapshot: UISnapshot): List<String> {
            return snapshot.flattenNodes().asSequence()
                .filter {
                    it.text.isNotBlank() || it.viewId.isNotBlank() || it.clickable || it.editable
                }
                .take(16)
                .map {
                    "${it.viewId.substringAfterLast('/')}|${it.text.take(24)}|${it.clickable}"
                }
                .toList()
        }

        fun sampleFromIncremental(inc: IncrementalUISnapshot): List<String> {
            return inc.changedNodes.take(16).map {
                "${it.viewId}|${it.text.take(24)}|${it.actionable}"
            }
        }

        fun hashSnapshot(snapshot: UISnapshot): String = computeTreeHash(
            packageName = snapshot.packageName,
            page = snapshot.page,
            nodeCount = snapshot.nodeCount,
            sample = sampleFromSnapshot(snapshot),
        )
    }
}
