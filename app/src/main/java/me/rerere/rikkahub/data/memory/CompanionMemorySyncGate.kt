package me.rerere.rikkahub.data.memory

/**
 * Optional Companion long-facts → Room L2 sync. Default off to avoid accidental promotion.
 */
object CompanionMemorySyncGate {
    @Volatile
    var enabled: Boolean = false
}
