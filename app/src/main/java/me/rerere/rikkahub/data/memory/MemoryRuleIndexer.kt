package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemoryEmotionTag
import me.rerere.rikkahub.data.db.entity.MemoryEntityType
import me.rerere.rikkahub.data.db.entity.MemoryRelationType

data class ExtractedMemoryIndex(
    val summaryShort: String,
    val entities: List<ExtractedEntity>,
    val emotionTags: List<String>,
    val importance: Int,
    /** Heuristic event time from relative phrases; null = fall back to createdAt. */
    val observedAtHint: Long? = null,
)

data class ExtractedEntity(
    val name: String,
    val type: String,
    val role: String = "about",
)

data class CoOccurPair(
    val left: String,
    val right: String,
    val relation: String = MemoryRelationType.CO_OCCURS,
)

/**
 * Rule-based indexing only (no LLM). Conservative extraction for recall + graph.
 * Idle LLM relation enrichment is gated separately and defaults off.
 */
object MemoryRuleIndexer {
    private val PLACE_STRONG_MARKERS = listOf(
        "去了", "去過", "去过", "到了", "待在", "住在", "went to ", "visited ", "at "
    )
    private val PERSON_MARKERS = listOf(
        "和", "跟", "朋友", "同事", "妈妈", "爸爸", "爸", "妈", "女友", "男友",
        "with ", "friend", "mom", "dad", "colleague"
    )
    private val NON_PLACE_TERMS = listOf(
        "角色扮演", "角色", "扮演", "聊天", "对话", "游戏", "工作", "学习", "梦", "关系",
        "roleplay", "chat", "conversation", "game", "work", "study",
    )
    private val WARM = listOf("开心", "幸福", "温暖", "想你", "感动", "甜蜜", "快乐", "happy", "love", "miss")
    private val STRESS = listOf("压力", "焦虑", "难过", "累", "加班", "崩溃", "焦虑", "sad", "stress", "tired", "anxious")
    private val SHARED = listOf("一起", "我们", "共同", "那天", "那次", "together", "we ", "our ")
    private val CARE = listOf("身体", "生病", "休息", "吃药", "医院", "健康", "sleep", "sick", "rest")
    private val DAY_MS = 24L * 60 * 60 * 1000

    private val PREFERENCE_SUBJECT_PATTERNS = listOf(
        Regex("""最喜欢(?:的)?(?:东西|事物|姿势|方式|活动|是)?[:：]?\s*[「"']?([^「」""'，,。；;\n]{2,16})"""),
        Regex("""偏好[:：]?\s*[「"']?([^「」""'，,。；;\n]{2,16})"""),
        Regex("""喜欢(?:的)?(?:是)?[:：]?\s*[「"']?([^「」""'，,。；;\n]{2,16})"""),
    )
    private val DISLIKE_SUBJECT_PATTERNS = listOf(
        Regex("""(?:不喜欢|讨厌|厌恶)[:：]?\s*[「"']?([^「」""'，,。；;\n]{2,16})"""),
    )

