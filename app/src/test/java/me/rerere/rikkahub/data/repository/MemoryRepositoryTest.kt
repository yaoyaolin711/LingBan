package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.MemoryIndexDAO
import me.rerere.rikkahub.data.db.entity.MemoryEmbedding
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntityEdge
import me.rerere.rikkahub.data.db.entity.MemoryEntityLink
import me.rerere.rikkahub.data.db.entity.MemoryEntityNode
import me.rerere.rikkahub.data.db.entity.MemoryLayer
import me.rerere.rikkahub.data.db.entity.MemoryRecallMeta
import me.rerere.rikkahub.data.db.entity.MemoryStatus
import me.rerere.rikkahub.data.memory.MemoryTopicKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {
    @Test
    fun `addMemory replaces profile HEAD and archives old version`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        val first = repo.addMemory("a1", "我喜欢茶")
        val second = repo.addMemory("a1", "我喜欢手冲咖啡")

        // Same profile topic will replace HEAD by creating a fresh ACTIVE row,
        // while the old HEAD is archived as superseded for traceability.
        assertTrue(first.id != second.id)
        assertEquals(MemoryTopicKeys.PREFERENCE_LIKE, second.topicKey)
        assertEquals(MemoryLayer.PROFILE, second.layer)
        assertEquals(1, dao.activeCount("a1"))
        assertEquals("我喜欢手冲咖啡", second.content)
        assertTrue(dao.all.any { it.id == first.id && it.status == MemoryStatus.SUPERSEDED })
    }

    @Test
    fun `stable name-addressing dedupe keeps existing profile HEAD`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        val first = repo.addMemory("a1", "我叫小雨")
        val second = repo.addMemory("a1", "我的名字是小雨")

        assertEquals(first.id, second.id)
        assertEquals(MemoryTopicKeys.PROFILE_NAME, second.topicKey)
        assertEquals(MemoryLayer.PROFILE, second.layer)
        assertEquals(1, dao.activeCount("a1"))
        assertEquals("我的名字是小雨", second.content)
    }

    @Test
    fun `stable addressing dedupe keeps existing profile HEAD`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        val first = repo.addMemory("a1", "叫我阿雨")
        val second = repo.addMemory("a1", "称呼我阿雨")

        assertEquals(first.id, second.id)
        assertEquals(MemoryTopicKeys.PREFERENCE_ADDRESSING, second.topicKey)
        assertEquals(MemoryLayer.PROFILE, second.layer)
        assertEquals(1, dao.activeCount("a1"))
        assertEquals("称呼我阿雨", second.content)
    }

    @Test
    fun `stable like dedupe keeps existing profile HEAD`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        val first = repo.addMemory("a1", "我喜欢手冲咖啡")
        val second = repo.addMemory("a1", "我偏好“手冲咖啡”")

        assertEquals(first.id, second.id)
        assertEquals(MemoryTopicKeys.PREFERENCE_LIKE, second.topicKey)
        assertEquals(MemoryLayer.PROFILE, second.layer)
        assertEquals(1, dao.activeCount("a1"))
        assertEquals("我偏好“手冲咖啡”", second.content)
    }

    @Test
    fun `stable dislike dedupe keeps existing profile HEAD`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        val first = repo.addMemory("a1", "我不喜欢加班")
        val second = repo.addMemory("a1", "我讨厌“加班”")

        assertEquals(first.id, second.id)
        assertEquals(MemoryTopicKeys.PREFERENCE_DISLIKE, second.topicKey)
        assertEquals(MemoryLayer.PROFILE, second.layer)
        assertEquals(1, dao.activeCount("a1"))
        assertEquals("我讨厌“加班”", second.content)
    }

    @Test
    fun `stable like without concrete target is rejected`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        val rejected = repo.addMemory("a1", "我喜欢")

        assertEquals(-1, rejected.id)
        assertEquals("rejected", rejected.status)
        assertEquals(MemoryTopicKeys.PREFERENCE_LIKE, rejected.topicKey)
        assertEquals(0, dao.activeCount("a1"))
    }

    @Test
    fun `editing stable topic without concrete target is rejected and leaves original intact`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        val first = repo.addMemory("a1", "我喜欢手冲咖啡")
        val rejected = repo.updateContent(first.id, "我喜欢")

        assertEquals("rejected", rejected.status)
        assertEquals(first.id, dao.getMemoryById(first.id)?.id)
        assertEquals("我喜欢手冲咖啡", dao.getMemoryById(first.id)?.content)
        assertEquals(1, dao.activeCount("a1"))
    }

    @Test
    fun `episode memories are not merged when content is different`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        repo.addMemory("a1", "上周去了海边")
        repo.addMemory("a1", "计划下个月出差")

        assertEquals(2, dao.activeCount("a1"))
        assertTrue(dao.all.filter { it.assistantId == "a1" }.all { it.layer == MemoryLayer.EPISODE })
    }

    @Test
    fun `episode memories are merged when content is duplicated`() = runBlocking {
        val dao = FakeMemoryDao()
        val repo = MemoryRepository(dao, FakeMemoryIndexDao())

        val first = repo.addMemory("a1", "上周去了海边")
        val second = repo.addMemory("a1", "上周去了海边")

        assertEquals(first.id, second.id)
        assertEquals(1, dao.activeCount("a1"))
        assertTrue(dao.all.filter { it.assistantId == "a1" }.all { it.layer == MemoryLayer.EPISODE })
    }

    @Test
    fun `recall applies top3 and total caps`() = runBlocking {
        val dao = FakeMemoryDao()
        val index = FakeMemoryIndexDao()
        val longContent = "y".repeat(MemoryRepository.RECALL_CONTENT_CHAR_CAP + 80)
        repeat(10) { index ->
            dao.insertMemory(
                MemoryEntity(
                    assistantId = "a1",
                    content = "keyword-$index $longContent",
                    layer = MemoryLayer.EPISODE,
                    status = MemoryStatus.ACTIVE,
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                )
            )
        }
        val repo = MemoryRepository(dao, index)
        val hits = repo.searchMemories("a1", "keyword")
        assertTrue(hits.memories.size <= MemoryRepository.RECALL_MAX_LIMIT)
        assertTrue(hits.memories.all { it.content.length <= MemoryRepository.RECALL_CONTENT_CHAR_CAP })
        assertTrue(hits.memories.sumOf { it.content.length } <= MemoryRepository.RECALL_TOTAL_CHAR_CAP)
    }

    @Test
    fun `expandFromEntity returns hub related entities and capped memories`() = runBlocking {
        val dao = FakeMemoryDao()
        val index = FakeMemoryIndexDao()
        val seaId = dao.insertMemory(
            MemoryEntity(
                assistantId = "a1",
                content = "和阿明一起去了海边旅行，很开心",
                layer = MemoryLayer.EPISODE,
                status = MemoryStatus.ACTIVE,
                createdAt = 1,
                updatedAt = 10,
            )
        ).toInt()
        val workId = dao.insertMemory(
            MemoryEntity(
                assistantId = "a1",
                content = "阿明推荐了一家海边餐厅",
                layer = MemoryLayer.EPISODE,
                status = MemoryStatus.ACTIVE,
                createdAt = 2,
                updatedAt = 20,
            )
        ).toInt()
        val otherId = dao.insertMemory(
            MemoryEntity(
                assistantId = "a1",
                content = "下周要开会讨论预算",
                layer = MemoryLayer.EPISODE,
                status = MemoryStatus.ACTIVE,
                createdAt = 3,
                updatedAt = 30,
            )
        ).toInt()

        val sea = index.insertEntity(
            MemoryEntityNode(assistantId = "a1", name = "海边", type = "place", mentionCount = 3, updatedAt = 10)
        )
        val amin = index.insertEntity(
            MemoryEntityNode(assistantId = "a1", name = "阿明", type = "person", mentionCount = 2, updatedAt = 20)
        )
        val travel = index.insertEntity(
            MemoryEntityNode(assistantId = "a1", name = "旅行", type = "event", mentionCount = 1, updatedAt = 10)
        )
        index.upsertLink(MemoryEntityLink(memoryId = seaId, entityId = sea, role = "about"))
        index.upsertLink(MemoryEntityLink(memoryId = seaId, entityId = amin, role = "about"))
        index.upsertLink(MemoryEntityLink(memoryId = seaId, entityId = travel, role = "about"))
        index.upsertLink(MemoryEntityLink(memoryId = workId, entityId = sea, role = "about"))
        index.upsertLink(MemoryEntityLink(memoryId = workId, entityId = amin, role = "about"))
        index.upsertMeta(MemoryRecallMeta(memoryId = seaId, summaryShort = "海边旅行", emotionTags = "warm"))
        index.upsertMeta(MemoryRecallMeta(memoryId = workId, summaryShort = "海边餐厅", emotionTags = "casual"))
        index.upsertMeta(MemoryRecallMeta(memoryId = otherId, summaryShort = "开会", emotionTags = "stress"))

        val repo = MemoryRepository(dao, index)
        val expand = repo.expandFromEntity("a1", "海边")!!
        assertEquals("海边", expand.hub)
        assertTrue(expand.relatedEntities.contains("阿明"))
        assertTrue(expand.relatedEntities.contains("旅行"))
        assertTrue(expand.relatedEntities.size <= MemoryRepository.EXPAND_MAX_RELATED)
        assertTrue(expand.memories.size in 1..MemoryRepository.RECALL_MAX_LIMIT)
        assertTrue(expand.memories.all { it.content.contains("海边") || it.id == seaId || it.id == workId })
        assertTrue(expand.relationLine().startsWith("海边 —关联→"))

        val search = repo.searchMemories("a1", "海边")
        assertTrue(search.memories.isNotEmpty())
        assertEquals(expand.relationLine(), search.relationSummary)

        val hints = repo.preretrieveForUserText("a1", "好想再去海边啊")
        assertTrue(hints.memories.size <= MemoryRepository.PRERETRIEVE_MAX_ITEMS)
        assertEquals(expand.relationLine(), hints.relationLine)
    }

    @Test
    fun `expandFromQuery falls back via keyword hit entity links`() = runBlocking {
        val dao = FakeMemoryDao()
        val index = FakeMemoryIndexDao()
        val memId = dao.insertMemory(
            MemoryEntity(
                assistantId = "a1",
                content = "周末和阿明去爬山了",
                layer = MemoryLayer.EPISODE,
                status = MemoryStatus.ACTIVE,
                createdAt = 1,
                updatedAt = 5,
            )
        ).toInt()
        val amin = index.insertEntity(
            MemoryEntityNode(assistantId = "a1", name = "阿明", type = "person", mentionCount = 2, updatedAt = 5)
        )
        val hike = index.insertEntity(
            MemoryEntityNode(assistantId = "a1", name = "爬山", type = "event", mentionCount = 1, updatedAt = 5)
        )
        index.upsertLink(MemoryEntityLink(memoryId = memId, entityId = amin, role = "about"))
        index.upsertLink(MemoryEntityLink(memoryId = memId, entityId = hike, role = "about"))
        index.upsertMeta(MemoryRecallMeta(memoryId = memId, summaryShort = "爬山", emotionTags = "warm"))

        val repo = MemoryRepository(dao, index)
        // Query has no indexed entity substring; expand resolves hub from top keyword hit's links.
        val expand = repo.expandFromQuery("a1", "周末那次")!!
        assertTrue(expand.hub == "阿明" || expand.hub == "爬山")
        assertTrue(expand.memories.isNotEmpty())
        assertTrue(expand.relatedEntities.isNotEmpty())
    }

    @Test
    fun `graphHubsSummary omitted below min entities and capped when present`() = runBlocking {
        val dao = FakeMemoryDao()
        val index = FakeMemoryIndexDao()
        val memId = dao.insertMemory(
            MemoryEntity(
                assistantId = "a1",
                content = "和阿明去海边旅行",
                layer = MemoryLayer.EPISODE,
                status = MemoryStatus.ACTIVE,
                createdAt = 1,
                updatedAt = 1,
            )
        ).toInt()
        val a = index.insertEntity(MemoryEntityNode(assistantId = "a1", name = "海边", mentionCount = 3, updatedAt = 3))
        val b = index.insertEntity(MemoryEntityNode(assistantId = "a1", name = "阿明", mentionCount = 2, updatedAt = 2))
        index.upsertLink(MemoryEntityLink(memoryId = memId, entityId = a))
        index.upsertLink(MemoryEntityLink(memoryId = memId, entityId = b))
        index.upsertMeta(MemoryRecallMeta(memoryId = memId, summaryShort = "海边"))

        val repo = MemoryRepository(dao, index)
        assertEquals(null, repo.graphHubsSummary("a1"))

        val c = index.insertEntity(MemoryEntityNode(assistantId = "a1", name = "旅行", mentionCount = 1, updatedAt = 1))
        index.upsertLink(MemoryEntityLink(memoryId = memId, entityId = c))
        val summary = repo.graphHubsSummary("a1")!!
        assertTrue(summary.contains("→"))
        assertTrue(summary.length <= MemoryRepository.GRAPH_HUBS_CHAR_CAP)
    }

    private class FakeMemoryDao : MemoryDAO {
        val all = mutableListOf<MemoryEntity>()
        private var nextId = 1

        fun activeCount(assistantId: String) =
            all.count { it.assistantId == assistantId && it.status == MemoryStatus.ACTIVE }

        override fun getMemoriesOfAssistantFlow(assistantId: String, status: String): Flow<List<MemoryEntity>> =
            MutableStateFlow(all.toList()).map { list ->
                list.filter { it.assistantId == assistantId && it.status == status }
                    .sortedWith(compareByDescending<MemoryEntity> { it.updatedAt }.thenByDescending { it.id })
            }

        override suspend fun getMemoriesOfAssistant(assistantId: String, status: String): List<MemoryEntity> =
            all.filter { it.assistantId == assistantId && it.status == status }
                .sortedWith(compareByDescending<MemoryEntity> { it.updatedAt }.thenByDescending { it.id })

        override suspend fun getAllMemoriesOfAssistant(assistantId: String): List<MemoryEntity> =
            all.filter { it.assistantId == assistantId }

        override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = MutableStateFlow(all.toList())

        override suspend fun getAllMemories(): List<MemoryEntity> = all.toList()

        override suspend fun getMemoryById(id: Int): MemoryEntity? = all.find { it.id == id }

        override suspend fun getActiveByTopic(assistantId: String, topicKey: String): MemoryEntity? =
            all.find {
                it.assistantId == assistantId &&
                    it.status == MemoryStatus.ACTIVE &&
                    it.topicKey == topicKey
            }

        override suspend fun searchMemories(
            assistantId: String,
            query: String,
            includeSuperseded: Boolean,
            limit: Int,
        ): List<MemoryEntity> =
            all.filter { entity ->
                entity.assistantId == assistantId &&
                    (includeSuperseded || entity.status == MemoryStatus.ACTIVE) &&
                    (entity.content.contains(query) || (entity.topicKey?.contains(query) == true))
            }.sortedWith(
                compareBy<MemoryEntity> { if (it.status == MemoryStatus.ACTIVE) 0 else 1 }
                    .thenByDescending { it.updatedAt }
                    .thenByDescending { it.id }
            ).take(limit)

        override suspend fun insertMemory(memory: MemoryEntity): Long {
            val id = nextId++
            all.add(memory.copy(id = id))
            return id.toLong()
        }

        override suspend fun updateMemory(memory: MemoryEntity) {
            val index = all.indexOfFirst { it.id == memory.id }
            require(index >= 0)
            all[index] = memory
        }

        override suspend fun deleteMemory(id: Int) {
            all.removeAll { it.id == id }
        }

        override suspend fun deleteMemoriesOfAssistant(assistantId: String) {
            all.removeAll { it.assistantId == assistantId }
        }
    }

    private class FakeMemoryIndexDao : MemoryIndexDAO {
        private val nodes = mutableListOf<MemoryEntityNode>()
        private val links = mutableListOf<MemoryEntityLink>()
        private val edges = mutableListOf<MemoryEntityEdge>()
        private val metas = mutableMapOf<Int, MemoryRecallMeta>()
        private val embeddings = mutableMapOf<Int, MemoryEmbedding>()
        private var nextEntityId = 1L

        override fun observeEntities(assistantId: String): Flow<List<MemoryEntityNode>> =
            MutableStateFlow(nodes.filter { it.assistantId == assistantId })

        override suspend fun listEntities(assistantId: String, limit: Int): List<MemoryEntityNode> =
            nodes.filter { it.assistantId == assistantId }
                .sortedWith(
                    compareByDescending<MemoryEntityNode> { it.mentionCount }
                        .thenByDescending { it.updatedAt }
                )
                .take(limit)

        override suspend fun findEntity(assistantId: String, name: String): MemoryEntityNode? =
            nodes.find { it.assistantId == assistantId && it.name == name }

        override suspend fun insertEntity(node: MemoryEntityNode): Long {
            val id = nextEntityId++
            nodes.add(node.copy(id = id))
            return id
        }

        override suspend fun updateEntity(node: MemoryEntityNode) {
            val i = nodes.indexOfFirst { it.id == node.id }
            if (i >= 0) nodes[i] = node
        }

        override suspend fun linksForMemory(memoryId: Int): List<MemoryEntityLink> =
            links.filter { it.memoryId == memoryId }

        override suspend fun linksForEntity(entityId: Long): List<MemoryEntityLink> =
            links.filter { it.entityId == entityId }

        override suspend fun allLinksForAssistant(assistantId: String): List<MemoryEntityLink> {
            val ids = nodes.filter { it.assistantId == assistantId }.map { it.id }.toSet()
            return links.filter { it.entityId in ids }
        }

        override suspend fun upsertLink(link: MemoryEntityLink) {
            links.removeAll { it.memoryId == link.memoryId && it.entityId == link.entityId }
            links.add(link)
        }

        override suspend fun deleteLinksForMemory(memoryId: Int) {
            links.removeAll { it.memoryId == memoryId }
        }

        override suspend fun allEdgesForAssistant(assistantId: String): List<MemoryEntityEdge> =
            edges.filter { it.assistantId == assistantId }
                .sortedWith(compareByDescending<MemoryEntityEdge> { it.weight }.thenByDescending { it.updatedAt })

        override suspend fun edgesForEntity(entityId: Long): List<MemoryEntityEdge> =
            edges.filter { it.fromEntityId == entityId || it.toEntityId == entityId }

        override suspend fun findEdge(fromId: Long, toId: Long, relation: String): MemoryEntityEdge? =
            edges.find { it.fromEntityId == fromId && it.toEntityId == toId && it.relation == relation }

        override suspend fun upsertEdge(edge: MemoryEntityEdge) {
            edges.removeAll {
                it.fromEntityId == edge.fromEntityId &&
                    it.toEntityId == edge.toEntityId &&
                    it.relation == edge.relation
            }
            edges.add(edge)
        }

        override suspend fun upsertMeta(meta: MemoryRecallMeta) {
            metas[meta.memoryId] = meta
        }

        override suspend fun getMeta(memoryId: Int): MemoryRecallMeta? = metas[memoryId]

        override suspend fun getMetaForIds(ids: List<Int>): List<MemoryRecallMeta> =
            ids.mapNotNull { metas[it] }

        override suspend fun deleteMeta(memoryId: Int) {
            metas.remove(memoryId)
        }

        override suspend fun markRecalled(ids: List<Int>, at: Long) {
            ids.forEach { id ->
                val old = metas[id] ?: return@forEach
                metas[id] = old.copy(lastRecalledAt = at)
            }
        }

        override suspend fun upsertEmbedding(embedding: MemoryEmbedding) {
            embeddings[embedding.memoryId] = embedding
        }

        override suspend fun getEmbedding(memoryId: Int): MemoryEmbedding? = embeddings[memoryId]

        override suspend fun getEmbeddings(ids: List<Int>): List<MemoryEmbedding> =
            ids.mapNotNull { embeddings[it] }

        override suspend fun deleteEmbedding(memoryId: Int) {
            embeddings.remove(memoryId)
        }
    }
}
