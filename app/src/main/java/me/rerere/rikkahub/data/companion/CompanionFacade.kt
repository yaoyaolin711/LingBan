package me.rerere.rikkahub.data.companion

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.companion.model.InteractionSuggestion
import me.rerere.rikkahub.data.companion.model.CompanionPromptBundle
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.health.HealthConnectRepository
import me.rerere.rikkahub.data.life.LifeContextResolver
import me.rerere.rikkahub.data.life.RestSource
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

class CompanionFacade(
    private val stateStore: CompanionStateStore,
    private val characterManager: CharacterManager,
    private val personaManager: PersonaManager,
    private val memoryManager: MemoryManager,
    private val relationshipManager: RelationshipManager,
    private val behaviorPolicyManager: BehaviorPolicyManager,
    private val proactiveTriggerManager: ProactiveTriggerManager,
    private val promptBuilder: PromptBuilder,
    private val promptCache: PromptCache,
    private val healthConnectRepository: HealthConnectRepository,
    private val lifeContextResolver: LifeContextResolver,
    private val memoryRepository: me.rerere.rikkahub.data.repository.MemoryRepository? = null,
) {
    suspend fun preparePromptBundle(
        conversationId: Uuid,
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
    ): CompanionPromptBundle? {
        if (!assistant.enableCompanion) return null

        val state = stateStore.getState(conversationId)
        val character = characterManager.getCharacter(assistant)
        val persona = personaManager.getPersona(settings)
        val behaviorPolicy = behaviorPolicyManager.resolvePolicyAsync(state)

        val lifeSnapshot = if (settings.lifeContext.enabled) {
            runCatching { lifeContextResolver.readSnapshot(settings) }.getOrNull()
        } else {
            null
        }
        val lifeContext = lifeContextResolver.formatForPrompt(lifeSnapshot)

        val healthContext = if (settings.healthConnect.enabled) {
            runCatching {
                val summary = healthConnectRepository.readDailySummary(settings.healthConnect)
                // 休息窗已由 life_context 表达时，避免 health 块重复堆睡眠
                val healthSetting = if (lifeSnapshot?.source == RestSource.HEALTH_CONNECT &&
                    lifeSnapshot.isInjectable
                ) {
                    settings.healthConnect.copy(includeSleep = false)
                } else {
                    settings.healthConnect
                }
                healthConnectRepository.formatSummaryForPrompt(summary, healthSetting)
            }.getOrDefault("")
        } else {
            ""
        }
        val previewBundle = promptBuilder.buildBundle(
            conversationId = conversationId,
            assistant = assistant,
            character = character,
            persona = persona,
            state = state,
            messages = messages,
            relationshipContext = state.relationshipState.relationshipContext,
            behaviorPolicy = behaviorPolicy,
            healthContext = healthContext,
            lifeContext = lifeContext,
        )
        promptCache.get(previewBundle.cacheKey)?.let { return it }
        return promptCache.put(conversationId, previewBundle)
    }

    suspend fun onGenerationCompleted(
        conversationId: Uuid,
        assistant: Assistant,
        settings: Settings,
        conversation: Conversation,
    ) {
        if (!assistant.enableCompanion) return

        val currentState = stateStore.getState(conversationId)
        val memoryUpdated = memoryManager.updateState(currentState, conversation.currentMessages)
        val relationshipUpdated = relationshipManager.updateStateAsync(memoryUpdated, conversation.currentMessages)
        if (relationshipUpdated != currentState) {
            stateStore.saveState(conversationId, relationshipUpdated)
            promptCache.invalidate(conversationId)
        }

        // Optional high-confidence Companion long facts → Room L2 (default off).
        if (assistant.enableMemory && me.rerere.rikkahub.data.memory.CompanionMemorySyncGate.enabled) {
            val scope = if (assistant.useGlobalMemory) {
                me.rerere.rikkahub.data.repository.MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            runCatching {
                memoryRepository?.syncCompanionLongFacts(
                    assistantId = scope,
                    facts = relationshipUpdated.longMemoryFacts,
                    enabled = true,
                )
            }
        }

        // Touch the lightweight caches so the next request reuses parsed sources.
        characterManager.getCharacter(assistant)
        personaManager.getPersona(settings)
    }

    suspend fun buildInteractionSuggestion(
        assistant: Assistant,
        settings: Settings,
        conversation: Conversation,
    ): InteractionSuggestion? {
        if (!assistant.enableCompanion) return null
        if (!assistant.proactiveChatEnabled && !settings.companionAssist.proactiveChatEnabled) return null
        val state = stateStore.getState(conversation.id)
        return proactiveTriggerManager.evaluateSuggestion(
            settings = settings,
            conversation = conversation,
            state = state,
        )
    }
}
