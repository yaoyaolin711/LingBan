package com.agent.chat.data.memory

import com.agent.chat.data.local.dao.MemoryDao
import com.agent.chat.data.local.mapper.toDomain
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.MemoryCategory
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import kotlin.math.exp
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

data class MemoryRetrieveRequest(
    val personaId: String,
    /** 当前用户问题 / 检索意图 */
    val queryText: String = "",
    /** 近期对话，用于补充检索词 */
    val recentMessages: List<Message> = emptyList(),
    val maxTokens: Int = MemorySettingsStore.PROMPT_MEMORY_MAX_TOKENS,
    val maxItems: Int = MemoryManager.DEFAULT_MAX_ITEMS,
    /** 候选池大小（按重要度预取） */
    val candidateLimit: Int = MemoryManager.CANDIDATE_POOL,
)

data class ScoredMemory(
    val memory: Memory,
    /** 1–10 标度的综合重要度（含类别/衰减调整） */
    val importanceScore: Float,
    /** 0–1 与当前问题相关性 */
    val relevanceScore: Float,
    /** 最终排序分 */
    val finalScore: Float,
)

data class MemoryRetrieveResult(
    val memories: List<Memory>,
    val scored: List<ScoredMemory>,
    val usedTokens: Int,
    val maxTokens: Int,
)

/**
 * Memory Manager：分类 + 重要性评分 + 按问题检索 + token 预算。
 * 不把全部记忆塞进 Prompt。
 */
