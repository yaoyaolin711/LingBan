package me.rerere.rikkahub.data.life

import java.time.Instant

enum class RestSource {
    HEALTH_CONNECT,
    PHONE_INACTIVITY,
}

enum class RestConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * 过夜休息窗快照（本地结构化；进模型前再 format 成自然语言）。
 */
data class LifeContextSnapshot(
    val restStart: Instant? = null,
    val wakeApprox: Instant? = null,
    val durationMinutes: Long? = null,
    val source: RestSource? = null,
    val confidence: RestConfidence? = null,
    val fetchedAtEpochMs: Long = System.currentTimeMillis(),
) {
    /** 是否足够注入伴侣上下文（低置信且无有效时长则不注入） */
    val isInjectable: Boolean
        get() = restStart != null &&
            durationMinutes != null &&
            durationMinutes >= MIN_INJECT_MINUTES &&
            confidence != null &&
            confidence != RestConfidence.LOW

    companion object {
        const val MIN_INJECT_MINUTES = 4L * 60
        val EMPTY = LifeContextSnapshot()
    }
}
