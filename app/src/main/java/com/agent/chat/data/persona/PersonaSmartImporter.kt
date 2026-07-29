package com.agent.chat.data.persona

import com.agent.chat.data.error.AppErrorMapper
import com.agent.chat.data.error.ProviderHttpException
import com.agent.chat.data.provider.AIProvider
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ModelConfig
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.domain.error.AppError
import com.agent.chat.domain.error.userMessage
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedPersonaDraft(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val openingLine: String,
)

sealed class PersonaSmartImportException(message: String) : Exception(message) {
    /** 安全边界拦截，不向用户暴露细节 */
    class Blocked : PersonaSmartImportException("这段内容暂时无法导入")

    /** JSON/字段无法识别 */
    class Unrecognized : PersonaSmartImportException(
        "没能识别出人设信息，你可以手动填写或换一段文本试试",
    )

    /** 网络/Provider 等，已映射为友好文案 */
    class Provider(val userFacingMessage: String) :
        PersonaSmartImportException(userFacingMessage)
}

@Singleton
class PersonaSmartImporter @Inject constructor(
    private val aiProvider: AIProvider,
    private val providerConfigRepository: ProviderConfigRepository,
    moshi: Moshi,
) {

    private val draftAdapter = moshi.adapter(AiPersonaJson::class.java)

    suspend fun parse(rawText: String): ParsedPersonaDraft {
        val text = rawText.trim()
        require(text.isNotBlank()) { "请先粘贴人设描述文本" }

        val provider = providerConfigRepository.getDefaultConfig()
            ?: throw PersonaSmartImportException.Provider(
                AppError.InvalidApiKey.userMessage(),
            )

        val prompt = buildPrompt(text)
        val messages = listOf(ChatMessage(ChatMessage.ROLE_USER, prompt))
        val baseConfig = providerConfigRepository.toModelConfig(provider, temperature = 0.2f)

        val raw = try {
            collectCompletion(messages, baseConfig.copy(jsonObjectMode = true))
        } catch (e: ProviderHttpException) {
            if (e.code in 400..499) {
                try {
                    collectCompletion(messages, baseConfig.copy(jsonObjectMode = false))
                } catch (retry: Exception) {
                    throw mapProviderError(retry)
                }
            } else {
                throw mapProviderError(e)
            }
        } catch (e: PersonaSmartImportException) {
            throw e
        } catch (e: Exception) {
            throw mapProviderError(e)
        }

        return parseJsonDraft(raw)
    }

    private suspend fun collectCompletion(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ): String {
        val builder = StringBuilder()
        aiProvider.chatStream(messages, config).collect { token ->
            builder.append(token)
        }
        return builder.toString().trim()
    }

    private fun parseJsonDraft(raw: String): ParsedPersonaDraft {
        val json = extractJsonObject(raw)
            ?: throw PersonaSmartImportException.Unrecognized()

        val parsed = try {
            draftAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: throw PersonaSmartImportException.Unrecognized()

        val error = parsed.error?.trim().orEmpty()
        if (error.isNotEmpty()) {
            throw PersonaSmartImportException.Blocked()
        }

        val name = parsed.name?.trim().orEmpty()
        val systemPrompt = parsed.systemPrompt?.trim().orEmpty()
        if (name.isEmpty() || systemPrompt.isEmpty()) {
            throw PersonaSmartImportException.Unrecognized()
        }

        return ParsedPersonaDraft(
            name = name.take(40),
            description = parsed.description?.trim().orEmpty().take(120),
            systemPrompt = systemPrompt.take(8000),
            openingLine = parsed.openingLine?.trim().orEmpty().take(200),
        )
    }

    private fun extractJsonObject(raw: String): String? {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                .substringBeforeLast("```")
                .trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun mapProviderError(e: Exception): PersonaSmartImportException {
        val appError = AppErrorMapper.from(e)
        return PersonaSmartImportException.Provider(appError.userMessage())
    }

    private fun buildPrompt(userText: String): String = """
请从以下文本中提取角色人设信息，以JSON格式输出，不要输出任何其他内容：
{
  "name": "角色名称",
  "description": "一句话简介，用于人设卡片展示，可稍长",
  "systemPrompt": "完整系统提示词：务必保留原文中的性格、说话习惯、口头禅、关系设定、禁忌与互动方式，写成可直接喂给模型的人设指令。允许较长（建议详尽），不要压成干巴的说明书摘要。控制在8000字以内。",
  "openingLine": "一句符合该角色语气的开场白"
}

原文如下：
$userText

仅输出JSON，不要有多余文字。如果原文描述的角色明显带有未成年人特征（如学生、幼态外观描述、低龄化称呼等），不要提取该内容，直接返回{"error": "该内容无法识别为可用人设"}
""".trimIndent()

    @JsonClass(generateAdapter = false)
    internal data class AiPersonaJson(
        val name: String? = null,
        val description: String? = null,
        val systemPrompt: String? = null,
        val openingLine: String? = null,
        val error: String? = null,
    )
}
