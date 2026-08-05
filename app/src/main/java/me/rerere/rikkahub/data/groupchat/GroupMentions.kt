package me.rerere.rikkahub.data.groupchat

import kotlin.uuid.Uuid

/**
 * Resolve @mentions in free text against group member display names / assistant names.
 * Matches `@Name` where Name is a member displayName (case-insensitive, longest match first).
 */
fun parseGroupMentions(text: String, members: List<GroupMember>): List<Uuid> {
    if (text.isBlank() || members.isEmpty()) return emptyList()
    val sorted = members
        .filter { it.displayName.isNotBlank() }
        .sortedByDescending { it.displayName.length }
    if (sorted.isEmpty()) return emptyList()

    val found = linkedSetOf<Uuid>()
    val pattern = Regex("""@([^\s@]+)""")
    for (match in pattern.findAll(text)) {
        val token = match.groupValues[1]
        val member = sorted.firstOrNull { it.displayName.equals(token, ignoreCase = true) }
            ?: sorted.firstOrNull { token.startsWith(it.displayName, ignoreCase = true) }
        if (member != null) found.add(member.assistantId)
    }
    return found.toList()
}
