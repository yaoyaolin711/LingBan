package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.MemoryIndexDAO
import me.rerere.rikkahub.data.db.entity.MemoryEmotionTag
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntityEdge
import me.rerere.rikkahub.data.db.entity.MemoryEntityLink
import me.rerere.rikkahub.data.db.entity.MemoryEntityNode
import me.rerere.rikkahub.data.db.entity.MemoryLayer
import me.rerere.rikkahub.data.db.entity.MemoryRecallMeta
import me.rerere.rikkahub.data.db.entity.MemoryRelationType
import me.rerere.rikkahub.data.db.entity.MemoryStatus
import me.rerere.rikkahub.data.memory.DisabledMemoryRelationEnricher
import me.rerere.rikkahub.data.memory.MemoryEpisodeDeduplicator
import me.rerere.rikkahub.data.memory.MemoryRelationEnricher
import me.rerere.rikkahub.data.memory.MemoryRelationEnrichmentGate
import me.rerere.rikkahub.data.memory.MemoryNameCanonicalizer
import me.rerere.rikkahub.data.memory.MemoryRuleIndexer
import me.rerere.rikkahub.data.memory.MemorySemanticIndex
import me.rerere.rikkahub.data.memory.MemoryTopicKeys
import me.rerere.rikkahub.data.memory.MemoryWriteGate
import me.rerere.rikkahub.data.memory.MemoryWriteGateDecision
import me.rerere.rikkahub.data.model.AssistantMemory

data class MemoryGraphSnapshot(
    val nodes: List<MemoryEntityNode>,
    val links: List<MemoryEntityLink>,
    val edges: List<MemoryEntityEdge> = emptyList(),
    val memoriesById: Map<Int, AssistantMemory>,
)

/** Progressive disclosure subgraph for model consumption (not full-graph injection). */
data class MemoryGraphExpand(
    val hub: String,
    val relatedEntities: List<String>,
    val memories: List<AssistantMemory>,
) {
    fun relationLine(maxRelated: Int = MemoryRepository.EXPAND_MAX_RELATED): String {
        val related = relatedEntities.take(maxRelated)
        return if (related.isEmpty()) hub else "$hub —关联→ ${related.joinToString(", ")}"
    }
}

data class MemorySearchBundle(
    val memories: List<AssistantMemory>,
    val relationSummary: String? = null,
)

