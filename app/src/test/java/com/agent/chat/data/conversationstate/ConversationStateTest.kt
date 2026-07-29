package com.agent.chat.data.conversationstate

import com.agent.chat.domain.model.ConversationStateKind
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.LingBanChatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEmotionDetectorTest {

    @Test
    fun stressMessage_detected() {
        val signal = ConversationEmotionDetector.detect("今天压力很大，好累")
        assertTrue(signal.score >= 0.35f)
        assertTrue(signal.labels.contains("stress"))
    }

    @Test
    fun neutralMessage_lowScore() {
        val signal = ConversationEmotionDetector.detect("帮我查一下天气")
        assertTrue(signal.score < 0.35f)
    }
}

class ConversationStateIntentDetectorTest {

    @Test
    fun technicalMessage_mapsToKnowledge() {
        val candidates = ConversationStateIntentDetector.detect(
            userMessage = "Kotlin 里怎么实现 Retrofit？",
            chatMode = LingBanChatMode.COMPANION,
        )
        assertEquals(ConversationStateKind.KNOWLEDGE, candidates.first().state)
    }

    @Test
    fun playfulMessage_mapsToPlayful() {
        val candidates = ConversationStateIntentDetector.detect(
            userMessage = "哈哈哈你太逗了",
            chatMode = LingBanChatMode.COMPANION,
        )
        assertEquals(ConversationStateKind.PLAYFUL, candidates.first().state)
    }
}

class ConversationStateEngineTest {

    private val engine = ConversationStateEngine()

    @Test
    fun stressMessage_entersEmotionalSupport() {
        val snapshot = engine.decide(
            userMessage = "今天压力很大",
            previous = null,
            chatMode = LingBanChatMode.COMPANION,
        )
        assertEquals(ConversationStateKind.EMOTIONAL_SUPPORT, snapshot.currentState)
        assertTrue(snapshot.confidence >= ConversationStateSnapshot.ACTIVE_THRESHOLD)
    }

    @Test
    fun promptSection_preservesPersonaConstraint() {
        val snapshot = ConversationStateSnapshot(
            currentState = ConversationStateKind.EMOTIONAL_SUPPORT,
            confidence = 0.8f,
            trigger = "emotion:stress",
        )
        val section = engine.buildPromptSection(snapshot)
        assertTrue(section.contains("不得覆盖 Persona"))
        assertTrue(section.contains("高冷程序员"))
    }

    @Test
    fun normalState_noPromptSection() {
        val section = engine.buildPromptSection(ConversationStateSnapshot.DEFAULT)
        assertTrue(section.isBlank())
    }
}

class ConversationStateManagerTest {

    private val manager = ConversationStateManager(ConversationStateEngine())

    @Test
    fun updateOnUserMessage_storesStatePerConversation() {
        val state = manager.updateOnUserMessage(
            conversationId = "c1",
            userMessage = "今天好累啊",
            chatMode = LingBanChatMode.COMPANION,
        )
        assertEquals(ConversationStateKind.EMOTIONAL_SUPPORT, state.currentState)
        assertEquals(state.currentState, manager.get("c1").currentState)
    }

    @Test
    fun idleDecay_resetsToNormal() {
        val stale = ConversationStateSnapshot(
            currentState = ConversationStateKind.EMOTIONAL_SUPPORT,
            confidence = 0.7f,
            trigger = "emotion:stress",
            timestamp = System.currentTimeMillis() - ConversationStateManager.IDLE_DECAY_MS - 1,
        )
        val decayed = manager.applyDecay(stale, System.currentTimeMillis())
        assertEquals(ConversationStateKind.NORMAL, decayed.currentState)
    }

    @Test
    fun onConversationInactive_resetsState() {
        manager.updateOnUserMessage("c2", "今天好累", LingBanChatMode.COMPANION)
        manager.onConversationInactive("c2")
        assertEquals(ConversationStateKind.NORMAL, manager.get("c2").currentState)
    }
}
