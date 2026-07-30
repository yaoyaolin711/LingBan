package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.companion.model.CompanionPromptBlock
import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid

object CompanionPromptTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<me.rerere.ai.ui.UIMessage>,
    ): List<me.rerere.ai.ui.UIMessage> {
        if (ctx.companionPromptBlocks.isEmpty()) return messages

        val grouped = ctx.companionPromptBlocks
            .filter { it.enabled && it.content.isNotBlank() }
            .sortedBy { it.order }
            .map { it.toSyntheticInjection() }
            .groupBy { it.position }

        return applyInjections(messages, grouped)
    }

    private fun CompanionPromptBlock.toSyntheticInjection(): PromptInjection.ModeInjection {
        return PromptInjection.ModeInjection(
            id = Uuid.random(),
            name = type,
            enabled = enabled,
            priority = -order,
            position = position,
            content = content,
            injectDepth = injectDepth,
            role = role,
        )
    }
}
