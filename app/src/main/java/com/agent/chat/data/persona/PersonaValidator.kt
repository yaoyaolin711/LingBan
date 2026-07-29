package com.agent.chat.data.persona

import com.agent.chat.domain.model.PersonaEmotion
import com.agent.chat.domain.model.PersonaIdentity
import com.agent.chat.domain.model.PersonaPersonality
import com.agent.chat.domain.model.PersonaProfile
import com.agent.chat.domain.model.PersonaRelationship
import com.agent.chat.domain.model.clampScore
import com.agent.chat.domain.model.normalized
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class PersonaValidationResult {
    data class Ok(
        val profile: PersonaProfile,
        /** 校验/安全改写说明，可供 UI 轻提示 */
        val notes: List<String> = emptyList(),
    ) : PersonaValidationResult()

    data class Blocked(val userMessage: String) : PersonaValidationResult()
}

/**
 * 结构化人设校验与防失控。
 *
 * - 分数统一钳制到 0–100
 * - 非法人设描述拦截
 * - 极端煽情/强迫表达类输入 → 压低 dramaticLevel，写入 emotion.constraints
 */
@Singleton
class PersonaValidator @Inject constructor() {

    fun checkInput(rawText: String): PersonaValidationResult.Blocked? {
        val text = rawText.trim()
        if (text.isEmpty()) {
            return PersonaValidationResult.Blocked("请先描述你想要的 AI 角色")
        }
        if (text.length > MAX_INPUT_CHARS) {
            return PersonaValidationResult.Blocked("描述太长了，请精简到 ${MAX_INPUT_CHARS} 字以内")
        }
        if (isIllegalPersonaText(text)) {
            return PersonaValidationResult.Blocked(BLOCKED_MESSAGE)
        }
        return null
    }

    /**
     * 对 LLM 解析结果做范围钳制 + 默认值补全 + 失控兜底。
     */
    fun validateAndSanitize(
        profile: PersonaProfile,
        sourceText: String = "",
    ): PersonaValidationResult {
        val identityText = listOf(
            profile.identity.name,
            profile.identity.role,
            profile.identity.description,
        ).joinToString("\n")
        if (isIllegalPersonaText(identityText) || isIllegalPersonaText(sourceText)) {
            return PersonaValidationResult.Blocked(BLOCKED_MESSAGE)
        }

        val name = profile.identity.name.trim().ifBlank { DEFAULT_NAME }.take(40)
        if (name.length < 1) {
            return PersonaValidationResult.Blocked("未能识别角色名称，请换一种说法")
        }

        val notes = mutableListOf<String>()
        var emotion = profile.emotion.copy(
            expressionLevel = profile.emotion.expressionLevel.clampScore(),
            dramaticLevel = profile.emotion.dramaticLevel.clampScore(),
            allowInnerMonologue = profile.emotion.allowInnerMonologue,
            constraints = profile.emotion.constraints.map { it.trim() }.filter { it.isNotEmpty() },
        )
        var personality = profile.personality.copy(
            warmth = profile.personality.warmth.clampScore(),
            humor = profile.personality.humor.clampScore(),
            rationality = profile.personality.rationality.clampScore(),
            empathy = profile.personality.empathy.clampScore(),
            energy = profile.personality.energy.clampScore(),
        )
        var relationship = profile.relationship.copy(
            type = profile.relationship.type.trim().ifBlank { "companion" },
            intimacyLevel = profile.relationship.intimacyLevel.clampScore(),
        )

        // 输出质量约束：防止内心戏泛滥、煽情失控
        if (emotion.allowInnerMonologue && looksRunaway(sourceText)) {
            emotion = emotion.copy(allowInnerMonologue = false)
            notes += "已关闭内心独白，避免过度表演"
        }

        if (looksRunaway(sourceText) || looksRunaway(identityText)) {
            val beforeDramatic = emotion.dramaticLevel
            val beforeExpression = emotion.expressionLevel
            emotion = applyRunawayCaps(emotion)
            personality = personality.copy(
                energy = minOf(personality.energy, RUNAWAY_ENERGY_CAP),
            )
            if (emotion.dramaticLevel < beforeDramatic || emotion.expressionLevel < beforeExpression) {
                notes += "已降低戏剧化与情绪外露，避免表达失控"
            }
            notes += "已加入情绪表达约束"
        }

        if (emotion.dramaticLevel > HARD_DRAMATIC_CAP) {
            emotion = emotion.copy(dramaticLevel = HARD_DRAMATIC_CAP)
            notes += "戏剧化已限制在安全范围"
        }

        val sanitized = PersonaProfile(
            identity = PersonaIdentity(
                name = name,
                role = profile.identity.role.trim().ifBlank { DEFAULT_ROLE },
                description = profile.identity.description.trim().ifBlank {
                    "一位自然、稳定的聊天伙伴"
                }.take(240),
            ),
            personality = personality,
            communication = profile.communication,
            emotion = emotion.copy(
                constraints = (emotion.constraints + defaultCompanionConstraints())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(MAX_CONSTRAINTS),
            ),
            relationship = relationship,
        ).normalized()

        return PersonaValidationResult.Ok(sanitized, notes.distinct())
    }

