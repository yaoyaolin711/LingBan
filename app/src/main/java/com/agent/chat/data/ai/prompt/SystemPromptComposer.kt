package com.agent.chat.data.ai.prompt

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 兼容旧注入点：委托给 [PromptComposer]。
 * 新代码请直接使用 [PromptComposer]。
 */
@Singleton
class SystemPromptComposer @Inject constructor(
    private val promptComposer: PromptComposer,
) {
    fun compose(context: PromptBuildContext): String {
        val result = promptComposer.compose(
            PromptComposeRequest(
                persona = context.persona,
                memories = context.memories,
                userContext = UserContext(
                    nickname = context.userNickname,
                    interest = context.userInterest,
                    occupation = context.userOccupation,
                    goal = context.userGoal,
                ),
                conversationHistory = context.recentMessages,
                careContext = context.careContext,
                conversationGoal = context.conversationGoal,
                agentId = context.agentId,
                modelName = context.modelName,
                baseHumanEnabled = context.baseHumanEnabled,
                rolePlayEnabled = context.rolePlayEnabled,
                chatMode = context.chatMode,
                relationshipProfile = context.relationshipProfile,
                expressionProfile = context.expressionProfile,
                interactionPreference = context.interactionPreference,
                userMessage = context.userMessage,
                conversationState = context.conversationState,
                behaviorPlan = context.behaviorPlan,
            ),
        )
        return result.systemPrompt
    }

    fun composeSections(context: PromptBuildContext): List<PromptSection> =
        promptComposer.composeDryRun(
            PromptComposeRequest(
                persona = context.persona,
                memories = context.memories,
                userContext = UserContext(
                    nickname = context.userNickname,
                    interest = context.userInterest,
                    occupation = context.userOccupation,
                    goal = context.userGoal,
                ),
                conversationHistory = context.recentMessages,
                careContext = context.careContext,
                conversationGoal = context.conversationGoal,
                agentId = context.agentId,
                modelName = context.modelName,
                baseHumanEnabled = context.baseHumanEnabled,
                rolePlayEnabled = context.rolePlayEnabled,
                chatMode = context.chatMode,
                relationshipProfile = context.relationshipProfile,
                expressionProfile = context.expressionProfile,
                interactionPreference = context.interactionPreference,
                userMessage = context.userMessage,
                conversationState = context.conversationState,
                behaviorPlan = context.behaviorPlan,
            ),
        ).sections
}
