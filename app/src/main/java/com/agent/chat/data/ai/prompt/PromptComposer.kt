package com.agent.chat.data.ai.prompt

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一 Prompt Builder：替代散落拼接。
 *
 * 输入：[PromptComposeRequest]（Persona / Memory / UserContext / History）
 * 输出：[PromptComposeResult].systemPrompt
 *
 * 顺序：
 * 1 LingBan Core Rules
 * 2 Human Conversation Rules
 * 3 Current Persona（含 Agent 差异片段）
 * 4 Relationship
 * 5 Expression Style
 * 6 Conversation State（运行时）
 * 7 Runtime Decision（行为计划）
 * 8 Relevant Memory
 * 9 Conversation Goal
 */
@Singleton
class PromptComposer @Inject constructor(
    lingBanCoreRulesBuilder: LingBanCoreRulesBuilder,
    humanConversationRulesBuilder: HumanConversationRulesBuilder,
    interactionPreferencePromptBuilder: InteractionPreferencePromptBuilder,
    personaPromptBuilder: PersonaPromptBuilder,
    relationshipPromptBuilder: RelationshipPromptBuilder,
    expressionPromptBuilder: ExpressionPromptBuilder,
    conversationStatePromptBuilder: ConversationStatePromptBuilder,
    runtimeDecisionPromptBuilder: RuntimeDecisionPromptBuilder,
    memoryPromptBuilder: MemoryPromptBuilder,
    conversationGoalPromptBuilder: ConversationGoalPromptBuilder,
    private val agentRegistry: AgentPromptRegistry,
    private val modelRegistry: ModelPromptRegistry,
    private val promptLogger: PromptLogger,
) {
    private val builders: List<PromptSectionBuilder> = listOf(
        lingBanCoreRulesBuilder,
        humanConversationRulesBuilder,
        interactionPreferencePromptBuilder,
        personaPromptBuilder,
        relationshipPromptBuilder,
        expressionPromptBuilder,
        conversationStatePromptBuilder,
        runtimeDecisionPromptBuilder,
        memoryPromptBuilder,
        conversationGoalPromptBuilder,
    )

    fun compose(request: PromptComposeRequest): PromptComposeResult {
        val agentId = agentRegistry.resolveAgentId(request.agentId, request.persona)
        val context = PromptBuildContext.from(request.copy(agentId = agentId))

        val sections = mutableListOf<PromptSection>()
        builders.forEach { sections += it.build(context) }
        sections += agentRegistry.adapter(agentId).extraSections(context)
        modelRegistry.resolve(request.modelName)?.let { sections += it.extraSections(context) }

        val ordered = sections
            .filter { it.content.isNotBlank() }
            .sortedWith(
                compareBy<PromptSection> { it.layer.order }
                    .thenBy { it.sortKey }
                    .thenBy { it.id },
            )

        val systemPrompt = ordered.joinToString("\n\n") { it.content.trim() }.trim()
        val result = PromptComposeResult(
            systemPrompt = systemPrompt,
            sections = ordered,
            meta = PromptComposeMeta(
                agentId = agentId,
                modelName = request.modelName,
                providerName = request.providerName,
                chatMode = context.chatMode,
                rolePlayEnabled = context.rolePlayEnabled,
                sectionIds = ordered.map { it.id },
                charCount = systemPrompt.length,
            ),
        )
        promptLogger.log(result, conversationId = request.conversationId)
        return result
    }

    /** 仅拼装不写日志（单元测试 / 预览） */
    fun composeDryRun(request: PromptComposeRequest): PromptComposeResult {
        val agentId = agentRegistry.resolveAgentId(request.agentId, request.persona)
        val context = PromptBuildContext.from(request.copy(agentId = agentId))
        val sections = builders.flatMap { it.build(context) }.toMutableList()
        sections += agentRegistry.adapter(agentId).extraSections(context)
        modelRegistry.resolve(request.modelName)?.let { sections += it.extraSections(context) }
        val ordered = sections
            .filter { it.content.isNotBlank() }
            .sortedWith(
                compareBy<PromptSection> { it.layer.order }
                    .thenBy { it.sortKey }
                    .thenBy { it.id },
            )
        val systemPrompt = ordered.joinToString("\n\n") { it.content.trim() }.trim()
        return PromptComposeResult(
            systemPrompt = systemPrompt,
            sections = ordered,
            meta = PromptComposeMeta(
                agentId = agentId,
                modelName = request.modelName,
                providerName = request.providerName,
                chatMode = context.chatMode,
                rolePlayEnabled = context.rolePlayEnabled,
                sectionIds = ordered.map { it.id },
                charCount = systemPrompt.length,
            ),
        )
    }
}
