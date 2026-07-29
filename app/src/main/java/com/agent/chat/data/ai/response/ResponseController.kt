package com.agent.chat.data.ai.response

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Response Controller：评估 → 不通过则触发重新生成。
 */
@Singleton
class ResponseController @Inject constructor(
    private val evaluator: ResponseEvaluator,
) {

    /**
     * @param generate 第 1 次传 attempt=0；失败后 attempt≥1，可带上轮评估结果做修复提示。
     */
    suspend fun run(
        context: ResponseEvalContext,
        enabled: Boolean,
        maxAttempts: Int = MAX_ATTEMPTS,
        generate: suspend (attempt: Int, previous: ResponseEvalResult?) -> String,
    ): ResponseControlResult {
        if (!enabled) {
            val text = generate(0, null)
            val evaluation = ResponseEvalResult(
                passed = true,
                scores = ResponseScores(1f, 0f, 0f, 0f, 0f, 1f, 1f),
                reasons = emptyList(),
            )
            return ResponseControlResult(
                text = text,
                evaluation = evaluation,
                attempts = 1,
                regenerated = false,
            )
        }

        val attemptsCap = maxAttempts.coerceIn(1, 3)
        var lastEval: ResponseEvalResult? = null
        var text = ""
        var usedAttempts = 0

        for (attempt in 0 until attemptsCap) {
            text = generate(attempt, lastEval)
            usedAttempts = attempt + 1
            lastEval = evaluator.evaluate(text, context)
            Log.d(
                TAG,
                "attempt=$usedAttempts passed=${lastEval.passed} ${lastEval.scores.summaryLine()} " +
                    "reasons=${lastEval.reasons}",
            )
            if (lastEval.passed) break
        }

        val evaluation = lastEval ?: ResponseEvalResult(
            passed = true,
            scores = ResponseScores(1f, 0f, 0f, 0f, 0f, 1f, 1f),
        )
        return ResponseControlResult(
            text = text,
            evaluation = evaluation,
            attempts = usedAttempts,
            regenerated = usedAttempts > 1,
        )
    }

    fun buildRepairHint(previous: ResponseEvalResult, context: ResponseEvalContext? = null): String {
        val expr = context?.expressionProfile
        val tips = buildList {
            if (previous.reasons.any { it.contains("真人感") || it.contains("natural") }) {
                add("更像真人发消息：口语短句，例如「今天辛苦了，早点休息」")
            }
            if (previous.reasons.any { it.contains("戏剧") }) {
                add("降低戏剧化，不要夸张表演")
            }
            if (previous.reasons.any { it.contains("文学") || it.contains("poetic") }) {
                add("降低文学化，不要「仿佛/如同/失去颜色」式隐喻")
            }
            if (previous.reasons.any { it.contains("独白") || it.contains("旁白") }) {
                add("禁止内心独白、*动作* 与旁白")
            }
            val isStoryTurn = context?.userMessage?.contains("故事") == true ||
                context?.relationshipProfile?.relationshipType ==
                com.agent.chat.domain.model.RelationshipType.ROLEPLAY
            if (previous.reasons.any { it.contains("长度") } && !isStoryTurn) {
                add("按当前句长设定缩短，用短口语")
            }
            if (expr != null && previous.reasons.any { it.contains("幽默") }) {
                add("幽默程度对齐设定(${expr.humorLevel})")
            }
            if (isEmpty()) add("表达风格对齐用户设定，真实口语即可")
        }
        return buildString {
            append("<response_repair>上一版回复不符合表达风格设定（")
            append(previous.reasons.joinToString("；").ifBlank { previous.scores.summaryLine() })
            append("）。请重写：")
            append(tips.joinToString("；"))
            append("。只输出对用户说的话。</response_repair>")
        }
    }

    companion object {
        private const val TAG = "ResponseController"
        const val MAX_ATTEMPTS = 2
    }
}
