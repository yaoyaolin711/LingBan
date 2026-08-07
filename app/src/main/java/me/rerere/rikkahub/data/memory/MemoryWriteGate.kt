package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.repository.MemoryWriteSource

enum class MemoryWriteGateDecision {
    ALLOW,
    REJECT,
}

data class MemoryWriteGateResult(
    val decision: MemoryWriteGateDecision,
    val reason: String? = null,
)

object MemoryWriteGate {
    private val canonicalStableTopics = setOf(
        MemoryTopicKeys.PROFILE_NAME,
        MemoryTopicKeys.PREFERENCE_ADDRESSING,
        MemoryTopicKeys.PREFERENCE_LIKE,
        MemoryTopicKeys.PREFERENCE_DISLIKE,
    )

    fun evaluate(
        content: String,
        topicKey: String?,
        source: MemoryWriteSource,
    ): MemoryWriteGateResult {
        if (topicKey == null) return MemoryWriteGateResult(MemoryWriteGateDecision.ALLOW)
        if (topicKey !in canonicalStableTopics) return MemoryWriteGateResult(MemoryWriteGateDecision.ALLOW)

        val canonical = MemoryNameCanonicalizer.canonicalizeNameOrAddressing(content, topicKey)
        if (canonical != null) return MemoryWriteGateResult(MemoryWriteGateDecision.ALLOW)

        return MemoryWriteGateResult(
            decision = MemoryWriteGateDecision.REJECT,
            reason = "canonicalize_failed:${source.name.lowercase()}",
        )
    }
}