@Singleton
class MemoryManager @Inject constructor(
    private val memoryDao: MemoryDao,
) {

    suspend fun retrieveForPrompt(request: MemoryRetrieveRequest): MemoryRetrieveResult {
        val candidates = memoryDao
            .getTopByPersona(request.personaId, request.candidateLimit)
            .map { it.toDomain() }
            .filterNot { it.blockedFromAi }
        if (candidates.isEmpty()) {
            return MemoryRetrieveResult(emptyList(), emptyList(), 0, request.maxTokens)
        }

        val query = buildQueryText(request.queryText, request.recentMessages)
        val queryTokens = tokenize(query)
        val intentCategories = inferQueryCategories(query)

        val scored = candidates.map { memory ->
            val importance = scoreImportance(memory)
            val relevance = scoreRelevance(memory, queryTokens, intentCategories)
            val finalScore = FINAL_IMPORTANCE_WEIGHT * (importance / 10f) +
                FINAL_RELEVANCE_WEIGHT * relevance +
                categoryIntentBoost(memory.category, intentCategories)
            ScoredMemory(
                memory = memory,
                importanceScore = importance,
                relevanceScore = relevance,
                finalScore = finalScore,
            )
        }.sortedByDescending { it.finalScore }

        val packed = packWithBudget(
            ranked = scored,
            maxTokens = request.maxTokens,
            maxItems = request.maxItems,
            queryBlank = queryTokens.isEmpty(),
        )

        return MemoryRetrieveResult(
            memories = packed.map { it.memory },
            scored = packed,
            usedTokens = packed.sumOf { estimateTokens(it.memory.content) },
            maxTokens = request.maxTokens,
        )
    }

    /**
     * 重要性评分 1–10：存库 importance + 类别底噪 + 时间衰减（事件/情绪）。
     */
    fun scoreImportance(memory: Memory): Float {
        val base = memory.importance.coerceIn(1, 10).toFloat()
        val categoryFloor = when (memory.category) {
            MemoryCategory.CORE -> 1.2f
            MemoryCategory.PREFERENCE -> 0.6f
            MemoryCategory.EMOTION -> 0.8f
            MemoryCategory.EVENT -> 0.3f
        }
        val ageDays = ((System.currentTimeMillis() - memory.createdAt)
            .coerceAtLeast(0L) / 86_400_000f)
        val recencyBoost = when (memory.category) {
            MemoryCategory.EVENT, MemoryCategory.EMOTION ->
                1.5f * exp(-ageDays / 14f)
            MemoryCategory.PREFERENCE ->
                0.4f * exp(-ageDays / 60f)
            MemoryCategory.CORE -> 0.2f
        }
        return (base + categoryFloor + recencyBoost).coerceIn(1f, 10f)
    }

    fun scoreRelevance(
        memory: Memory,
        queryTokens: Set<String>,
        intentCategories: Set<MemoryCategory>,
    ): Float {
        if (queryTokens.isEmpty()) {
            // 无明确问题时：Core / 高重要度仍保留基线相关
            return when (memory.category) {
                MemoryCategory.CORE -> 0.45f
                MemoryCategory.PREFERENCE -> 0.25f
                MemoryCategory.EMOTION -> 0.2f
                MemoryCategory.EVENT -> 0.15f
            }
        }
        val memTokens = tokenize(memory.content)
        if (memTokens.isEmpty()) return 0f

        val overlap = queryTokens.intersect(memTokens).size
        val union = queryTokens.union(memTokens).size.coerceAtLeast(1)
        val jaccard = overlap.toFloat() / union
        val coverage = overlap.toFloat() / queryTokens.size.coerceAtLeast(1)

        var score = 0.55f * coverage + 0.45f * jaccard
        if (memory.category in intentCategories) {
            score += 0.15f
        }
        // 短记忆命中关键词时额外加权
        val hitKeywords = queryTokens.count { token ->
            token.length >= 2 && memory.content.contains(token, ignoreCase = true)
        }
        if (hitKeywords > 0) {
            score += (0.08f * hitKeywords).coerceAtMost(0.25f)
        }
        return score.coerceIn(0f, 1f)
    }

    private fun packWithBudget(
        ranked: List<ScoredMemory>,
        maxTokens: Int,
        maxItems: Int,
        queryBlank: Boolean,
    ): List<ScoredMemory> {
        val selected = LinkedHashMap<String, ScoredMemory>()
        var used = 0

        fun tryAdd(item: ScoredMemory): Boolean {
            if (selected.size >= maxItems) return false
            if (selected.containsKey(item.memory.id)) return true
            val cost = estimateTokens(item.memory.content)
            if (used + cost > maxTokens && selected.isNotEmpty()) return false
            if (selected.isEmpty() && cost > maxTokens) {
                val clipped = item.memory.copy(
                    content = clipToTokens(item.memory.content, maxTokens),
                )
                selected[clipped.id] = item.copy(memory = clipped)
                used = maxTokens
                return true
            }
            if (used + cost > maxTokens) return false
            selected[item.memory.id] = item
            used += cost
            return true
        }

        // 1) 预留：高重要度 Core（保证身份稳定）
        ranked.asSequence()
            .filter { it.memory.category == MemoryCategory.CORE && it.importanceScore >= 6f }
            .take(CORE_RESERVED)
            .forEach { tryAdd(it) }

        // 2) 按 finalScore 填满；无 query 时略提高门槛，避免灌入低相关事件
        val minRelevance = if (queryBlank) 0f else MIN_RELEVANCE_WHEN_QUERY
        ranked.forEach { item ->
            if (item.relevanceScore < minRelevance &&
                item.memory.category != MemoryCategory.CORE
            ) {
                // 高重要情绪仍可进
                if (!(item.memory.category == MemoryCategory.EMOTION && item.importanceScore >= 8f)) {
                    return@forEach
                }
            }
            tryAdd(item)
        }

        return selected.values.sortedByDescending { it.finalScore }
    }

    private fun buildQueryText(queryText: String, recent: List<Message>): String {
        val primary = queryText.trim()
        if (primary.isNotBlank()) return primary
        return recent.asReversed()
            .asSequence()
            .filter { it.role == MessageRole.USER }
            .take(3)
            .map { it.content.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun inferQueryCategories(query: String): Set<MemoryCategory> {
        if (query.isBlank()) return emptySet()
        val q = query.lowercase(Locale.ROOT)
        val set = linkedSetOf<MemoryCategory>()
        if (listOf("喜欢", "习惯", "风格", "怎么说", "别", "不要", "偏好").any { it in q }) {
            set += MemoryCategory.PREFERENCE
        }
        if (listOf("难过", "开心", "累", "焦虑", "情绪", "感觉", "心情").any { it in q }) {
            set += MemoryCategory.EMOTION
        }
        if (listOf("今天", "昨天", "最近", "明天", "面试", "开会", "计划").any { it in q }) {
            set += MemoryCategory.EVENT
        }
        if (listOf("工作", "职业", "兴趣", "爱好", "名字", "住").any { it in q }) {
            set += MemoryCategory.CORE
        }
        return set
    }

    private fun categoryIntentBoost(
        category: MemoryCategory,
        intent: Set<MemoryCategory>,
    ): Float {
        if (intent.isEmpty()) return 0f
        return if (category in intent) 0.12f else 0f
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 12
        const val CANDIDATE_POOL = 80
        const val CORE_RESERVED = 3
        const val MIN_RELEVANCE_WHEN_QUERY = 0.08f
        const val FINAL_IMPORTANCE_WEIGHT = 0.4f
        const val FINAL_RELEVANCE_WEIGHT = 0.6f

        /** 中文粗估：约 1.5 字 / token */
        fun estimateTokens(text: String): Int =
            max(1, (text.length / 1.5f).toInt())

        fun clipToTokens(text: String, maxTokens: Int): String {
            val maxChars = (maxTokens * 1.5f).toInt().coerceAtLeast(1)
            return text.take(maxChars)
        }

        fun tokenize(text: String): Set<String> {
            if (text.isBlank()) return emptySet()
            val normalized = text.lowercase(Locale.ROOT)
            val tokens = linkedSetOf<String>()
            // 英文/数字词
            Regex("""[a-z0-9_]{2,}""").findAll(normalized).forEach { tokens += it.value }
            // 中文：连续汉字切 bigram + 单字（长度>=2 的词优先）
            val hans = Regex("""[\u4e00-\u9fff]+""").findAll(normalized).map { it.value }
            hans.forEach { chunk ->
                if (chunk.length == 1) {
                    tokens += chunk
                } else {
                    for (i in 0 until chunk.length - 1) {
                        tokens += chunk.substring(i, i + 2)
                    }
                    if (chunk.length >= 3) tokens += chunk.take(3)
                }
            }
            return tokens
        }
    }
}
