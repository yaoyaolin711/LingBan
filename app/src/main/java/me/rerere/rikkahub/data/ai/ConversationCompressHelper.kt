package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.RollingSummaryRequest
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.service.backgroundTextGenerationParams
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale

/** Default target size for automatic rolling summaries. */
const val DEFAULT_AUTO_SUMMARY_TARGET_TOKENS = 768

val AUTO_CONTEXT_SUMMARY_ADDITIONAL_PROMPT =
    "Preserve agreements, decisions, user preferences, names, open tasks, and any facts needed to continue the conversation coherently."

class ConversationCompressHelper(
    private val providerManager: ProviderManager,
) {
    suspend fun compressMessageChunks(
        settings: Settings,
        messages: List<UIMessage>,
        targetTokens: Int,
        additionalPrompt: String = "",
        maxMessagesPerChunk: Int = 256,
    ): List<String> {
        if (messages.isEmpty()) return emptyList()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel()
            ?: error("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: error("Provider not found")
        val providerHandler = providerManager.getProviderByType(provider)
        val params = backgroundTextGenerationParams(model)

        suspend fun compressOne(chunk: List<UIMessage>): String {
            val contentToCompress = chunk.joinToString("\n\n") { it.summaryAsText(maxLength = 2000) }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = params,
            )
            return result.choices[0].message?.toText()?.trim()
                ?: error("Failed to generate compressed summary")
        }

        return splitMessages(messages, maxMessagesPerChunk).map { compressOne(it) }
    }

    /**
     * Build or merge a rolling summary for the uncovered prefix in one compression pass when possible.
     */
    suspend fun buildRollingSummary(
        settings: Settings,
        request: RollingSummaryRequest,
        targetTokens: Int = DEFAULT_AUTO_SUMMARY_TARGET_TOKENS,
    ): String {
        val previous = request.previousSummary
        if (request.uncoveredMessages.isEmpty()) {
            return previous?.takeIf { it.isNotBlank() }
                ?: error("Nothing to summarize")
        }

        val contentMessages = if (previous.isNullOrBlank()) {
            request.uncoveredMessages
        } else {
            listOf(
                UIMessage.user(
                    buildString {
                        appendLine("Previous conversation summary:")
                        appendLine(previous)
                        appendLine()
                        appendLine("Newly truncated messages to merge in:")
                        append(
                            request.uncoveredMessages.joinToString("\n\n") {
                                it.summaryAsText(maxLength = 2000)
                            }
                        )
                    }
                )
            )
        }

        val additional = if (previous.isNullOrBlank()) {
            AUTO_CONTEXT_SUMMARY_ADDITIONAL_PROMPT
        } else {
            "$AUTO_CONTEXT_SUMMARY_ADDITIONAL_PROMPT Merge into one coherent summary; do not drop facts from the previous summary."
        }

        return compressMessageChunks(
            settings = settings,
            messages = contentMessages,
            targetTokens = targetTokens,
            additionalPrompt = additional,
        ).joinToString("\n\n").trim()
    }

    private fun splitMessages(messages: List<UIMessage>, maxMessagesPerChunk: Int): List<List<UIMessage>> {
        if (messages.size <= maxMessagesPerChunk) return listOf(messages)
        val mid = messages.size / 2
        return splitMessages(messages.subList(0, mid), maxMessagesPerChunk) +
            splitMessages(messages.subList(mid, messages.size), maxMessagesPerChunk)
    }
}
