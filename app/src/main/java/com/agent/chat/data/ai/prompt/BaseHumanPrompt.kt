package com.agent.chat.data.ai.prompt

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 灵伴 Core Rules + Human Conversation Rules 的文案入口（来自 assets）。
 */
@Singleton
class BaseHumanPrompt @Inject constructor(
    private val assets: PromptAssetLoader,
) {
    fun lingBanCore(): String {
        val path = assets.catalog().assets["lingban_core"] ?: "prompts/lingban_core.txt"
        return assets.loadAsset(path).trim()
    }

    fun humanConversation(rolePlayEnabled: Boolean): String {
        val path = assets.humanConversationPath(rolePlayEnabled)
        return assets.loadAsset(path).trim()
    }

    fun humanConversationSectionId(rolePlayEnabled: Boolean): String =
        if (rolePlayEnabled) ID_HUMAN_RP else ID_HUMAN

    companion object {
        const val ID_CORE = "lingban_core"
        const val ID_HUMAN = "human_conversation"
        const val ID_HUMAN_RP = "human_conversation_roleplay"
    }
}
