package me.rerere.rikkahub.ui.components.message

import me.rerere.ai.ui.UIMessagePart
import kotlin.math.roundToLong

/**
 * Pure helpers for sentence-by-sentence UI reveal (display only; does not mutate stored messages).
 */
object SentenceReveal {
    private val SENTENCE_ENDS = charArrayOf('。', '！', '？', '；', '.', '!', '?', ';', '\n')

    /** Split [text] into sentences, keeping trailing delimiters. Join of result equals [text]. */
    fun splitSentences(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(ch)
            if (ch in SENTENCE_ENDS) {
                result.add(sb.toString())
                sb.clear()
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }

    /**
     * Sentences ready to reveal from [text].
     * While streaming ([includeIncomplete] = false), the trailing fragment without a boundary is held back.
     */
    fun availableSentences(text: String, includeIncomplete: Boolean): List<String> {
        val parts = splitSentences(text)
        if (parts.isEmpty()) return emptyList()
        val last = parts.last()
        val complete = last.lastOrNull()?.let { it in SENTENCE_ENDS } == true
        return if (includeIncomplete || complete) parts else parts.dropLast(1)
    }

    fun delayMs(sentence: String, charsPerSecond: Float): Long {
        if (sentence.isBlank()) return 0L
        val cps = charsPerSecond.coerceAtLeast(0.1f)
        val ms = (sentence.length / cps * 1000f).roundToLong()
        return ms.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    /** Truncate Text parts so their concatenated text is a prefix of length [revealedLength]. */
    fun applyRevealToParts(parts: List<UIMessagePart>, revealedLength: Int): List<UIMessagePart> {
        if (revealedLength < 0) return parts
        var remaining = revealedLength
        return parts.map { part ->
            if (part is UIMessagePart.Text) {
                val take = minOf(remaining, part.text.length)
                remaining -= take
                if (take == part.text.length) part else part.copy(text = part.text.take(take))
            } else {
                part
            }
        }
    }

    /**
     * Replace Text parts with one [UIMessagePart.Text] per sentence so the chat UI
     * renders each sentence as its own bubble. Non-text parts keep their relative order.
     */
    fun expandToSentenceBubbles(
        parts: List<UIMessagePart>,
        sentences: List<String>,
    ): List<UIMessagePart> {
        val bubbles = sentences.map { it.trim() }.filter { it.isNotEmpty() }
        var replaced = false
        val result = ArrayList<UIMessagePart>(parts.size + bubbles.size)
        for (part in parts) {
            if (part is UIMessagePart.Text) {
                if (!replaced) {
                    bubbles.forEach { result.add(UIMessagePart.Text(it)) }
                    replaced = true
                }
            } else {
                result.add(part)
            }
        }
        if (!replaced) {
            bubbles.forEach { result.add(UIMessagePart.Text(it)) }
        }
        return result
    }

    const val MIN_DELAY_MS = 200L
    const val MAX_DELAY_MS = 4000L
}
