package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/**
 * User-authored stable facts shared across all companions.
 * Distinct from per-assistant [AssistantMemory] (relationship / episode notes).
 */
@Serializable
data class GlobalUserProfile(
    val displayName: String = "",
    val birthday: String = "",
    val occupation: String = "",
    val personality: String = "",
    /** How the user prefers to be addressed (nickname, honorific, etc.). */
    val preferredAddressing: String = "",
    val locale: String = "",
    val extraNotes: String = "",
) {
    val isEmpty: Boolean
        get() = listOf(
            displayName,
            birthday,
            occupation,
            personality,
            preferredAddressing,
            locale,
            extraNotes,
        ).all { it.isBlank() }
}
