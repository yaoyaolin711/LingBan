package me.rerere.rikkahub.data.groupchat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

fun Conversation.resolvedGroupMembers(settings: Settings): List<GroupMember> {
    return groupMembers.map { member ->
        val assistant = settings.getAssistantById(member.assistantId)
        member.copy(
            displayName = member.displayName.ifBlank {
                assistant?.name?.ifBlank { null } ?: member.assistantId.toString().take(8)
            }
        )
    }
}

fun Conversation.toGroupTranscript(settings: Settings): List<GroupTranscriptLine> {
    val nameById = resolvedGroupMembers(settings).associate { it.assistantId to it.displayName }
    return currentMessages.mapNotNull { msg ->
        val text = msg.toText().trim()
        if (text.isBlank()) return@mapNotNull null
        val label = when (msg.role) {
            MessageRole.USER -> "User"
            MessageRole.ASSISTANT -> nameById[msg.speakerId] ?: "Assistant"
            else -> return@mapNotNull null
        }
        GroupTranscriptLine(speakerLabel = label, text = text.take(500))
    }
}

/**
 * Rewrite transcript so the generating member sees who said what.
 * Other assistants' lines become USER messages labeled with their name (API-compatible).
 */
fun Conversation.messagesForGroupSpeaker(
    speakerId: Uuid,
    settings: Settings,
): List<UIMessage> {
    val nameById = resolvedGroupMembers(settings).associate { it.assistantId to it.displayName }
    return currentMessages.mapNotNull { msg ->
        when (msg.role) {
            MessageRole.USER -> msg
            MessageRole.SYSTEM -> msg
            MessageRole.ASSISTANT -> {
                if (msg.speakerId == speakerId) {
                    msg
                } else {
                    val name = nameById[msg.speakerId] ?: "Assistant"
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text("[$name]: ${msg.toText()}")),
                    )
                }
            }
            MessageRole.TOOL -> null
        }
    }
}

fun groupSpeakerSystemAddon(speakerName: String, members: List<GroupMember>): String {
    val roster = members.joinToString(", ") { it.displayName.ifBlank { it.assistantId.toString() } }
    return """

**Group chat**
You are "$speakerName" in a group with: $roster, and the user.
Speak only as yourself. Keep replies concise. You may @otherName to address another member.
Do not speak for other members.
""".trimIndent()
}

fun UIMessage.withSpeaker(speakerId: Uuid, mentions: List<Uuid> = emptyList()): UIMessage =
    copy(speakerId = speakerId, mentions = mentions)
