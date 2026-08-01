package me.rerere.ai.ui

/**
 * Request describing how to refresh a soft rolling summary for messages about to be
 * dropped by [limitContext] / [contextWindowStartIndex].
 */
data class RollingSummaryRequest(
    /** Prefix length that the resulting summary must cover (context window start index). */
    val coverCount: Int,
    /** Messages not yet covered that should be folded into the summary. */
    val uncoveredMessages: List<UIMessage>,
    val previousSummary: String?,
)

/**
 * Pure planner: whether / what to summarize before [limitContext] drops a prefix.
 * Returns null when no truncation occurs or an existing summary already covers the drop zone.
 */
fun planRollingSummaryUpdate(
    messages: List<UIMessage>,
    contextMessageLimit: Int,
    existingSummary: String?,
    coveredCount: Int,
): RollingSummaryRequest? {
    val coverCount = messages.contextWindowStartIndex(contextMessageLimit)
    if (coverCount <= 0) return null

    val normalizedCovered = coveredCount.coerceIn(0, coverCount)
    val hasUsableSummary = !existingSummary.isNullOrBlank() && normalizedCovered >= coverCount
    if (hasUsableSummary) return null

    val from = normalizedCovered.coerceAtMost(coverCount)
    val uncovered = when {
        from < coverCount -> messages.subList(from, coverCount)
        // coveredCount claims coverage but summary missing — rebuild full prefix
        else -> messages.subList(0, coverCount)
    }
    if (uncovered.isEmpty()) return null

    return RollingSummaryRequest(
        coverCount = coverCount,
        uncoveredMessages = uncovered,
        previousSummary = existingSummary?.takeIf { it.isNotBlank() && from < coverCount },
    )
}
