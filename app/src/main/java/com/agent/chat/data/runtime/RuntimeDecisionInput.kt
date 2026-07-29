package com.agent.chat.data.runtime

import com.agent.chat.domain.model.BehaviorPlan
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.InteractionPreference
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.RelationshipProfile

/**
 * Runtime Decision 输入：结构化上下文（数据，不是 Prompt 文本）。
 */
data class RuntimeDecisionInput(
    val persona: Persona? = null,
    val relationship: RelationshipProfile = RelationshipProfile(),
    val expression: ExpressionProfile = ExpressionProfile(),
    val interactionPreference: InteractionPreference = InteractionPreference(),
    val memories: List<Memory> = emptyList(),
    val conversationState: ConversationStateSnapshot = ConversationStateSnapshot.DEFAULT,
    val userMessage: String = "",
    val chatMode: LingBanChatMode = LingBanChatMode.COMPANION,
)
