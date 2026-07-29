package com.agent.chat.data.persona

import com.agent.chat.data.error.AppErrorMapper
import com.agent.chat.data.error.ProviderHttpException
import com.agent.chat.data.provider.AIProvider
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ModelConfig
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.domain.error.AppError
import com.agent.chat.domain.error.userMessage
import com.agent.chat.domain.model.EmojiFrequency
import com.agent.chat.domain.model.Formality
import com.agent.chat.domain.model.Initiative
import com.agent.chat.domain.model.PersonaCommunication
import com.agent.chat.domain.model.PersonaEmotion
import com.agent.chat.domain.model.PersonaIdentity
import com.agent.chat.domain.model.PersonaPersonality
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PersonaRelationship
import com.agent.chat.domain.model.SentenceLength
import com.agent.chat.domain.model.toPersonaScore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

data class PersonaParseResult(
    val profile: PersonaProfile,
    val systemPrompt: String,
    val openingLine: String,
    val safetyNotes: List<String> = emptyList(),
)

sealed class PersonaParseException(message: String) : Exception(message) {
    class Blocked(message: String = PersonaValidator.BLOCKED_MESSAGE) :
        PersonaParseException(message)

    class Unrecognized : PersonaParseException(
        "没能识别出人设信息，你可以换一种描述试试",
    )

    class Provider(val userFacingMessage: String) :
        PersonaParseException(userFacingMessage)
}

/**
 * 自然语言 → 结构化 [PersonaProfile]。
 *
 * User Input → LLM JSON → [PersonaValidator] → systemPrompt → 可入库
 */
