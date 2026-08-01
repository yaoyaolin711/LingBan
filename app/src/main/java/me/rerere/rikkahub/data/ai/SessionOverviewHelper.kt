package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.util.concurrent.atomic.AtomicReference
import kotlin.uuid.Uuid

private const val TAG = "SessionOverview"
private const val MIN_MESSAGES_FOR_OVERVIEW = 2
private const val OVERVIEW_TARGET_TOKENS = 512

const val CARRYOVER_OFFER_UI = "carryover_offer"
private const val META_UI = "ui"
private const val META_SOURCE_TITLE = "sourceTitle"
private const val META_SOURCE_ID = "sourceConversationId"

/**
 * Pending overview waiting to be optionally imported into the next new conversation.
 */
data class PendingSessionCarryover(
    val assistantId: Uuid,
    val sourceConversationId: Uuid,
    val sourceTitle: String,
    val overview: String,
)

/**
 * Builds a short "session overview" when leaving a chat for a new one,
 * and can inject an assistant offer card into the new conversation asynchronously.
 */
class SessionOverviewHelper(
    private val compressHelper: ConversationCompressHelper,
    private val conversationRepo: ConversationRepository,
) {
    private val pending = AtomicReference<PendingSessionCarryover?>(null)

    fun peekPending(): PendingSessionCarryover? = pending.get()

    fun consumePending(): PendingSessionCarryover? = pending.getAndSet(null)

    fun clearPending() {
        pending.set(null)
    }

    fun setPending(carryover: PendingSessionCarryover) {
        pending.set(carryover)
    }

    fun shouldOfferOverview(conversation: Conversation): Boolean {
        if (conversation.newConversation && conversation.messageNodes.isEmpty()) return false
        return conversation.currentMessages.size >= MIN_MESSAGES_FOR_OVERVIEW
    }

    /**
     * Always rebuild overview from current messages (chat may have continued since last leave).
     * Persists result on the source conversation for history / debugging.
     */
    suspend fun ensureSessionOverview(
        conversation: Conversation,
        settings: Settings,
    ): String {
        val messages = conversation.currentMessages
        if (messages.isEmpty()) return ""

        val overview = runCatching {
            compressHelper.compressMessageChunks(
                settings = settings,
                messages = messages,
                targetTokens = OVERVIEW_TARGET_TOKENS,
                additionalPrompt = SESSION_OVERVIEW_PROMPT,
            ).joinToString("\n\n").trim()
        }.onFailure {
            Log.w(TAG, "ensureSessionOverview failed", it)
        }.getOrDefault("")

        if (overview.isNotEmpty()) {
            runCatching {
                conversationRepo.updateConversation(conversation.copy(sessionOverview = overview))
            }.onFailure { Log.w(TAG, "persist sessionOverview failed", it) }
        }
        return overview
    }

    /**
     * Prepare a pending carryover from [conversation] for optional import into a new chat.
     * Returns null if the chat is too short or summarization failed / empty.
     */
    suspend fun prepareCarryoverFrom(
        conversation: Conversation,
        settings: Settings,
    ): PendingSessionCarryover? {
        if (!shouldOfferOverview(conversation)) return null
        val overview = ensureSessionOverview(conversation, settings)
        if (overview.isBlank()) return null
        val carryover = PendingSessionCarryover(
            assistantId = conversation.assistantId,
            sourceConversationId = conversation.id,
            sourceTitle = conversation.title.ifBlank { "上一会话" },
            overview = overview,
        )
        pending.set(carryover)
        return carryover
    }

    companion object {
        val SESSION_OVERVIEW_PROMPT =
            """
            Write a concise session overview for carrying context into a NEW chat.
            Include: topics discussed, important facts/preferences the user shared, funny or memorable anecdotes
            (keep punchlines / key details), open questions, and any agreements or plans.
            Use short bullet points. Do not invent facts. Keep under ~400 Chinese characters or ~250 English words.
            """.trimIndent()

        fun buildOfferMessage(carryover: PendingSessionCarryover): UIMessage {
            return UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Text(
                        text = carryover.overview,
                        metadata = buildJsonObject {
                            put(META_UI, CARRYOVER_OFFER_UI)
                            put(META_SOURCE_TITLE, carryover.sourceTitle)
                            put(META_SOURCE_ID, carryover.sourceConversationId.toString())
                        },
                    )
                ),
            )
        }

        fun buildOfferNode(carryover: PendingSessionCarryover): MessageNode =
            buildOfferMessage(carryover).toMessageNode()
    }
}

fun UIMessagePart.Text.isCarryoverOffer(): Boolean =
    metadata?.get(META_UI)?.jsonPrimitive?.contentOrNull == CARRYOVER_OFFER_UI

fun UIMessage.isCarryoverOffer(): Boolean =
    parts.filterIsInstance<UIMessagePart.Text>().any { it.isCarryoverOffer() }

fun UIMessage.carryoverOfferSourceTitle(): String? =
    parts.filterIsInstance<UIMessagePart.Text>()
        .firstOrNull { it.isCarryoverOffer() }
        ?.metadata
        ?.get(META_SOURCE_TITLE)
        ?.jsonPrimitive
        ?.contentOrNull

fun List<UIMessage>.withoutCarryoverOffers(): List<UIMessage> =
    filterNot { it.isCarryoverOffer() }

internal fun buildCarryoverOverviewPrompt(overview: String, sourceTitle: String? = null) =
    buildString {
        appendLine()
        append("**Previous session overview (user chose to import)**")
        appendLine()
        if (!sourceTitle.isNullOrBlank()) {
            appendLine("Source chat: $sourceTitle")
        }
        appendLine(
            "The user started a new conversation but imported an overview of the previous one. " +
                "Treat facts and anecdotes here as known shared context. Do not pretend you forgot them."
        )
        appendLine()
        append(overview.trim())
        appendLine()
    }