    fun extract(content: String, topicKey: String?, now: Long = System.currentTimeMillis()): ExtractedMemoryIndex {
        val text = content.trim()
        val summary = text.replace('\n', ' ').take(80)
        val entities = linkedMapOf<String, ExtractedEntity>()

        topicKey?.let { key ->
            entityFromTopicKey(key, text)?.let { entity ->
                entities[entity.name] = entity
            }
        }

        extractQuotedOrMarkedNames(text).forEach { name ->
            entities.putIfAbsent(name, ExtractedEntity(name = name, type = MemoryEntityType.PERSON))
        }

        if (topicKey?.startsWith("preference.") != true) {
            extractPlaces(text).forEach { place ->
                entities.putIfAbsent(place, ExtractedEntity(name = place, type = MemoryEntityType.PLACE))
            }
        }

        if (PERSON_MARKERS.any { text.contains(it, ignoreCase = true) }) {
            Regex("""(?:和|跟)\s*([\u4e00-\u9fffA-Za-z]{1,8})(?:一起|去|吃|聊|说|说|玩)?""")
                .findAll(text)
                .map { it.groupValues[1] }
                .filter { it !in setOf("我", "你", "他", "她", "他们", "我们") }
                .forEach { person ->
                    entities.putIfAbsent(person, ExtractedEntity(name = person, type = MemoryEntityType.PERSON))
                }
        }

        dedupeSubsumedEntities(entities)

        // Fallback token: keep a coarse event node so every memory is graph-reachable.
        if (entities.isEmpty() && text.length >= 4) {
            val token = text.take(12).trim()
            entities[token] = ExtractedEntity(name = token, type = MemoryEntityType.EVENT)
        }

        val tags = buildList {
            if (WARM.any { text.contains(it, ignoreCase = true) }) add(MemoryEmotionTag.WARM)
            if (STRESS.any { text.contains(it, ignoreCase = true) }) add(MemoryEmotionTag.STRESS)
            if (SHARED.any { text.contains(it, ignoreCase = true) }) add(MemoryEmotionTag.SHARED)
            if (CARE.any { text.contains(it, ignoreCase = true) }) add(MemoryEmotionTag.CARE)
            if (isEmpty()) add(MemoryEmotionTag.CASUAL)
        }

        val importance = when {
            topicKey != null -> 3
            MemoryEmotionTag.STRESS in tags || MemoryEmotionTag.WARM in tags -> 2
            else -> 1
        }

        return ExtractedMemoryIndex(
            summaryShort = summary,
            entities = entities.values.toList().take(6),
            emotionTags = tags.distinct(),
            importance = importance,
            observedAtHint = inferObservedAtHint(text, now),
        )
    }

    /** Undirected co-occurrence pairs among entities extracted from the same memory. */
    fun coOccurPairs(entities: List<ExtractedEntity>): List<CoOccurPair> {
        if (entities.size < 2) return emptyList()
        val names = entities.map { it.name.trim() }.filter { it.isNotBlank() }.distinct()
        if (names.size < 2) return emptyList()
        val pairs = ArrayList<CoOccurPair>()
        for (i in 0 until names.lastIndex) {
            for (j in i + 1 until names.size) {
                val a = names[i]
                val b = names[j]
                val (left, right) = if (a <= b) a to b else b to a
                pairs.add(CoOccurPair(left = left, right = right))
            }
        }
        return pairs
    }

    fun inferObservedAtHint(text: String, now: Long = System.currentTimeMillis()): Long? {
        val lower = text.lowercase()
        return when {
            lower.contains("昨天") || lower.contains("yesterday") -> now - DAY_MS
            lower.contains("前天") || lower.contains("day before yesterday") -> now - 2 * DAY_MS
            lower.contains("上周") || lower.contains("上週") || lower.contains("last week") -> now - 7 * DAY_MS
            lower.contains("上个月") || lower.contains("上個月") || lower.contains("last month") -> now - 30 * DAY_MS
            lower.contains("去年") || lower.contains("last year") -> now - 365 * DAY_MS
            else -> null
        }
    }

    fun detectEntitiesInUserText(text: String, knownEntityNames: Collection<String>): List<String> {
        val lower = text.lowercase()
        return knownEntityNames
            .filter { name -> name.isNotBlank() && lower.contains(name.lowercase()) }
            .sortedByDescending { it.length }
            .take(4)
    }

