package me.rerere.rikkahub.data.groupchat

import android.util.Log
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.service.backgroundTextGenerationParams
import kotlin.uuid.Uuid

private const val TAG = "LlmSoftScheduler"

/**
 * SoftScheduler backed by the configured group scheduler / fast model.
 * Failures fall back to [fallback] (fail-safe toward ending the round via HardGate).
 */
class LlmSoftScheduler(
    private val providerManager: ProviderManager,
    private val settingsProvider: () -> Settings,
    private val fallback: SoftScheduler = RulesFallbackScheduler(),
) : SoftScheduler {
    override suspend fun decide(
        context: GroupScheduleContext,
        candidates: List<Uuid>,
        transcript: List<GroupTranscriptLine>,
    ): SchedulerDecision {
        if (candidates.isEmpty()) return SchedulerDecision.endRound("no_candidates")

        val decision = runCatching {
            val settings = settingsProvider()
            val model = settings.findModelById(
                settings.groupSchedulerModelId,
                fallback = settings.fastModelId,
            ) ?: settings.getCurrentChatModel()
                ?: error("No scheduler model")
            val provider = model.findProvider(settings.providers)
                ?: error("Provider not found")
            val handler = providerManager.getProviderByType(provider)
            val prompt = buildSchedulerPrompt(context, candidates, transcript)
            val result = handler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = backgroundTextGenerationParams(model),
            )
            val text = result.choices[0].message?.toText()?.trim().orEmpty()
            parseSchedulerDecisionJson(text)
                ?: error("Failed to parse scheduler JSON: $text")
        }.onFailure {
            Log.w(TAG, "LLM scheduler failed, using rules fallback", it)
        }.getOrNull()

        return decision ?: fallback.decide(context, candidates, transcript)
    }
}
