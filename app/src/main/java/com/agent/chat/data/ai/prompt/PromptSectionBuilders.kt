package com.agent.chat.data.ai.prompt

import com.agent.chat.data.ai.PromptContextInjector
import com.agent.chat.data.conversationstate.ConversationStateEngine
import com.agent.chat.data.runtime.RuntimeDecisionEngine
import com.agent.chat.data.expression.ExpressionEngine
import com.agent.chat.data.interaction.InteractionPreferenceEngine
import com.agent.chat.data.interaction.InteractionTendencyContext
import com.agent.chat.data.mode.ModeManager
import com.agent.chat.data.relationship.RelationshipEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LingBanCoreRulesBuilder @Inject constructor(
    private val baseHumanPrompt: BaseHumanPrompt,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        val content = baseHumanPrompt.lingBanCore()
        if (content.isBlank()) return emptyList()
        return listOf(
            PromptSection(
                id = BaseHumanPrompt.ID_CORE,
                layer = PromptLayer.LINGBAN_CORE,
                content = content,
                sortKey = 0,
            ),
        )
    }
}

@Singleton
class HumanConversationRulesBuilder @Inject constructor(
    private val modeManager: ModeManager,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        if (!context.baseHumanEnabled) return emptyList()
        val mode = context.chatMode
        val sections = mutableListOf<PromptSection>()

        val modeSection = modeManager.loadModeSection(mode)
        if (modeSection.isNotBlank()) {
            sections += PromptSection(
                id = "mode_${mode.storageKey}",
                layer = PromptLayer.HUMAN_CONVERSATION,
                content = modeSection,
                sortKey = 0,
            )
        }

        val humanRules = modeManager.loadHumanRules(mode)
        if (humanRules.isNotBlank()) {
            sections += PromptSection(
                id = "human_rules_${mode.storageKey}",
                layer = PromptLayer.HUMAN_CONVERSATION,
                content = humanRules,
                sortKey = 1,
            )
        }
        return sections
    }
}

/** @deprecated 名称保留；逻辑已拆到 [LingBanCoreRulesBuilder] / [HumanConversationRulesBuilder] */
@Deprecated("Use LingBanCoreRulesBuilder + HumanConversationRulesBuilder")
@Singleton
class BaseHumanPromptBuilder @Inject constructor(
    private val human: HumanConversationRulesBuilder,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> = human.build(context)
}

@Singleton
class InteractionPreferencePromptBuilder @Inject constructor(
    private val engine: InteractionPreferenceEngine,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        val content = engine.buildPromptSection(
            InteractionTendencyContext(
                preference = context.interactionPreference,
                userMessage = context.userMessage,
                relationship = context.relationshipProfile,
                persona = context.persona,
                conversationState = context.conversationState,
            ),
        )
        if (content.isBlank()) return emptyList()
        return listOf(
            PromptSection(
                id = "interaction_preference",
                layer = PromptLayer.INTERACTION_PREFERENCE,
                content = content,
                sortKey = 0,
            ),
        )
    }
}

@Singleton
class PersonaPromptBuilder @Inject constructor(
    private val assets: PromptAssetLoader,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        val persona = context.persona ?: return emptyList()
        val sections = mutableListOf<PromptSection>()

        val raw = persona.systemPrompt.trim()
        if (raw.isNotEmpty()) {
            sections += PromptSection(
                id = "persona_system",
                layer = PromptLayer.PERSONA,
                content = PromptContextInjector.applyPlaceholders(
                    text = raw,
                    persona = persona,
                    userNickname = context.userNickname,
                ),
                sortKey = 0,
            )
        }

        val lore = PromptContextInjector.matchLorebook(
            persona = persona,
            recentMessages = context.recentMessages,
            userNickname = context.userNickname,
        )
        if (lore.isNotEmpty()) {
            val header = assets.label(
                "lore_header",
                "【相关设定】（由对话关键词触发，请自然融入，勿生硬宣读）",
            )
            val itemTemplate = assets.label("lore_item", "- {{entry}}")
            val body = lore.joinToString("\n") { entry ->
                assets.render(itemTemplate, mapOf("entry" to entry))
            }
            sections += PromptSection(
                id = "lorebook",
                layer = PromptLayer.PERSONA,
                content = "$header\n$body",
                sortKey = 20,
            )
        }
        return sections
    }
}

@Singleton
class RelationshipPromptBuilder @Inject constructor(
    private val relationshipEngine: RelationshipEngine,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        if (context.behaviorPlan != null) return emptyList()
        val content = relationshipEngine.buildPromptSection(context.relationshipProfile)
        if (content.isBlank()) return emptyList()
        return listOf(
            PromptSection(
                id = "relationship_${context.relationshipProfile.relationshipType.storageKey}",
                layer = PromptLayer.RELATIONSHIP,
                content = content,
                sortKey = 0,
            ),
        )
    }
}