data class MemoryTurnHints(
    val memories: List<AssistantMemory>,
    val relationLine: String? = null,
) {
    val isEmpty: Boolean get() = memories.isEmpty() && relationLine.isNullOrBlank()
}

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val indexDAO: MemoryIndexDAO,
    private val semanticIndex: MemorySemanticIndex? = null,
    private val relationEnricher: MemoryRelationEnricher = DisabledMemoryRelationEnricher,
) {
    companion object {
        const val GLOBAL_MEMORY_ID = "__global__"
        const val RECALL_DEFAULT_LIMIT = 3
        const val RECALL_MAX_LIMIT = 3
        const val RECALL_CONTENT_CHAR_CAP = 200
        const val RECALL_TOTAL_CHAR_CAP = 600
        const val PRERETRIEVE_MAX_ITEMS = 2
        const val PRERETRIEVE_TOTAL_CHAR_CAP = 400
        const val EMOTION_MAX_ITEMS = 3
        const val EMOTION_TOTAL_CHAR_CAP = 400
        const val TURN_EXTRA_MEMORY_CHAR_CAP = 800
        const val EXPAND_MAX_RELATED = 5
        const val EXPAND_DEFAULT_DEPTH = 1
        const val EXPAND_MAX_DEPTH = 2
        const val GRAPH_HUBS_MIN_ENTITIES = 3
        const val GRAPH_HUBS_TOP = 5
        const val GRAPH_HUBS_CHAR_CAP = 120

        @Deprecated("Use RECALL_DEFAULT_LIMIT", ReplaceWith("RECALL_DEFAULT_LIMIT"))
        const val SEARCH_DEFAULT_LIMIT = RECALL_DEFAULT_LIMIT

        @Deprecated("Use RECALL_MAX_LIMIT", ReplaceWith("RECALL_MAX_LIMIT"))
        const val SEARCH_MAX_LIMIT = RECALL_MAX_LIMIT

        @Deprecated("Use RECALL_CONTENT_CHAR_CAP", ReplaceWith("RECALL_CONTENT_CHAR_CAP"))
        const val SEARCH_CONTENT_CHAR_CAP = RECALL_CONTENT_CHAR_CAP

        @Deprecated("Use RECALL_TOTAL_CHAR_CAP", ReplaceWith("RECALL_TOTAL_CHAR_CAP"))
        const val SEARCH_TOTAL_CHAR_CAP = RECALL_TOTAL_CHAR_CAP
    }

    private val indexScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var backfillStarted = false

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId, MemoryStatus.ACTIVE)
            .map { entities -> entities.map { it.toModel() } }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        ensureIndexed(assistantId)
        return memoryDAO.getMemoriesOfAssistant(assistantId, MemoryStatus.ACTIVE)
            .map { it.toModel() }
    }

    fun getGlobalMemoriesFlow(): Flow<List<AssistantMemory>> =
        getMemoriesOfAssistantFlow(GLOBAL_MEMORY_ID)

    suspend fun getGlobalMemories(): List<AssistantMemory> {
        return getMemoriesOfAssistant(GLOBAL_MEMORY_ID)
    }

    fun observeEntities(assistantId: String): Flow<List<MemoryEntityNode>> =
        indexDAO.observeEntities(assistantId)

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        val ids = memoryDAO.getAllMemoriesOfAssistant(assistantId).map { it.id }
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
        ids.forEach { id ->
            indexDAO.deleteLinksForMemory(id)
            indexDAO.deleteMeta(id)
        }
    }

    suspend fun updateContent(
        id: Int,
        content: String,
        source: MemoryWriteSource = MemoryWriteSource.USER,
    ): AssistantMemory {
        val old = memoryDAO.getMemoryById(id) ?: error("Memory record #$id not found")
        val now = System.currentTimeMillis()
        val trimmed = content.trim()
        val inferredTopic = MemoryTopicKeys.inferTopicKey(trimmed)
        val gate = MemoryWriteGate.evaluate(trimmed, inferredTopic, source)
        if (gate.decision == MemoryWriteGateDecision.REJECT) {
            return old.toModel().copy(
                content = trimmed,
                topicKey = inferredTopic,
                status = "rejected",
                updatedAt = now,
            )
        }

        if (inferredTopic != null) {
            if (source == MemoryWriteSource.COMPANION) {
                // Companion is supplementary only: never promote to profile HEAD.
                val updated = old.copy(
                    content = trimmed,
                    topicKey = inferredTopic,
                    layer = MemoryLayer.EPISODE,
                    status = MemoryStatus.ACTIVE,
                    updatedAt = now,
                )
                memoryDAO.updateMemory(updated)
                indexMemoryAsync(updated)
                return updated.toModel()
            }

            val existingHead = memoryDAO.getActiveByTopic(old.assistantId, inferredTopic)
            if (existingHead != null) {
                if (existingHead.id != old.id) {
                    // Replace the previous HEAD but preserve it as superseded (traceability).
                    memoryDAO.updateMemory(
                        existingHead.copy(
                            status = MemoryStatus.SUPERSEDED,
                            updatedAt = now,
                            supersedesId = null,
                        )
                    )
                    val updated = old.copy(
                        content = trimmed,
                        topicKey = inferredTopic,
                        layer = MemoryLayer.PROFILE,
                        status = MemoryStatus.ACTIVE,
                        updatedAt = now,
                    )
                    memoryDAO.updateMemory(updated)
                    indexMemoryAsync(updated)
                    return updated.toModel()
                }

                // Editing the current HEAD: archive it first, then create a fresh HEAD row.
                memoryDAO.updateMemory(
                    old.copy(
                        status = MemoryStatus.SUPERSEDED,
                        updatedAt = now,
                        supersedesId = null,
                    )
                )
                val head = MemoryEntity(
                    assistantId = old.assistantId,
                    content = trimmed,
                    topicKey = inferredTopic,
                    layer = MemoryLayer.PROFILE,
                    status = MemoryStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                )
                val id = memoryDAO.insertMemory(head).toInt()
                val saved = head.copy(id = id)
                indexMemoryAsync(saved)
                return saved.toModel()
            }
        }

        val layer = when {
            inferredTopic != null -> MemoryLayer.PROFILE
            old.layer == MemoryLayer.PROFILE && old.topicKey != null -> MemoryLayer.PROFILE
            else -> MemoryLayer.EPISODE
        }
        val topicKey = inferredTopic ?: old.topicKey?.takeIf { layer == MemoryLayer.PROFILE }

        val updated = old.copy(
            content = trimmed,
            topicKey = topicKey,
            layer = layer,
            status = MemoryStatus.ACTIVE,
            updatedAt = now,
        )
        memoryDAO.updateMemory(updated)
        indexMemoryAsync(updated)
        return updated.toModel()
    }

    suspend fun addMemory(
        assistantId: String,
        content: String,
        source: MemoryWriteSource = MemoryWriteSource.USER,
    ): AssistantMemory {
        val now = System.currentTimeMillis()
        val trimmed = content.trim()
        val topicKey = MemoryTopicKeys.inferTopicKey(trimmed)
        val gate = MemoryWriteGate.evaluate(trimmed, topicKey, source)
        if (gate.decision == MemoryWriteGateDecision.REJECT) {
            return AssistantMemory(
                id = -1,
                content = trimmed,
                topicKey = topicKey,
                layer = if (topicKey != null) MemoryLayer.PROFILE else MemoryLayer.EPISODE,
                status = "rejected",
                createdAt = now,
                updatedAt = now,
            )
        }

        if (topicKey != null) {
            if (source == MemoryWriteSource.COMPANION) {
                // Companion is supplementary only: store as episode note instead of profile HEAD.
                val episodeCandidates = memoryDAO
                    .getMemoriesOfAssistant(assistantId, MemoryStatus.ACTIVE)
                    .filter { it.layer != MemoryLayer.PROFILE }
                    .take(25)

                val duplicate = MemoryEpisodeDeduplicator.findDuplicateEpisode(
                    newContent = trimmed,
                    newTopicKey = topicKey,
                    candidates = episodeCandidates,
                )
                if (duplicate != null) {
                    val updated = duplicate.copy(
                        content = trimmed,
                        updatedAt = now,
                    )
                    memoryDAO.updateMemory(updated)
                    indexMemoryAsync(updated)
                    return updated.toModel()
                }

                val episode = MemoryEntity(
                    assistantId = assistantId,
                    content = trimmed,
                    topicKey = topicKey,
                    layer = MemoryLayer.EPISODE,
                    status = MemoryStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                )
                val id = memoryDAO.insertMemory(episode).toInt()
                val saved = episode.copy(id = id)
                indexMemoryAsync(saved)
                return saved.toModel()
            }

            val existing = memoryDAO.getActiveByTopic(assistantId, topicKey)
            if (existing != null) {
                // Stable-name/addressing dedupe (profile HEAD keeps a single canonical version).
                if (topicKey == MemoryTopicKeys.PROFILE_NAME ||
                    topicKey == MemoryTopicKeys.PREFERENCE_ADDRESSING ||
                    topicKey == MemoryTopicKeys.PREFERENCE_LIKE ||
                    topicKey == MemoryTopicKeys.PREFERENCE_DISLIKE
                ) {
                    val newCanon = MemoryNameCanonicalizer.canonicalizeNameOrAddressing(trimmed, topicKey)
                    val oldCanon = MemoryNameCanonicalizer.canonicalizeNameOrAddressing(existing.content, topicKey)
                    if (newCanon != null && oldCanon != null && newCanon == oldCanon) {
                        val updated = existing.copy(
                            content = trimmed,
                            topicKey = topicKey,
                            layer = MemoryLayer.PROFILE,
                            status = MemoryStatus.ACTIVE,
                            updatedAt = now,
                        )
                        memoryDAO.updateMemory(updated)
                        indexMemoryAsync(updated)
                        return updated.toModel()
                    }
                }

                // USER replacement: archive old HEAD, then create a fresh HEAD row.
                memoryDAO.updateMemory(
                    existing.copy(
                        status = MemoryStatus.SUPERSEDED,
                        updatedAt = now,
                        supersedesId = null,
                    )
                )
                val head = MemoryEntity(
                    assistantId = assistantId,
                    content = trimmed,
                    topicKey = topicKey,
                    layer = MemoryLayer.PROFILE,
                    status = MemoryStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                )
                val id = memoryDAO.insertMemory(head).toInt()
                val saved = head.copy(id = id)
                indexMemoryAsync(saved)
                return saved.toModel()
            }
        }

        val layer = if (topicKey != null) MemoryLayer.PROFILE else MemoryLayer.EPISODE
        if (layer == MemoryLayer.EPISODE) {
            // Episode dedupe: merge with the most similar recent episode instead of always inserting.
            val episodeCandidates = memoryDAO
                .getMemoriesOfAssistant(assistantId, MemoryStatus.ACTIVE)
                .filter { it.layer != MemoryLayer.PROFILE }
                .take(25)

            val duplicate = MemoryEpisodeDeduplicator.findDuplicateEpisode(
                newContent = trimmed,
                newTopicKey = topicKey,
                candidates = episodeCandidates,
            )
            if (duplicate != null) {
                val updated = duplicate.copy(
                    content = trimmed,
                    updatedAt = now,
                )
                memoryDAO.updateMemory(updated)
                indexMemoryAsync(updated)
                return updated.toModel()
            }
        }

        val entity = MemoryEntity(
            assistantId = assistantId,
            content = trimmed,
            topicKey = topicKey,
            layer = layer,
            status = MemoryStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        val id = memoryDAO.insertMemory(entity).toInt()
        val saved = entity.copy(id = id)
        indexMemoryAsync(saved)
        return saved.toModel()
    }

    suspend fun deleteMemory(id: Int) {
        memoryDAO.deleteMemory(id)
        indexDAO.deleteLinksForMemory(id)
        indexDAO.deleteMeta(id)
        runCatching { indexDAO.deleteEmbedding(id) }
    }

    /**
     * Unified recall: server-side ranking + hard caps (anti choice-paralysis).
     * Optional semantic boost (0..40) when embeddings are available; otherwise pure rules.
     */
    suspend fun recall(
        assistantId: String,
        query: String? = null,
        entity: String? = null,
        emotion: String? = null,
        includeSuperseded: Boolean = false,
        limit: Int = RECALL_DEFAULT_LIMIT,
        contentCharCap: Int = RECALL_CONTENT_CHAR_CAP,
        totalCharCap: Int = RECALL_TOTAL_CHAR_CAP,
        markRecalled: Boolean = true,
    ): List<AssistantMemory> {
        ensureIndexed(assistantId)
        val cappedLimit = limit.coerceIn(1, RECALL_MAX_LIMIT)
        val q = query?.trim().orEmpty()
        val entityName = entity?.trim().orEmpty()

        val candidates = if (includeSuperseded) {
            memoryDAO.getAllMemoriesOfAssistant(assistantId)
        } else {
            memoryDAO.getMemoriesOfAssistant(assistantId, MemoryStatus.ACTIVE)
        }
        if (candidates.isEmpty()) return emptyList()

        val metas = indexDAO.getMetaForIds(candidates.map { it.id }).associateBy { it.memoryId }
        val entityId = if (entityName.isNotEmpty()) {
            indexDAO.findEntity(assistantId, entityName)?.id
        } else {
            null
        }
        val linkedMemoryIds = if (entityId != null) {
            indexDAO.linksForEntity(entityId).map { it.memoryId }.toSet()
        } else {
            emptySet()
        }
        val semanticBoosts = if (q.isNotEmpty()) {
            runCatching {
                semanticIndex?.scoreBoosts(q, candidates.map { it.id }).orEmpty()
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        data class Scored(val entity: MemoryEntity, val score: Int, val lastRecalled: Long)

        val scored = candidates.mapNotNull { mem ->
            val meta = metas[mem.id]
            var score = 0
            if (entityId != null && mem.id in linkedMemoryIds) score += 100
            if (entityName.isNotEmpty() && mem.content.contains(entityName, ignoreCase = true)) score += 40
            if (q.isNotEmpty()) {
                if (mem.content.contains(q, ignoreCase = true)) score += 50
                if (mem.topicKey?.contains(q, ignoreCase = true) == true) score += 20
                if (meta?.summaryShort?.contains(q, ignoreCase = true) == true) score += 15
            }
            score += semanticBoosts[mem.id] ?: 0
            if (!emotion.isNullOrBlank()) {
                val tags = meta?.emotionTags.orEmpty()
                if (tags.contains(emotion)) score += 35
            }
            // Always allow listing when no filters (emotion-only / casual browse)
            if (q.isEmpty() && entityName.isEmpty() && emotion.isNullOrBlank()) {
                score += 1
            }
            if (score <= 0) return@mapNotNull null
            Scored(mem, score, meta?.lastRecalledAt ?: 0L)
        }.sortedWith(
            compareByDescending<Scored> { it.score }
                .thenByDescending { it.entity.updatedAt }
                .thenBy { it.lastRecalled } // older last_recalled first
                .thenByDescending { it.entity.id }
        )

        var totalChars = 0
        val result = ArrayList<AssistantMemory>(cappedLimit)
        for (item in scored) {
            if (result.size >= cappedLimit) break
            val truncated = item.entity.content.take(contentCharCap)
            val projected = totalChars + truncated.length
            if (result.isNotEmpty() && projected > totalCharCap) break
            totalChars = projected
            result.add(item.entity.toModel().copy(content = truncated))
        }

        if (markRecalled && result.isNotEmpty()) {
            indexDAO.markRecalled(result.map { it.id }, System.currentTimeMillis())
        }
        return result
    }

    /**
     * Keyword recall plus optional 1-line relation summary when hits are graph-linked.
     * Relation + truncated bodies still respect [RECALL_TOTAL_CHAR_CAP].
     */
    suspend fun searchMemories(
        assistantId: String,
        query: String,
        includeSuperseded: Boolean = false,
        limit: Int = RECALL_DEFAULT_LIMIT,
    ): MemorySearchBundle {
        val expand = expandFromQuery(
            assistantId = assistantId,
            query = query,
            includeSuperseded = includeSuperseded,
            memoryLimit = limit,
            contentCharCap = RECALL_CONTENT_CHAR_CAP,
            totalCharCap = RECALL_TOTAL_CHAR_CAP,
            markRecalled = true,
        )
        if (expand != null && expand.memories.isNotEmpty()) {
            val relation = expand.relationLine().takeIf { expand.relatedEntities.isNotEmpty() }
            val memories = fitMemoriesUnderTotalCap(
                memories = expand.memories,
                relationCost = relation?.length ?: 0,
                totalCharCap = RECALL_TOTAL_CHAR_CAP,
            )
            return MemorySearchBundle(memories = memories, relationSummary = relation)
        }
        val memories = recall(
            assistantId = assistantId,
            query = query,
            includeSuperseded = includeSuperseded,
            limit = limit,
            markRecalled = true,
        )
        return MemorySearchBundle(memories = memories, relationSummary = null)
    }

    /**
     * Expand 1–2 hop neighborhood around a known entity.
     * Memories reuse [recall] ranking + hard caps; related entity names ≤ [EXPAND_MAX_RELATED].
     */
    suspend fun expandFromEntity(
        assistantId: String,
        entityName: String,
        depth: Int = EXPAND_DEFAULT_DEPTH,
        includeSuperseded: Boolean = false,
        memoryLimit: Int = RECALL_DEFAULT_LIMIT,
        contentCharCap: Int = RECALL_CONTENT_CHAR_CAP,
        totalCharCap: Int = RECALL_TOTAL_CHAR_CAP,
        markRecalled: Boolean = true,
    ): MemoryGraphExpand? {
        ensureIndexed(assistantId)
        val hubName = entityName.trim()
        if (hubName.isEmpty()) return null
        val hubNode = indexDAO.findEntity(assistantId, hubName) ?: return null
        val maxDepth = depth.coerceIn(1, EXPAND_MAX_DEPTH)

        val entityById = indexDAO.listEntities(assistantId, limit = 200).associateBy { it.id }
        val visited = mutableSetOf(hubNode.id)
        var frontier = setOf(hubNode.id)
        val related = LinkedHashMap<Long, MemoryEntityNode>()

        // Seed from explicit co_occurs edges (P2)
        for (edge in indexDAO.edgesForEntity(hubNode.id)) {
            val otherId = if (edge.fromEntityId == hubNode.id) edge.toEntityId else edge.fromEntityId
            if (otherId in visited) continue
            visited.add(otherId)
            entityById[otherId]?.let { related[it.id] = it }
        }

        for (d in 1..maxDepth) {
            val next = mutableSetOf<Long>()
            for (entityId in frontier) {
                for (edge in indexDAO.edgesForEntity(entityId)) {
                    val otherId = if (edge.fromEntityId == entityId) edge.toEntityId else edge.fromEntityId
                    if (otherId in visited) continue
                    visited.add(otherId)
                    next.add(otherId)
                    entityById[otherId]?.let { related[it.id] = it }
                }
                val memoryIds = indexDAO.linksForEntity(entityId).map { it.memoryId }
                for (memoryId in memoryIds) {
                    for (link in indexDAO.linksForMemory(memoryId)) {
                        if (link.entityId in visited) continue
                        visited.add(link.entityId)
                        next.add(link.entityId)
                        entityById[link.entityId]?.let { related[it.id] = it }
                    }
                }
            }
            frontier = next
            if (frontier.isEmpty()) break
        }

        val relatedNames = related.values
            .sortedWith(
                compareByDescending<MemoryEntityNode> { it.mentionCount }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.name }
            )
            .take(EXPAND_MAX_RELATED)
            .map { it.name }

        val memories = recall(
            assistantId = assistantId,
            entity = hubNode.name,
            includeSuperseded = includeSuperseded,
            limit = memoryLimit,
            contentCharCap = contentCharCap,
            totalCharCap = totalCharCap,
            markRecalled = markRecalled,
        )
        if (memories.isEmpty() && relatedNames.isEmpty()) return null
        return MemoryGraphExpand(
            hub = hubNode.name,
            relatedEntities = relatedNames,
            memories = memories,
        )
    }

    /**
     * Resolve hub via entity mention in [query], else via top keyword hit's strongest linked entity.
     */
    suspend fun expandFromQuery(
        assistantId: String,
        query: String,
        depth: Int = EXPAND_DEFAULT_DEPTH,
        includeSuperseded: Boolean = false,
        memoryLimit: Int = RECALL_DEFAULT_LIMIT,
        contentCharCap: Int = RECALL_CONTENT_CHAR_CAP,
        totalCharCap: Int = RECALL_TOTAL_CHAR_CAP,
        markRecalled: Boolean = true,
    ): MemoryGraphExpand? {
        ensureIndexed(assistantId)
        val q = query.trim()
        if (q.isEmpty()) return null

        val known = indexDAO.listEntities(assistantId, limit = 120)
        val entityHits = MemoryRuleIndexer.detectEntitiesInUserText(q, known.map { it.name })
        if (entityHits.isNotEmpty()) {
            return expandFromEntity(
                assistantId = assistantId,
                entityName = entityHits.first(),
                depth = depth,
                includeSuperseded = includeSuperseded,
                memoryLimit = memoryLimit,
                contentCharCap = contentCharCap,
                totalCharCap = totalCharCap,
                markRecalled = markRecalled,
            )
        }

        val topHit = recall(
            assistantId = assistantId,
            query = q,
            includeSuperseded = includeSuperseded,
            limit = 1,
            contentCharCap = contentCharCap,
            totalCharCap = totalCharCap,
            markRecalled = false,
        ).firstOrNull() ?: return null

        val links = indexDAO.linksForMemory(topHit.id)
        if (links.isEmpty()) {
            val memories = recall(
                assistantId = assistantId,
                query = q,
                includeSuperseded = includeSuperseded,
                limit = memoryLimit,
                contentCharCap = contentCharCap,
                totalCharCap = totalCharCap,
                markRecalled = markRecalled,
            )
            return MemoryGraphExpand(
                hub = q.take(16),
                relatedEntities = emptyList(),
                memories = memories,
            )
        }

        val entityById = known.associateBy { it.id }
        val hub = links.mapNotNull { entityById[it.entityId] }
            .maxWithOrNull(
                compareBy<MemoryEntityNode> { it.mentionCount }
                    .thenBy { it.updatedAt }
            ) ?: return null

        return expandFromEntity(
            assistantId = assistantId,
            entityName = hub.name,
            depth = depth,
            includeSuperseded = includeSuperseded,
            memoryLimit = memoryLimit,
            contentCharCap = contentCharCap,
            totalCharCap = totalCharCap,
            markRecalled = markRecalled,
        )
    }

    /**
     * High-confidence pre-retrieve for the current user turn (max 2 + one relation line).
     */
    suspend fun preretrieveForUserText(
        assistantId: String,
        userText: String,
    ): MemoryTurnHints {
        val known = indexDAO.listEntities(assistantId, limit = 120).map { it.name }
        val hits = MemoryRuleIndexer.detectEntitiesInUserText(userText, known)
        if (hits.isEmpty()) return MemoryTurnHints(emptyList())
        val expand = expandFromEntity(
            assistantId = assistantId,
            entityName = hits.first(),
            memoryLimit = PRERETRIEVE_MAX_ITEMS,
            contentCharCap = RECALL_CONTENT_CHAR_CAP,
            totalCharCap = PRERETRIEVE_TOTAL_CHAR_CAP,
            markRecalled = true,
        ) ?: return MemoryTurnHints(emptyList())
        val relation = expand.relationLine().takeIf { expand.relatedEntities.isNotEmpty() }
        val memories = fitMemoriesUnderTotalCap(
            memories = expand.memories.take(PRERETRIEVE_MAX_ITEMS),
            relationCost = relation?.length ?: 0,
            totalCharCap = PRERETRIEVE_TOTAL_CHAR_CAP,
        )
        return MemoryTurnHints(memories = memories, relationLine = relation)
    }

    suspend fun recallForEmotion(
        assistantId: String,
        emotion: String,
    ): MemoryTurnHints {
        val memories = recall(
            assistantId = assistantId,
            emotion = emotion,
            limit = EMOTION_MAX_ITEMS,
            contentCharCap = RECALL_CONTENT_CHAR_CAP,
            totalCharCap = EMOTION_TOTAL_CHAR_CAP,
            markRecalled = true,
        )
        if (memories.isEmpty()) return MemoryTurnHints(emptyList())
        val relationLine = relationLineAroundMemories(assistantId, memories)
        val fitted = fitMemoriesUnderTotalCap(
            memories = memories,
            relationCost = relationLine?.length ?: 0,
            totalCharCap = EMOTION_TOTAL_CHAR_CAP,
        )
        return MemoryTurnHints(memories = fitted, relationLine = relationLine)
    }

    private suspend fun relationLineAroundMemories(
        assistantId: String,
        memories: List<AssistantMemory>,
    ): String? {
        val top = memories.firstOrNull() ?: return null
        val links = indexDAO.linksForMemory(top.id)
        if (links.isEmpty()) return null
        val entities = indexDAO.listEntities(assistantId, limit = 120).associateBy { it.id }
        val hub = links.mapNotNull { entities[it.entityId] }
            .maxWithOrNull(
                compareBy<MemoryEntityNode> { it.mentionCount }
                    .thenBy { it.updatedAt }
            ) ?: return null
        val expand = expandFromEntity(
            assistantId = assistantId,
            entityName = hub.name,
            memoryLimit = 1,
            markRecalled = false,
        ) ?: return null
        return expand.relationLine().takeIf { expand.relatedEntities.isNotEmpty() }
    }

    private fun fitMemoriesUnderTotalCap(
        memories: List<AssistantMemory>,
        relationCost: Int,
        totalCharCap: Int,
    ): List<AssistantMemory> {
        val budget = (totalCharCap - relationCost.coerceAtLeast(0)).coerceAtLeast(0)
        if (budget <= 0) return emptyList()
        var used = 0
        val out = ArrayList<AssistantMemory>(memories.size)
        for (memory in memories) {
            val truncated = memory.content.take(RECALL_CONTENT_CHAR_CAP)
            val projected = used + truncated.length
            if (out.isNotEmpty() && projected > budget) break
            if (out.isEmpty() && truncated.length > budget) {
                out.add(memory.copy(content = truncated.take(budget)))
                break
            }
            out.add(memory.copy(content = truncated))
            used = projected
        }
        return out
    }

    suspend fun getGraphSnapshot(assistantId: String, nodeLimit: Int = 40): MemoryGraphSnapshot {
        reindexAssistantGraph(assistantId)
        val links = indexDAO.allLinksForAssistant(assistantId)
        val linkedEntityIds = links.map { it.entityId }.toSet()
        val nodes = indexDAO.listEntities(assistantId, limit = nodeLimit)
            .filter { it.id in linkedEntityIds }
        val nodeIds = nodes.map { it.id }.toSet()
        val filteredLinks = links.filter { link -> link.entityId in nodeIds }
        val edges = indexDAO.allEdgesForAssistant(assistantId)
            .filter { it.fromEntityId in nodeIds && it.toEntityId in nodeIds }
        val memoryIds = filteredLinks.map { it.memoryId }.distinct()
        val memories = memoryIds.mapNotNull { id ->
            memoryDAO.getMemoryById(id)
                ?.takeIf { it.status == MemoryStatus.ACTIVE }
                ?.toModel()
        }.associateBy { it.id }
        return MemoryGraphSnapshot(
            nodes = nodes,
            links = filteredLinks,
            edges = edges,
            memoriesById = memories,
        )
    }

    /** Re-apply rule indexer for active memories (refreshes graph after heuristic updates). */
    suspend fun reindexAssistantGraph(assistantId: String) {
        ensureIndexed(assistantId)
        memoryDAO.getMemoriesOfAssistant(assistantId, MemoryStatus.ACTIVE).forEach { memory ->
            runCatching { indexMemory(memory) }
        }
    }

    /**
     * Tiny resident graph skeleton for system prompt (≤ [GRAPH_HUBS_CHAR_CAP] chars).
     * Omitted when fewer than [GRAPH_HUBS_MIN_ENTITIES] entities exist.
     */
    suspend fun graphHubsSummary(assistantId: String): String? {
        ensureIndexed(assistantId)
        val nodes = indexDAO.listEntities(assistantId, limit = 40)
        if (nodes.size < GRAPH_HUBS_MIN_ENTITIES) return null
        val byId = nodes.associateBy { it.id }
        val parts = ArrayList<String>(GRAPH_HUBS_TOP)
        var used = 0
        for (hub in nodes.take(GRAPH_HUBS_TOP)) {
            val related = strongestRelatedName(hubId = hub.id, excludeId = hub.id, byId = byId)
                ?: continue
            val piece = "${hub.name}→$related"
            val extra = if (parts.isEmpty()) piece.length else piece.length + 2
            if (parts.isNotEmpty() && used + extra > GRAPH_HUBS_CHAR_CAP) break
            if (parts.isEmpty() && piece.length > GRAPH_HUBS_CHAR_CAP) {
                parts.add(piece.take(GRAPH_HUBS_CHAR_CAP))
                break
            }
            parts.add(piece)
            used += extra
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    /**
     * Optional Companion → Room L2 sync for high-confidence long facts.
     * Default [enabled]=false to avoid accidental promotion.
     */
    suspend fun syncCompanionLongFacts(
        assistantId: String,
        facts: List<String>,
        enabled: Boolean = false,
        minChars: Int = 8,
        maxFacts: Int = 5,
    ): Int {
        if (!enabled) return 0
        var written = 0
        facts.asSequence()
            .map { it.trim() }
            .filter { it.length >= minChars }
            .take(maxFacts)
            .forEach { fact ->
                // Prefer profile-like durable statements; skip vague fragments.
                val looksDurable = fact.contains("是") || fact.contains("喜欢") ||
                    fact.contains("偏好") || fact.contains("叫") ||
                    fact.contains("is ", ignoreCase = true) ||
                    fact.contains("prefer", ignoreCase = true)
                if (!looksDurable) return@forEach
                addMemory(assistantId, fact, source = MemoryWriteSource.COMPANION)
                written++
            }
        return written
    }

    private suspend fun strongestRelatedName(
        hubId: Long,
        excludeId: Long,
        byId: Map<Long, MemoryEntityNode>,
    ): String? {
        val counts = HashMap<Long, Int>()
        for (edge in indexDAO.edgesForEntity(hubId)) {
            val otherId = if (edge.fromEntityId == hubId) edge.toEntityId else edge.fromEntityId
            if (otherId == excludeId || otherId !in byId) continue
            counts[otherId] = (counts[otherId] ?: 0) + edge.weight.coerceAtLeast(1)
        }
        for (link in indexDAO.linksForEntity(hubId)) {
            for (other in indexDAO.linksForMemory(link.memoryId)) {
                if (other.entityId == excludeId) continue
                if (other.entityId !in byId) continue
                counts[other.entityId] = (counts[other.entityId] ?: 0) + 1
            }
        }
        val bestId = counts.maxWithOrNull(
            compareBy<Map.Entry<Long, Int>> { it.value }
                .thenBy { byId[it.key]?.mentionCount ?: 0 }
                .thenBy { byId[it.key]?.updatedAt ?: 0L }
        )?.key ?: return null
        return byId[bestId]?.name
    }

    fun backfillAllAsync() {
        if (backfillStarted) return
        backfillStarted = true
        indexScope.launch {
            runCatching {
                memoryDAO.getAllMemories()
                    .filter { it.status == MemoryStatus.ACTIVE }
                    .forEach { indexMemory(it) }
            }
        }
    }

    private suspend fun ensureIndexed(assistantId: String) {
        backfillAllAsync()
        val active = memoryDAO.getMemoriesOfAssistant(assistantId, MemoryStatus.ACTIVE)
        active.forEach { mem ->
            if (indexDAO.getMeta(mem.id) == null) {
                runCatching { indexMemory(mem) }
            }
        }
    }

    private fun indexMemoryAsync(entity: MemoryEntity) {
        indexScope.launch {
            runCatching { indexMemory(entity) }
        }
    }

    private suspend fun indexMemory(entity: MemoryEntity) {
        if (entity.status != MemoryStatus.ACTIVE) return
        val now = System.currentTimeMillis()
        val ruleExtracted = MemoryRuleIndexer.extract(entity.content, entity.topicKey, now)
        val enriched = if (MemoryRelationEnrichmentGate.enabled) {
            runCatching { relationEnricher.enrich(entity.content, entity.topicKey) }.getOrNull()
        } else {
            null
        }
        val extracted = enriched ?: ruleExtracted
        indexDAO.upsertMeta(
            MemoryRecallMeta(
                memoryId = entity.id,
                summaryShort = extracted.summaryShort,
                observedAt = extracted.observedAtHint
                    ?: entity.createdAt.takeIf { it > 0 }
                    ?: now,
                emotionTags = extracted.emotionTags.joinToString(","),
                importance = extracted.importance,
                lastRecalledAt = indexDAO.getMeta(entity.id)?.lastRecalledAt ?: 0L,
            )
        )
        indexDAO.deleteLinksForMemory(entity.id)
        extracted.entities.forEach { extractedEntity ->
            val existing = indexDAO.findEntity(entity.assistantId, extractedEntity.name)
            val entityId = if (existing != null) {
                indexDAO.updateEntity(
                    existing.copy(
                        type = extractedEntity.type,
                        mentionCount = existing.mentionCount + 1,
                        updatedAt = now,
                    )
                )
                existing.id
            } else {
                val inserted = indexDAO.insertEntity(
                    MemoryEntityNode(
                        assistantId = entity.assistantId,
                        name = extractedEntity.name,
                        type = extractedEntity.type,
                        mentionCount = 1,
                        updatedAt = now,
                    )
                )
                if (inserted > 0) {
                    inserted
                } else {
                    indexDAO.findEntity(entity.assistantId, extractedEntity.name)?.id ?: return@forEach
                }
            }
            indexDAO.upsertLink(
                MemoryEntityLink(
                    memoryId = entity.id,
                    entityId = entityId,
                    role = extractedEntity.role,
                )
            )
        }

        // Persist undirected co_occurs edges among entities in this memory
        val resolved = extracted.entities.mapNotNull { e ->
            indexDAO.findEntity(entity.assistantId, e.name)?.let { e.name to it.id }
        }.distinctBy { it.second }
        for (pair in MemoryRuleIndexer.coOccurPairs(extracted.entities)) {
            val leftId = resolved.find { it.first == pair.left }?.second ?: continue
            val rightId = resolved.find { it.first == pair.right }?.second ?: continue
            val from = minOf(leftId, rightId)
            val to = maxOf(leftId, rightId)
            if (from == to) continue
            val existingEdge = indexDAO.findEdge(from, to, MemoryRelationType.CO_OCCURS)
            indexDAO.upsertEdge(
                MemoryEntityEdge(
                    assistantId = entity.assistantId,
                    fromEntityId = from,
                    toEntityId = to,
                    relation = MemoryRelationType.CO_OCCURS,
                    weight = (existingEdge?.weight ?: 0) + 1,
                    updatedAt = now,
                )
            )
        }

        runCatching { semanticIndex?.ensureCached(entity.id, entity.content) }
    }

    private fun MemoryEntity.toModel() = AssistantMemory(
        id = id,
        content = content,
        topicKey = topicKey,
        layer = layer,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        supersedesId = supersedesId,
    )
}

fun emotionTagForProactiveReason(reasonName: String): String = when (reasonName) {
    "CHECK_IN", "ANNIVERSARY", "RELATIONSHIP_SHIFT" -> MemoryEmotionTag.WARM
    "SILENCE" -> MemoryEmotionTag.SHARED
    "MORNING", "EVENING" -> MemoryEmotionTag.CARE
    else -> MemoryEmotionTag.CASUAL
}
