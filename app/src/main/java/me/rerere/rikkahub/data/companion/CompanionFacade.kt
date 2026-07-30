package me.rerere.rikkahub.data.companion

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.companion.model.InteractionSuggestion
import me.rerere.rikkahub.data.companion.model.CompanionPromptBundle
import me.rerere.rikkahub.data.datastore.Settings
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
        val previewBundle = promptBuilder.buildBundle(
            conversationId = conversationId,
            assistant = assistant,
            character = character,
            persona = persona,
            state = state,
            messages = messages,
            relationshipContext = state.relationshipState.relationshipContext,
            behaviorPolicy = behaviorPolicy,
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
        if (!settings.companionAssist.proactiveChatEnabled) return null
        val state = stateStore.getState(conversation.id)
        return proactiveTriggerManager.evaluateSuggestion(
            settings = settings,
            conversation = conversation,
            state = state,
        )
    }
}
