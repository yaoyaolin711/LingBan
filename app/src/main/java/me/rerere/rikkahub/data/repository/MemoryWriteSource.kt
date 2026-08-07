package me.rerere.rikkahub.data.repository

/**
 * Who triggered a memory write.
 *
 * - USER: user explicit input (including model-generated memory_tool calls based on the user's message)
 * - COMPANION: Companion-side proactive long facts sync
 */
enum class MemoryWriteSource {
    USER,
    COMPANION,
}