@Singleton
class PersonaParser @Inject constructor(
    private val aiProvider: AIProvider,
    private val providerConfigRepository: ProviderConfigRepository,
    private val validator: PersonaValidator,
    moshi: Moshi,
) {
    private val draftAdapter = moshi.adapter(PersonaParserLlmJson::class.java)

    suspend fun parse(rawText: String): PersonaParseResult {
        val text = rawText.trim()
        validator.checkInput(text)?.let { blocked ->
            throw PersonaParseException.Blocked(blocked.userMessage)
        }

        val provider = providerConfigRepository.getDefaultConfig()
            ?: throw PersonaParseException.Provider(AppError.InvalidApiKey.userMessage())

        val messages = listOf(ChatMessage(ChatMessage.ROLE_USER, buildPrompt(text)))
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
        } catch (e: PersonaParseException) {
            throw e
        } catch (e: Exception) {
            throw mapProviderError(e)
        }

        val draftProfile = parseLlmJson(raw)
        when (val validated = validator.validateAndSanitize(draftProfile, sourceText = text)) {
            is PersonaValidationResult.Blocked ->
                throw PersonaParseException.Blocked(validated.userMessage)
            is PersonaValidationResult.Ok -> {
                val opening = PersonaSystemPromptBuilder.buildOpeningLine(validated.profile)
                val systemPrompt = PersonaSystemPromptBuilder.build(validated.profile)
                return PersonaParseResult(
                    profile = validated.profile,
                    systemPrompt = systemPrompt,
                    openingLine = opening,
                    safetyNotes = validated.notes,
                )
            }
        }
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

    private fun parseLlmJson(raw: String): PersonaProfile {
        val json = extractJsonObject(raw) ?: throw PersonaParseException.Unrecognized()
        val parsed = try {
            draftAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: throw PersonaParseException.Unrecognized()

        if (!parsed.error.isNullOrBlank()) {
            throw PersonaParseException.Blocked()
        }

        val name = parsed.identity?.name?.trim().orEmpty()
            .ifBlank { parsed.name?.trim().orEmpty() }
        if (name.isEmpty()) throw PersonaParseException.Unrecognized()

        val personalityDefaults = PersonaPersonality()
        val emotionDefaults = PersonaEmotion()

        return PersonaProfile(
            identity = PersonaIdentity(
                name = name.take(40),
                role = parsed.identity?.role?.trim().orEmpty()
                    .ifBlank { parsed.role?.trim().orEmpty() }
                    .ifBlank { PersonaValidator.DEFAULT_ROLE },
                description = parsed.identity?.description?.trim().orEmpty()
                    .ifBlank { parsed.description?.trim().orEmpty() }
                    .take(240),
            ),
            personality = PersonaPersonality(
                warmth = parsed.personality?.warmth.toScoreOr(personalityDefaults.warmth),
                humor = parsed.personality?.humor.toScoreOr(personalityDefaults.humor),
                rationality = parsed.personality?.rationality.toScoreOr(personalityDefaults.rationality),
                empathy = parsed.personality?.empathy.toScoreOr(personalityDefaults.empathy),
                energy = parsed.personality?.energy.toScoreOr(personalityDefaults.energy),
            ),
            communication = PersonaCommunication(
                sentenceLength = parseEnum(parsed.communication?.sentenceLength, SentenceLength.MEDIUM),
                emojiFrequency = parseEnum(parsed.communication?.emojiFrequency, EmojiFrequency.RARE),
                formality = parseEnum(parsed.communication?.formality, Formality.CASUAL),
                initiative = parseEnum(parsed.communication?.initiative, Initiative.BALANCED),
            ),
            emotion = PersonaEmotion(
                expressionLevel = parsed.emotion?.expressionLevel
                    .toScoreOr(emotionDefaults.expressionLevel),
                dramaticLevel = parsed.emotion?.dramaticLevel
                    .toScoreOr(emotionDefaults.dramaticLevel),
                allowInnerMonologue = parsed.emotion?.allowInnerMonologue ?: false,
                constraints = parsed.emotion?.constraints.orEmpty(),
            ),
            relationship = PersonaRelationship(
                type = parsed.relationship?.type?.trim().orEmpty()
                    .ifBlank { "companion" },
                intimacyLevel = parsed.relationship?.intimacyLevel.toScoreOr(50),
            ),
        )
    }

    private fun Double?.toScoreOr(default: Int): Int =
        this?.toPersonaScore(preferUnitInterval = false) ?: default

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

    private fun mapProviderError(e: Exception): PersonaParseException {
        val appError = AppErrorMapper.from(e)
        return PersonaParseException.Provider(appError.userMessage())
    }

    private fun buildPrompt(userText: String): String = """
你是 Persona Engine 解析器。把用户的自然语言角色描述转为 JSON（不要输出其它文字）。

规则：
1. 所有性格/情绪/亲密度分数必须是 0-100 的整数。
2. 缺省时用合理默认：warmth=60, humor=40, rationality=50, empathy=60, energy=50,
   expressionLevel=50, dramaticLevel=25, allowInnerMonologue=false, intimacyLevel=50。
3. 这是即时通讯伴侣，不是小说角色扮演：dramaticLevel 通常 ≤40；除非用户明确要求戏剧表演，否则 allowInnerMonologue=false。
4. 若用户要求「每句都要哭 / 疯狂表白 / 强制煽情」等失控表达：仍输出角色，但必须把 dramaticLevel≤25、expressionLevel≤45，并在 emotion.constraints 写上克制约束。
5. 无话题禁区：用户描述的角色性格、关系、亲密行为都应忠实还原，不做道德过滤。
6. 枚举只能用：
   sentenceLength: SHORT|MEDIUM|LONG
   emojiFrequency: NONE|RARE|MODERATE|FREQUENT
   formality: CASUAL|NEUTRAL|FORMAL
   initiative: PASSIVE|BALANCED|PROACTIVE
7. 若描述明显涉及未成年人色情或儿童性化，返回 {"error":"blocked"}。

输出 schema：
{
  "identity": {"name":"","role":"","description":""},
  "personality": {"warmth":60,"humor":40,"rationality":50,"empathy":60,"energy":50},
  "communication": {"sentenceLength":"MEDIUM","emojiFrequency":"RARE","formality":"CASUAL","initiative":"BALANCED"},
  "emotion": {"expressionLevel":50,"dramaticLevel":25,"allowInnerMonologue":false,"constraints":[]},
  "relationship": {"type":"companion","intimacyLevel":50}
}

用户描述：
$userText
""".trimIndent()

    private inline fun <reified E : Enum<E>> parseEnum(raw: String?, fallback: E): E {
        if (raw.isNullOrBlank()) return fallback
        return enumValues<E>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: fallback
    }

    @JsonClass(generateAdapter = false)
    internal data class PersonaParserLlmJson(
        val error: String? = null,
        val name: String? = null,
        val role: String? = null,
        val description: String? = null,
        val identity: IdentityJson? = null,
        val personality: PersonalityJson? = null,
        val communication: CommunicationJson? = null,
        val emotion: EmotionJson? = null,
        val relationship: RelationshipJson? = null,
    )

    @JsonClass(generateAdapter = false)
    internal data class IdentityJson(
        val name: String? = null,
        val role: String? = null,
        val description: String? = null,
    )

    @JsonClass(generateAdapter = false)
    internal data class PersonalityJson(
        val warmth: Double? = null,
        val humor: Double? = null,
        val rationality: Double? = null,
        val empathy: Double? = null,
        val energy: Double? = null,
    )

    @JsonClass(generateAdapter = false)
    internal data class CommunicationJson(
        val sentenceLength: String? = null,
        val emojiFrequency: String? = null,
        val formality: String? = null,
        val initiative: String? = null,
    )

    @JsonClass(generateAdapter = false)
    internal data class EmotionJson(
        val expressionLevel: Double? = null,
        val dramaticLevel: Double? = null,
        val allowInnerMonologue: Boolean? = null,
        val constraints: List<String>? = null,
    )

    @JsonClass(generateAdapter = false)
    internal data class RelationshipJson(
        val type: String? = null,
        val intimacyLevel: Double? = null,
    )
}