    internal fun entityFromTopicKey(key: String, text: String): ExtractedEntity? {
        return when (key) {
            MemoryTopicKeys.PREFERENCE_LIKE -> {
                val subject = extractPreferenceSubject(text)
                ExtractedEntity(
                    name = subject ?: "喜好",
                    type = MemoryEntityType.PREFERENCE,
                )
            }
            MemoryTopicKeys.PREFERENCE_DISLIKE -> {
                val subject = extractDislikeSubject(text)
                ExtractedEntity(
                    name = subject ?: "避讳",
                    type = MemoryEntityType.PREFERENCE,
                )
            }
            MemoryTopicKeys.PREFERENCE_ADDRESSING,
            MemoryTopicKeys.PREFERENCE_REPLY_STYLE,
            MemoryTopicKeys.PROFILE_LOCALE -> {
                extractPreferenceSubject(text)?.let {
                    ExtractedEntity(name = it, type = MemoryEntityType.PREFERENCE)
                }
            }
            MemoryTopicKeys.PROFILE_NAME,
            MemoryTopicKeys.PROFILE_BIRTHDAY -> null
            else -> null
        }
    }

    internal fun extractPlaces(text: String): List<String> {
        if (PLACE_STRONG_MARKERS.none { text.contains(it, ignoreCase = true) }) {
            return emptyList()
        }
        val results = linkedSetOf<String>()
        Regex("""(?:去了|去过|去過|到了|住在|待在|went to|visited)\s*([\u4e00-\u9fffA-Za-z0-9]{2,12})""")
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { isLikelyPlaceName(it) }
            .forEach { results.add(it) }
        Regex("""在([\u4e00-\u9fffA-Za-z0-9]{2,12})(?!中|里|时|的|会|能|过)""")
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { isLikelyPlaceName(it) }
            .forEach { results.add(it) }
        Regex("""\bat\s+([\u4e00-\u9fffA-Za-z0-9]{2,24})""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { isLikelyPlaceName(it) }
            .forEach { results.add(it) }
        return results.toList()
    }

    internal fun isLikelyPlaceName(candidate: String): Boolean {
        if (candidate.length !in 2..12) return false
        val lower = candidate.lowercase()
        if (NON_PLACE_TERMS.any { lower.contains(it.lowercase()) }) return false
        if (candidate.endsWith("中") || candidate.endsWith("里") || candidate.endsWith("时")) return false
        return true
    }

    internal fun extractPreferenceSubject(text: String): String? {
        for (pattern in PREFERENCE_SUBJECT_PATTERNS) {
            val match = pattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (match.length in 2..16 && !isGenericPreferenceToken(match)) {
                return match
            }
        }
        return null
    }

    private fun extractDislikeSubject(text: String): String? {
        for (pattern in DISLIKE_SUBJECT_PATTERNS) {
            val match = pattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (match.length in 2..16 && !isGenericPreferenceToken(match)) {
                return match
            }
        }
        return null
    }

    private fun isGenericPreferenceToken(value: String): Boolean {
        val lower = value.lowercase()
        return lower in setOf("like", "love", "prefer", "preference", "喜好", "偏好", "喜欢")
    }

    internal fun dedupeSubsumedEntities(entities: LinkedHashMap<String, ExtractedEntity>) {
        val names = entities.keys.toList()
        val remove = mutableSetOf<String>()
        for (a in names) {
            for (b in names) {
                if (a == b) continue
                when {
                    a.contains(b) && a.length > b.length -> remove.add(b)
                    b.contains(a) && b.length > a.length -> remove.add(a)
                }
            }
        }
        remove.forEach { entities.remove(it) }
    }

    private fun extractQuotedOrMarkedNames(text: String): List<String> {
        val fromQuotes = Regex("""[「『"“](.{1,16})[」』"”]""")
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
        return fromQuotes.toList()
    }
}

/**
 * Optional idle-time LLM relation enricher. Default disabled — rules only.
 * Wire a real implementation behind a settings flag later; never call on every turn by default.
 */
fun interface MemoryRelationEnricher {
    suspend fun enrich(content: String, topicKey: String?): ExtractedMemoryIndex?
}

object DisabledMemoryRelationEnricher : MemoryRelationEnricher {
    override suspend fun enrich(content: String, topicKey: String?): ExtractedMemoryIndex? = null
}

/** Settings hook: keep false unless user opts into idle LLM graph tidy. */
object MemoryRelationEnrichmentGate {
    @Volatile
    var enabled: Boolean = false
}
