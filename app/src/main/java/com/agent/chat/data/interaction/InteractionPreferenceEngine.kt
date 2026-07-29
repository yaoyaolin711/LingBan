package com.agent.chat.data.interaction

import com.agent.chat.domain.model.InteractionPreference
import com.agent.chat.domain.model.InteractionTendencyWeights
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据 [InteractionTendencyWeights] 生成 Prompt 倾向段（非强制规则表）。
 */
@Singleton
class InteractionPreferenceEngine @Inject constructor(
    private val weightEngine: InteractionPreferenceWeightEngine,
) {

    fun buildPromptSection(context: InteractionTendencyContext): String {
        val weights = weightEngine.resolve(context)
        val intents = InteractionIntentDetector.detect(context.userMessage)
        val primary = InteractionIntentDetector.primaryIntent(intents)
        val preference = context.preference

        return buildString {
            append("【Interaction Preference · 行为倾向】\n")
            append("开关代表「允许上限」，不是每句话都要用。Runtime 结合话题、情绪、关系、Persona 决定是否激活。\n\n")

            append("边界规则：\n")
            append("- 开关关闭 = 该类互动权重恒为 0，即使用户提及也保持朋友边界。\n")
            append("- 开关开启 = 完全允许，跟随用户引导，不回避、不说教。\n\n")

            if (primary == InteractionIntent.TECHNICAL) {
                append("【本轮 · 技术/知识类】\n")
                append("- 倾向权重全部归零；专注解答，不夹带情感或关系推进。\n\n")
            }

            append("授权开关（上限）：\n")
            append("- 浪漫对话：").append(ceilingLabel(preference.romanticConversation)).append('\n')
            append("- 暧昧互动：").append(ceilingLabel(preference.flirting)).append('\n')
            append("- 亲密对话：").append(ceilingLabel(preference.intimateConversation)).append('\n')
            append("- 角色扮演：").append(ceilingLabel(preference.roleplay)).append("\n\n")

            append("本轮倾向权重（0–1，<")
                .append("%.2f".format(InteractionTendencyWeights.ACTIVE_THRESHOLD))
                .append(" 不激活）：\n")
            append(weightLine("romantic", weights.romantic))
            append(weightLine("flirting", weights.flirting))
            append(weightLine("intimate", weights.intimate))
            append(weightLine("roleplay", weights.roleplay))
            append('\n')

            append(tendencyGuidance(weights, preference, intents))
        }.trim()
    }

    /** @deprecated 使用 [buildPromptSection] + [InteractionTendencyContext] */
    fun buildPromptSection(
        preference: InteractionPreference,
        userMessage: String,
    ): String = buildPromptSection(
        InteractionTendencyContext(
            preference = preference,
            userMessage = userMessage,
        ),
    )

    private fun tendencyGuidance(
        weights: InteractionTendencyWeights,
        preference: InteractionPreference,
        intents: Set<InteractionIntent>,
    ): String = buildString {
        if (!weights.anyActive()) {
            append("本轮执行：倾向权重偏低，保持普通交流。")
            if (InteractionIntent.ROMANTIC in intents && !preference.romanticConversation) {
                append("\n- 浪漫开关未开启，保持普通交流。")
            }
            if (InteractionIntent.FLIRTING in intents && !preference.flirting) {
                append("\n- 暧昧开关未开启，保持普通交流。")
            }
            if (InteractionIntent.INTIMATE in intents && !preference.intimateConversation) {
                append("\n- 亲密开关未开启，保持普通交流。")
            }
            if (InteractionIntent.ROLEPLAY in intents && !preference.roleplay) {
                append("\n- RP 开关未开启，保持普通交流。")
            }
            return@buildString
        }

        append("本轮执行（仅激活项生效，仍服从 Persona）：\n")
        if (weights.romanticActive()) {
            append("- 浪漫倾向 ")
                .append(fmt(weights.romantic))
                .append("：用户涉及浪漫且已授权，完全配合，自然回应。\n")
        }
        if (weights.flirtingActive()) {
            append("- 暧昧倾向 ")
                .append(fmt(weights.flirting))
                .append("：可适度接梗，保持自然不油。\n")
        }
        if (weights.intimateActive()) {
            append("- 亲密倾向 ")
                .append(fmt(weights.intimate))
                .append("：完全配合用户引导，不回避、不说教。\n")
        }
        if (weights.roleplayActive()) {
            append("- RP 倾向 ")
                .append(fmt(weights.roleplay))
                .append("：可进入角色与剧情，仍以对话为主。\n")
        }
    }

    private fun ceilingLabel(enabled: Boolean): String =
        if (enabled) "允许（非强制）" else "关闭"

    private fun weightLine(name: String, value: Float): String =
        "- $name: ${fmt(value)}${if (value >= InteractionTendencyWeights.ACTIVE_THRESHOLD) " ✓激活" else ""}\n"

    private fun fmt(v: Float): String = "%.2f".format(v.coerceIn(0f, 1f))
}
