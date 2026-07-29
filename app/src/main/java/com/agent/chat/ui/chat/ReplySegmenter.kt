package com.agent.chat.ui.chat

/**
 * 将 AI 回复拆成多段气泡：优先按换行，其次按句号。
 */
object ReplySegmenter {

    private val endPunctuation = setOf('。', '！', '？', '!', '?', '…')

    fun split(
        text: String,
        preferNewline: Boolean = true,
        minLengthToSplit: Int = 40,
        maxSegments: Int = 6,
    ): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return listOf(trimmed)

        if (preferNewline) {
            val byLine = trimmed
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (byLine.size >= 2) {
                return byLine.take(maxSegments).let { parts ->
                    if (byLine.size > maxSegments) {
                        parts.dropLast(1) + byLine.drop(maxSegments - 1).joinToString("\n")
                    } else {
                        parts
                    }
                }
            }
        }

        if (trimmed.length < minLengthToSplit) return listOf(trimmed)

        val sentences = splitSentences(trimmed)
        if (sentences.size <= 1) return listOf(trimmed)

        val targetCount = when {
            sentences.size <= 4 -> sentences.size
            else -> maxSegments.coerceAtMost(4)
        }.coerceIn(2, maxSegments)

        if (sentences.size <= targetCount) return sentences
        return mergeIntoGroups(sentences, targetCount)
    }

    fun estimateTypingDelayMs(segment: String): Long {
        val perChar = 50 + (segment.hashCode().ushr(1) % 31)
        return (segment.length * perChar.toLong()).coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    fun sourceMessageId(displayId: String): String =
        displayId.substringBefore(SEG_DELIMITER)

    fun isSegmentId(displayId: String): Boolean =
        displayId.contains(SEG_DELIMITER)

    fun segmentId(sourceId: String, index: Int): String =
        "$sourceId$SEG_DELIMITER$index"

    private fun splitSentences(text: String): List<String> {
        val result = ArrayList<String>()
        val buffer = StringBuilder()
        for (ch in text) {
            buffer.append(ch)
            if (ch in endPunctuation) {
                val sentence = buffer.toString().trim()
                if (sentence.isNotEmpty()) result.add(sentence)
                buffer.clear()
            }
        }
        val rest = buffer.toString().trim()
        if (rest.isNotEmpty()) result.add(rest)
        return result
    }

    private fun mergeIntoGroups(sentences: List<String>, groupCount: Int): List<String> {
        val n = sentences.size
        val base = n / groupCount
        val extra = n % groupCount
        val result = ArrayList<String>(groupCount)
        var idx = 0
        repeat(groupCount) { g ->
            val size = base + if (g < extra) 1 else 0
            if (size <= 0 || idx >= n) return@repeat
            val chunk = sentences.subList(idx, (idx + size).coerceAtMost(n)).joinToString("")
            if (chunk.isNotBlank()) result.add(chunk.trim())
            idx += size
        }
        return result.ifEmpty { listOf(sentences.joinToString("")) }
    }

    const val SEG_DELIMITER = "__seg__"
    private const val MIN_DELAY_MS = 200L
    private const val MAX_DELAY_MS = 2000L
}
