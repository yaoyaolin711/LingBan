package com.agent.chat.data.provider

import com.agent.chat.domain.model.ProviderType

/**
 * 内置 Provider 预设列表。
 * 用户只需填写 API Key，其余字段已预填。
 * 所有 OpenAI 兼容端点共用 [OpenAICompatibleProvider]，无需额外代码。
 */
data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val iconKey: String = "",          // 对应 assets/icons/ 中的图标文件名（无扩展名）
    val requiresApiKey: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsToolCalling: Boolean = true,
    val modelSuggestions: List<String> = emptyList(),
)

object ProviderDefaults {

    // 保留旧常量，兼容现有代码
    const val DEFAULT_BASE_URL = "https://api.openai.com/v1/"
    const val DEFAULT_MODEL_NAME = "gpt-4o-mini"

    val PRESETS: List<ProviderPreset> = listOf(

        // ── OpenAI ──────────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1/",
            defaultModel = "gpt-4o-mini",
            iconKey = "openai",
            supportsVision = true,
            modelSuggestions = listOf(
                "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo",
            ),
        ),

        // ── Anthropic (Claude) ───────────────────────────────────────────────
        ProviderPreset(
            id = "preset_anthropic",
            name = "Claude (Anthropic)",
            baseUrl = "https://api.anthropic.com/v1/",
            defaultModel = "claude-3-5-sonnet-20241022",
            providerType = ProviderType.ANTHROPIC,
            iconKey = "anthropic",
            supportsVision = true,
            modelSuggestions = listOf(
                "claude-3-5-sonnet-20241022",
                "claude-3-5-haiku-20241022",
                "claude-3-opus-20240229",
                "claude-3-sonnet-20240229",
                "claude-3-haiku-20240307",
            ),
        ),

        // ── Google Gemini ────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_gemini",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/",
            defaultModel = "gemini-1.5-flash",
            providerType = ProviderType.GOOGLE_GEMINI,
            iconKey = "gemini",
            supportsVision = true,
            modelSuggestions = listOf(
                "gemini-2.0-flash-exp",
                "gemini-1.5-pro",
                "gemini-1.5-flash",
                "gemini-1.5-flash-8b",
            ),
        ),

        // ── DeepSeek ─────────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            defaultModel = "deepseek-v4-flash",
            iconKey = "deepseek",
            modelSuggestions = listOf(
                "deepseek-v4-flash",
                "deepseek-v4-pro",
                "deepseek-chat",
                "deepseek-reasoner",
            ),
        ),

        // ── 阿里通义千问 ──────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_qwen",
            name = "通义千问 (阿里云)",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
            defaultModel = "qwen-turbo",
            iconKey = "qwen",
            modelSuggestions = listOf(
                "qwen-turbo", "qwen-plus", "qwen-max",
                "qwen-long", "qwen2.5-72b-instruct",
            ),
        ),

        // ── Kimi (Moonshot) ──────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_kimi",
            name = "Kimi (月之暗面)",
            baseUrl = "https://api.moonshot.cn/v1/",
            defaultModel = "moonshot-v1-8k",
            iconKey = "kimi",
            modelSuggestions = listOf(
                "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k",
            ),
        ),

        // ── 豆包 (字节跳动) ─────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_doubao",
            name = "豆包 (字节跳动)",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3/",
            defaultModel = "ep-20250101-xxxxx",
            iconKey = "doubao",
            modelSuggestions = listOf("doubao-pro-4k", "doubao-pro-32k", "doubao-lite-4k"),
        ),

        // ── Groq ─────────────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/",
            defaultModel = "llama-3.3-70b-versatile",
            iconKey = "groq",
            modelSuggestions = listOf(
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "mixtral-8x7b-32768",
                "gemma2-9b-it",
            ),
        ),

        // ── xAI (Grok) ───────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_xai",
            name = "xAI (Grok)",
            baseUrl = "https://api.x.ai/v1/",
            defaultModel = "grok-beta",
            iconKey = "xai",
            supportsVision = true,
            modelSuggestions = listOf("grok-beta", "grok-vision-beta"),
        ),

        // ── SiliconFlow ──────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_siliconflow",
            name = "SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1/",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            iconKey = "siliconflow",
            modelSuggestions = listOf(
                "Qwen/Qwen2.5-7B-Instruct",
                "Qwen/Qwen2.5-72B-Instruct",
                "deepseek-ai/DeepSeek-V3",
                "meta-llama/Meta-Llama-3.1-70B-Instruct",
            ),
        ),

        // ── 智谱 GLM ─────────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_zhipu",
            name = "智谱 AI (GLM)",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
            defaultModel = "glm-4-flash",
            iconKey = "zhipu",
            modelSuggestions = listOf(
                "glm-4-flash", "glm-4-plus", "glm-4", "glm-4v",
            ),
        ),

        // ── Ollama（本地）────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_ollama",
            name = "Ollama (本地)",
            baseUrl = "http://localhost:11434/v1/",
            defaultModel = "llama3.2",
            iconKey = "ollama",
            requiresApiKey = false,
            modelSuggestions = listOf(
                "llama3.2", "llama3.1", "qwen2.5", "deepseek-r1:7b",
                "mistral", "phi4", "gemma2",
            ),
        ),

        // ── together.ai ──────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_together",
            name = "Together AI",
            baseUrl = "https://api.together.xyz/v1/",
            defaultModel = "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo",
            iconKey = "meta",
            modelSuggestions = listOf(
                "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo",
                "mistralai/Mixtral-8x7B-Instruct-v0.1",
            ),
        ),

        // ── OpenRouter ───────────────────────────────────────────────────────
        ProviderPreset(
            id = "preset_openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/",
            defaultModel = "openai/gpt-4o-mini",
            iconKey = "openrouter",
            supportsVision = true,
            modelSuggestions = listOf(
                "openai/gpt-4o-mini",
                "anthropic/claude-3.5-sonnet",
                "google/gemini-flash-1.5",
                "deepseek/deepseek-chat",
            ),
        ),
    )

    /** 按 id 快速查找预设 */
    fun findById(id: String): ProviderPreset? = PRESETS.firstOrNull { it.id == id }

    /** 根据 baseUrl 猜测最匹配的预设（用于导入已有配置时显示图标） */
    fun guessPreset(baseUrl: String): ProviderPreset? {
        val url = baseUrl.lowercase()
        return PRESETS.firstOrNull { preset ->
            url.contains(preset.baseUrl.removePrefix("https://").removePrefix("http://").take(20).lowercase())
        }
    }
}
