package com.agent.chat.data.ai.prompt

import com.agent.chat.domain.model.Persona
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按 Agent 类型注入差异化片段，支持多 Agent 扩展。
 */
fun interface AgentPromptAdapter {
    fun extraSections(context: PromptBuildContext): List<PromptSection>
}

@Singleton
class AgentPromptRegistry @Inject constructor(
    private val assets: PromptAssetLoader,
) {
    fun resolveAgentId(explicit: String?, persona: Persona?): String {
        if (!explicit.isNullOrBlank() && explicit != AgentPromptIds.COMPANION) {
            return explicit
        }
        return inferFromPersona(persona) ?: AgentPromptIds.COMPANION
    }

    fun adapter(agentId: String): AgentPromptAdapter = when (agentId) {
        AgentPromptIds.CODING -> assetAdapter("agent_coding", "agent_coding")
        AgentPromptIds.TUTOR -> assetAdapter("agent_tutor", "agent_tutor")
        AgentPromptIds.CREATIVE -> assetAdapter("agent_creative", "agent_creative")
        else -> AgentPromptAdapter { emptyList() }
    }

    private fun assetAdapter(assetKey: String, sectionId: String) = AgentPromptAdapter { _ ->
        val path = assets.catalog().assets[assetKey] ?: return@AgentPromptAdapter emptyList()
        val text = assets.loadAsset(path).trim()
        if (text.isEmpty()) emptyList()
        else listOf(
            PromptSection(
                id = sectionId,
                layer = PromptLayer.PERSONA,
                content = text,
                sortKey = -10,
            ),
        )
    }

    private fun inferFromPersona(persona: Persona?): String? {
        if (persona == null) return null
        val blob = listOf(persona.name, persona.description, persona.systemPrompt.take(200))
            .joinToString("\n")
            .lowercase(Locale.ROOT)
        return when {
            listOf("代码", "编程", "debug", "developer", "coder").any { blob.contains(it) } ->
                AgentPromptIds.CODING
            listOf("学习", "导师", "tutor", "讲解").any { blob.contains(it) } ->
                AgentPromptIds.TUTOR
            listOf("创意", "文案", "设计", "creative").any { blob.contains(it) } ->
                AgentPromptIds.CREATIVE
            else -> null
        }
    }
}

/**
 * 按模型名微调 Prompt（短提示层），支持多模型扩展。
 */
interface ModelPromptAdapter {
    fun matches(modelName: String?): Boolean
    fun extraSections(context: PromptBuildContext): List<PromptSection>
}

@Singleton
class ModelPromptRegistry @Inject constructor(
    private val assets: PromptAssetLoader,
) {
    private val adapters: List<ModelPromptAdapter> = listOf(
        CompactModelAdapter(assets),
    )

    fun resolve(modelName: String?): ModelPromptAdapter? =
        adapters.firstOrNull { it.matches(modelName) }
}

private class CompactModelAdapter(
    private val assets: PromptAssetLoader,
) : ModelPromptAdapter {
    override fun matches(modelName: String?): Boolean {
        val n = modelName?.lowercase(Locale.ROOT).orEmpty()
        if (n.isEmpty()) return false
        return listOf("mini", "flash", "haiku", "small", "lite").any { n.contains(it) }
    }

    override fun extraSections(context: PromptBuildContext): List<PromptSection> {
        val path = assets.catalog().assets["model_compact_hint"]
            ?: "prompts/models/compact_hint.txt"
        val text = runCatching { assets.loadAsset(path).trim() }.getOrDefault("")
        if (text.isEmpty()) return emptyList()
        return listOf(
            PromptSection(
                id = "model_compact_hint",
                layer = PromptLayer.LINGBAN_CORE,
                content = text,
                sortKey = 10,
            ),
        )
    }
}
