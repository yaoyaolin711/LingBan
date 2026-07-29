package com.agent.chat.data.ai.prompt

import com.agent.chat.domain.model.BehaviorPlan
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.InteractionPreference
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.Persona
import com.agent.chat.domain.model.RelationshipProfile

/**
 * System Prompt 九层结构（数值越小越靠前）：
 * 1 LingBan Core → 2 Human Conversation → 3 Interaction Preference → 4 Persona →
 * 5 Relationship → 6 Expression → 7 Conversation State → 8 Runtime Decision →
 * 9 Memory → 10 Goal
 */
enum class PromptLayer(val order: Int) {
    LINGBAN_CORE(0),
    HUMAN_CONVERSATION(1),
    INTERACTION_PREFERENCE(2),
    PERSONA(3),
    RELATIONSHIP(4),
    EXPRESSION(5),
    CONVERSATION_STATE(6),
    RUNTIME_DECISION(7),
    MEMORY(8),
    CONVERSATION_GOAL(9),
}

data class PromptSection(
    val id: String,
    val layer: PromptLayer,
    val content: String,
    val sortKey: Int = 0,
)

/** 用户侧上下文（昵称 / 画像 / 目标） */
data class UserContext(
    val nickname: String = "",
    val interest: String = "",
    val occupation: String = "",
    val goal: String = "",
)

/** PromptComposer 输入 */
data class PromptComposeRequest(
    val persona: Persona? = null,
    val memories: List<Memory> = emptyList(),
    val userContext: UserContext = UserContext(),
    val conversationHistory: List<Message> = emptyList(),
    val careContext: String = "",
    val conversationGoal: String = "",
    val agentId: String = AgentPromptIds.COMPANION,
    val modelName: String? = null,
    val providerName: String? = null,
    val conversationId: String? = null,
    val baseHumanEnabled: Boolean = true,
    val chatMode: LingBanChatMode = LingBanChatMode.COMPANION,
    /** @deprecated 使用 [chatMode]；保留兼容 */
    val rolePlayEnabled: Boolean = false,
    /** 会话级关系档案（独立于 Persona） */
    val relationshipProfile: RelationshipProfile = RelationshipProfile(),
    /** 表达风格档案（独立于 Persona） */
    val expressionProfile: ExpressionProfile = ExpressionProfile(),
    /** 用户全局互动边界偏好 */
    val interactionPreference: InteractionPreference = InteractionPreference(),
    /** 本轮用户消息（用于意图判定；空则从 history 推断） */
    val userMessage: String = "",
    /** 会话运行时交流状态（临时、可衰减） */
    val conversationState: ConversationStateSnapshot = ConversationStateSnapshot.DEFAULT,
    /** Runtime Decision 输出的行为计划；非 null 时替代 Relationship/Expression/State 的行为 Prompt */
    val behaviorPlan: BehaviorPlan? = null,
)

data class PromptComposeMeta(
    val agentId: String,
    val modelName: String?,
    val providerName: String?,
    val chatMode: LingBanChatMode,
    val rolePlayEnabled: Boolean,
    val sectionIds: List<String>,
    val charCount: Int,
)

data class PromptComposeResult(
    val systemPrompt: String,
    val sections: List<PromptSection>,
    val meta: PromptComposeMeta,
)

/** 内部组装上下文（Builder 使用） */
data class PromptBuildContext(
    val persona: Persona? = null,
    val memories: List<Memory> = emptyList(),
    val recentMessages: List<Message> = emptyList(),
    val careContext: String = "",
    val userNickname: String = "",
    val userInterest: String = "",
    val userOccupation: String = "",
    val userGoal: String = "",
    val conversationGoal: String = "",
    val agentId: String = AgentPromptIds.COMPANION,
    val modelName: String? = null,
    val baseHumanEnabled: Boolean = true,
    val chatMode: LingBanChatMode = LingBanChatMode.COMPANION,
    val rolePlayEnabled: Boolean = false,
    val relationshipProfile: RelationshipProfile = RelationshipProfile(),
    val expressionProfile: ExpressionProfile = ExpressionProfile(),
    val interactionPreference: InteractionPreference = InteractionPreference(),
    val userMessage: String = "",
    val conversationState: ConversationStateSnapshot = ConversationStateSnapshot.DEFAULT,
    val behaviorPlan: BehaviorPlan? = null,
) {
    companion object {
        fun from(request: PromptComposeRequest): PromptBuildContext {
            val mode = when {
                request.rolePlayEnabled && request.chatMode == LingBanChatMode.COMPANION ->
                    LingBanChatMode.ROLEPLAY
                else -> request.chatMode
            }
            return PromptBuildContext(
                persona = request.persona,
                memories = request.memories,
                recentMessages = request.conversationHistory,
                careContext = request.careContext,
                userNickname = request.userContext.nickname,
                userInterest = request.userContext.interest,
                userOccupation = request.userContext.occupation,
                userGoal = request.userContext.goal,
                conversationGoal = request.conversationGoal.ifBlank { request.userContext.goal },
                agentId = request.agentId,
                modelName = request.modelName,
                baseHumanEnabled = request.baseHumanEnabled,
                chatMode = mode,
                rolePlayEnabled = mode == LingBanChatMode.ROLEPLAY,
                relationshipProfile = request.relationshipProfile,
                expressionProfile = request.expressionProfile,
                interactionPreference = request.interactionPreference,
                userMessage = request.userMessage.ifBlank {
                    request.conversationHistory.asReversed()
                        .firstOrNull { it.role == com.agent.chat.domain.model.MessageRole.USER }
                        ?.content
                        .orEmpty()
                },
                conversationState = request.conversationState,
                behaviorPlan = request.behaviorPlan,
            )
        }
    }
}

fun interface PromptSectionBuilder {
    fun build(context: PromptBuildContext): List<PromptSection>
}

object AgentPromptIds {
    const val COMPANION = "companion"
    const val CODING = "coding"
    const val TUTOR = "tutor"
    const val CREATIVE = "creative"
}
