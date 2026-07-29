package com.agent.chat.data.conversationstate

import com.agent.chat.domain.model.ConversationStateKind
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.LingBanChatMode
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话级运行时状态管理（内存驻留，带自动衰减）。
 *
 * - 只影响当前对话轮次
 * - 不写入 Room / 不修改 Persona / Relationship
 */
@Singleton
class ConversationStateManager @Inject constructor(
    private val engine: ConversationStateEngine,
) {
    private val cache = ConcurrentHashMap<String, ConversationStateSnapshot>()

    fun get(conversationId: String): ConversationStateSnapshot {
        val current = cache[conversationId] ?: ConversationStateSnapshot.DEFAULT
        val decayed = applyDecay(current, System.currentTimeMillis())
        if (decayed != current) {
            cache[conversationId] = decayed
        }
        return decayed
    }

    fun updateOnUserMessage(
        conversationId: String,
        userMessage: String,
        chatMode: LingBanChatMode,
        now: Long = System.currentTimeMillis(),
    ): ConversationStateSnapshot {
        val previous = get(conversationId).let { applyDecay(it, now) }
        val next = engine.decide(
            userMessage = userMessage,
            previous = previous.takeIf { it.currentState != ConversationStateKind.NORMAL },
            chatMode = chatMode,
            now = now,
        )
        cache[conversationId] = next
        return next
    }

    fun onConversationInactive(conversationId: String) {
        val current = cache[conversationId] ?: return
        cache[conversationId] = current.copy(
            currentState = ConversationStateKind.NORMAL,
            confidence = 0f,
            trigger = "inactive_reset",
            timestamp = System.currentTimeMillis(),
        )
    }

    fun clear(conversationId: String) {
        cache.remove(conversationId)
    }

    fun applyDecay(
        snapshot: ConversationStateSnapshot,
        now: Long,
    ): ConversationStateSnapshot {
        if (snapshot.currentState == ConversationStateKind.NORMAL) return snapshot

        val elapsed = now - snapshot.timestamp
        if (elapsed >= IDLE_DECAY_MS) {
            return ConversationStateSnapshot(
                currentState = ConversationStateKind.NORMAL,
                confidence = 0f,
                trigger = "idle_reset",
                timestamp = now,
            )
        }

        val decayFactor = elapsed.toFloat() / IDLE_DECAY_MS.toFloat()
        val decayedConfidence = snapshot.confidence - decayFactor * DECAY_RATE
        if (decayedConfidence < ConversationStateSnapshot.ACTIVE_THRESHOLD) {
            return ConversationStateSnapshot(
                currentState = ConversationStateKind.NORMAL,
                confidence = 0f,
                trigger = "decay",
                timestamp = now,
            )
        }

        return snapshot.copy(confidence = decayedConfidence)
    }

    companion object {
        /** 超过此时长无新消息 → 重置为 NORMAL */
        const val IDLE_DECAY_MS = 20 * 60 * 1000L
        private const val DECAY_RATE = 0.35f
    }
}
