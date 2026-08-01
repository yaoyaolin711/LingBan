package me.rerere.rikkahub.data.companion.policy

import me.rerere.rikkahub.data.companion.CompanionStateStore
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.model.CompanionState
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import kotlin.uuid.Uuid

data class CompanionEmotionContext(
    val emotion: CompanionEmotionState,
    val conversation: Conversation?,
    val state: CompanionState?,
)

/**
 * 按助手最近会话读取持久化情绪（CompanionState 按 conversationId 存储）。
 */
class CompanionEmotionResolver(
    private val stateStore: CompanionStateStore,
    private val conversationRepo: ConversationRepository,
) {
    suspend fun resolveForAssistant(assistantId: Uuid): CompanionEmotionState =
        resolveContext(assistantId).emotion

    suspend fun resolveContext(assistantId: Uuid): CompanionEmotionContext {
        val recent = conversationRepo.getRecentConversations(assistantId, limit = 1)
            .firstOrNull()
            ?: return CompanionEmotionContext(
                emotion = CompanionEmotionState.CALM,
                conversation = null,
                state = null,
            )
        val state = stateStore.getState(recent.id)
        return CompanionEmotionContext(
            emotion = state.relationshipState.emotionState,
            conversation = recent,
            state = state,
        )
    }
}