@Singleton
class ExpressionPromptBuilder @Inject constructor(
    private val expressionEngine: ExpressionEngine,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        if (context.behaviorPlan != null) return emptyList()
        val content = expressionEngine.buildPromptSection(context.expressionProfile)
        if (content.isBlank()) return emptyList()
        return listOf(
            PromptSection(
                id = "expression_style",
                layer = PromptLayer.EXPRESSION,
                content = content,
                sortKey = 0,
            ),
        )
    }
}

@Singleton
class ConversationStatePromptBuilder @Inject constructor(
    private val engine: ConversationStateEngine,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        if (context.behaviorPlan != null) return emptyList()
        val content = engine.buildPromptSection(context.conversationState)
        if (content.isBlank()) return emptyList()
        return listOf(
            PromptSection(
                id = "conversation_state",
                layer = PromptLayer.CONVERSATION_STATE,
                content = content,
                sortKey = 0,
            ),
        )
    }
}

@Singleton
class RuntimeDecisionPromptBuilder @Inject constructor(
    private val engine: RuntimeDecisionEngine,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        val plan = context.behaviorPlan ?: return emptyList()
        val content = engine.buildPromptSection(plan, context.persona?.name)
        if (content.isBlank()) return emptyList()
        return listOf(
            PromptSection(
                id = "runtime_decision",
                layer = PromptLayer.RUNTIME_DECISION,
                content = content,
                sortKey = 0,
            ),
        )
    }
}

@Singleton
class MemoryPromptBuilder @Inject constructor(
    private val assets: PromptAssetLoader,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        if (context.memories.isEmpty()) return emptyList()
        val header = assets.label(
            "memory_header",
            "【相关记忆】仅注入与当前问题相关的条目（可引用但勿整段朗读）：",
        )
        val itemTemplate = assets.label(
            "memory_item",
            "- [{{category}}|重要性{{importance}}|id={{id}}] {{content}}",
        )
        val body = context.memories.joinToString("\n") { m ->
            assets.render(
                itemTemplate,
                mapOf(
                    "id" to m.id,
                    "content" to m.content.trim(),
                    "category" to m.category.displayName,
                    "importance" to m.importance.toString(),
                ),
            )
        }
        return listOf(
            PromptSection(
                id = "memory",
                layer = PromptLayer.MEMORY,
                content = "$header\n$body",
                sortKey = 0,
            ),
        )
    }
}

@Singleton
class ConversationGoalPromptBuilder @Inject constructor(
    private val assets: PromptAssetLoader,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> {
        val parts = mutableListOf<String>()
        val header = assets.label(
            "goal_header",
            "【Conversation Goal】本轮对话目标与情境：",
        )
        parts += header

        val itemTemplate = assets.label("goal_item", "- {{line}}")
        fun bullet(line: String) = assets.render(itemTemplate, mapOf("line" to line))

        val goal = context.conversationGoal.trim().ifBlank { context.userGoal.trim() }
        if (goal.isNotBlank()) parts += bullet("目标：$goal")

        if (context.userNickname.isNotBlank()) parts += bullet("姓名：${context.userNickname}")
        if (context.userInterest.isNotBlank()) parts += bullet("兴趣：${context.userInterest}")
        if (context.userOccupation.isNotBlank()) parts += bullet("职业：${context.userOccupation}")

        val care = context.careContext.trim()
        if (care.isNotEmpty()) {
            parts += care
        } else {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm E", Locale.getDefault()).format(Date())
            val template = assets.label("time_fallback", "【当前时间】{{time}}")
            parts += assets.render(template, mapOf("time" to now))
        }

        val lastUser = context.recentMessages
            .asReversed()
            .firstOrNull { it.role == com.agent.chat.domain.model.MessageRole.USER }
            ?.content
            ?.trim()
            .orEmpty()
        if (lastUser.isNotBlank()) {
            parts += bullet("本轮用户焦点：${lastUser.take(120)}")
        }

        // 仅有 header 时不输出
        if (parts.size <= 1) return emptyList()

        return listOf(
            PromptSection(
                id = "conversation_goal",
                layer = PromptLayer.CONVERSATION_GOAL,
                content = parts.joinToString("\n"),
                sortKey = 0,
            ),
        )
    }
}

/** @deprecated 使用 [ConversationGoalPromptBuilder] */
@Deprecated("Use ConversationGoalPromptBuilder")
@Singleton
class ConversationContextPromptBuilder @Inject constructor(
    private val goal: ConversationGoalPromptBuilder,
) : PromptSectionBuilder {
    override fun build(context: PromptBuildContext): List<PromptSection> = goal.build(context)
}
