package com.agent.chat.data.ai

import com.agent.chat.domain.model.OutputRegex
import com.agent.chat.domain.model.Persona

data class RegexRewriteResult(
    /** 用于落库的文本 */
    val persisted: String,
    /** 用于 UI 展示的文本 */
    val displayed: String,
)

object OutputRegexApplier {

    fun apply(raw: String, persona: Persona?): RegexRewriteResult {
        val rules = persona?.outputRegexes.orEmpty().filter { it.enabled && it.pattern.isNotBlank() }
        if (rules.isEmpty()) {
            return RegexRewriteResult(persisted = raw, displayed = raw)
        }

        var persisted = raw
        var displayed = raw
        for (rule in rules) {
            val regex = runCatching { Regex(rule.pattern) }.getOrNull() ?: continue
            displayed = displayed.replace(regex, rule.replacement)
            if (!rule.visualOnly) {
                persisted = persisted.replace(regex, rule.replacement)
            }
        }
        return RegexRewriteResult(persisted = persisted, displayed = displayed)
    }

    fun applyDisplayOnly(raw: String, rules: List<OutputRegex>): String {
        var text = raw
        for (rule in rules.filter { it.enabled && it.pattern.isNotBlank() }) {
            val regex = runCatching { Regex(rule.pattern) }.getOrNull() ?: continue
            text = text.replace(regex, rule.replacement)
        }
        return text
    }
}