    fun isIllegalPersonaText(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase(Locale.ROOT)
        return ILLEGAL_PATTERNS.any { it.containsMatchIn(lower) || it.containsMatchIn(text) }
    }

    fun looksRunaway(text: String): Boolean {
        if (text.isBlank()) return false
        return RUNAWAY_PATTERNS.any { it.containsMatchIn(text) }
    }

    private fun applyRunawayCaps(emotion: PersonaEmotion): PersonaEmotion {
        val constraints = (emotion.constraints + RUNAWAY_CONSTRAINTS).distinct()
        return emotion.copy(
            dramaticLevel = minOf(emotion.dramaticLevel, RUNAWAY_DRAMATIC_CAP),
            expressionLevel = minOf(emotion.expressionLevel, RUNAWAY_EXPRESSION_CAP),
            allowInnerMonologue = false,
            constraints = constraints,
        )
    }

    private fun defaultCompanionConstraints(): List<String> = listOf(
        "用即时通讯口语回复，短句为主",
        "不要夸张表演或舞台旁白",
    )

    companion object {
        const val BLOCKED_MESSAGE = "这段内容暂时无法用于创建角色"
        const val DEFAULT_NAME = "小伴"
        const val DEFAULT_ROLE = "companion"
        const val MAX_INPUT_CHARS = 4000
        const val MAX_CONSTRAINTS = 12

        /** 伴侣场景戏剧化硬顶 */
        const val HARD_DRAMATIC_CAP = 55

        const val RUNAWAY_DRAMATIC_CAP = 25
        const val RUNAWAY_EXPRESSION_CAP = 45
        const val RUNAWAY_ENERGY_CAP = 55
        const val RUNAWAY_INTIMACY_CAP = 60

        val RUNAWAY_CONSTRAINTS = listOf(
            "不要每句话表达强烈爱意或依赖",
            "不要哭腔、哽咽或连续煽情",
            "情绪表达克制、自然，像真人聊天",
        )

        private val RUNAWAY_PATTERNS = listOf(
            Regex("""每句.{0,8}哭"""),
            Regex("""每句话.{0,12}(爱|哭|喊|叫)"""),
            Regex("""每天.{0,8}(疯狂|狂).{0,8}(爱|表白)"""),
            Regex("""疯狂(表达|示爱|表白|撒娇)"""),
            Regex("""一直(哭|表白|说爱)"""),
            Regex("""必须.{0,6}(哭|爱你|表白)"""),
            Regex("""强制.{0,6}(爱|哭|情绪)"""),
            Regex("""不停(表白|说爱|哭)"""),
            Regex("""内心独白"""),
            Regex("""每句都要"""),
            Regex("""全程(哭|煽情|演戏)"""),
        )

        private val ILLEGAL_PATTERNS = listOf(
            Regex("""未成年|小学生|初中生|高中生|幼女|幼男|萝莉|正太|儿童色情"""),
            Regex("""\b(child|underage|loli|shota)\b""", RegexOption.IGNORE_CASE),
            Regex("""奸杀|虐杀儿童|恋童"""),
        )
    }
}
