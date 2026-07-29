package com.agent.chat.data.mode

import com.agent.chat.data.ai.prompt.PromptAssetLoader
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.Persona
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LingBan 聊天模式中枢：统一影响 Persona 参数、Prompt、Response Controller。
 */
@Singleton
class ModeManager @Inject constructor(
    private val chatSettingsStore: ChatSettingsStore,
    private val assets: PromptAssetLoader,
) {

    fun currentMode(): LingBanChatMode = chatSettingsStore.get().chatMode

    fun setMode(mode: LingBanChatMode) {
        chatSettingsStore.setChatMode(mode)
    }

    fun promptPolicy(mode: LingBanChatMode = currentMode()): ModePromptPolicy =
        ModePolicies.prompt(mode)

    fun responsePolicy(mode: LingBanChatMode = currentMode()): ModeResponsePolicy =
        ModePolicies.response(mode)

    fun personaEffect(
        persona: Persona?,
        mode: LingBanChatMode = currentMode(),
    ): ModePersonaEffect = ModePolicies.applyPersona(mode, persona)

    /** 加载模式专属 Prompt 片段（来自 assets）。 */
    fun loadModeSection(mode: LingBanChatMode = currentMode()): String {
        val key = promptPolicy(mode).modeSectionAssetKey
        val path = assets.catalog().assets[key] ?: defaultModePath(mode)
        return runCatching { assets.loadAsset(path).trim() }.getOrDefault("")
    }

    fun loadHumanRules(mode: LingBanChatMode = currentMode()): String {
        val key = promptPolicy(mode).humanRulesAssetKey
        val path = assets.catalog().assets[key]
            ?: assets.humanConversationPath(mode == LingBanChatMode.ROLEPLAY)
        return runCatching { assets.loadAsset(path).trim() }.getOrDefault("")
    }

    fun memoryTokenBudget(
        baseTokens: Int,
        mode: LingBanChatMode = currentMode(),
    ): Int {
        val scale = promptPolicy(mode).memoryTokenScale
        return (baseTokens * scale).toInt().coerceAtLeast(120)
    }

    private fun defaultModePath(mode: LingBanChatMode): String = when (mode) {
        LingBanChatMode.ASSISTANT -> "prompts/modes/assistant.txt"
        LingBanChatMode.COMPANION -> "prompts/modes/companion.txt"
        LingBanChatMode.ROLEPLAY -> "prompts/modes/roleplay.txt"
    }
}
