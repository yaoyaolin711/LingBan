package com.agent.chat.data.ai.response

import com.agent.chat.data.expression.ExpressionPolicies
import com.agent.chat.data.expression.ExpressionStylePolicy
import com.agent.chat.data.mode.ModePolicies
import com.agent.chat.data.mode.ModeResponsePolicy
import com.agent.chat.domain.model.BehaviorPlan
import com.agent.chat.domain.model.ConversationStateSnapshot
import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.RelationshipProfile

/**
 * 回复质量评分（0–1）。
 *
 * - [naturalnessScore] / [styleFitScore] / [lengthScore]：越高越好
 * - [dramaticScore] / [poeticScore] / [innerMonologue] / [humorScore]：检测到的表达强度；与用户设定比对
 */
data class ResponseScores(
    val naturalnessScore: Float,
    val dramaticScore: Float,
    val poeticScore: Float,
    val innerMonologue: Float,
    val humorScore: Float,
    val lengthScore: Float,
    /** 综合风格贴合度 */
    val styleFitScore: Float,
) {
    /** 兼容旧 debug 字段名 */
    val humanScore: Float get() = naturalnessScore
    val emotionScore: Float get() = styleFitScore

    fun summaryLine(): String =
        "natural=${fmt(naturalnessScore)} dramatic=${fmt(dramaticScore)} " +
            "poetic=${fmt(poeticScore)} mono=${fmt(innerMonologue)} " +
            "humor=${fmt(humorScore)} len=${fmt(lengthScore)} fit=${fmt(styleFitScore)}"

    private fun fmt(v: Float): String = "%.2f".format(v.coerceIn(0f, 1f))
}

data class ResponseEvalContext(
    val userMessage: String = "",
    val chatMode: LingBanChatMode = LingBanChatMode.COMPANION,
    val policy: ModeResponsePolicy = ModePolicies.response(LingBanChatMode.COMPANION),
    val relationshipProfile: RelationshipProfile = RelationshipProfile(),
    val expressionProfile: ExpressionProfile = ExpressionProfile(),
    val expressionPolicy: ExpressionStylePolicy = ExpressionPolicies.fromProfile(ExpressionProfile()),
    val conversationState: ConversationStateSnapshot = ConversationStateSnapshot.DEFAULT,
    val behaviorPlan: BehaviorPlan = BehaviorPlan.DEFAULT,
) {
    val rolePlayEnabled: Boolean get() = chatMode == LingBanChatMode.ROLEPLAY
}

data class ResponseEvalResult(
    val passed: Boolean,
    val scores: ResponseScores,
    val reasons: List<String> = emptyList(),
)

data class ResponseControlResult(
    val text: String,
    val evaluation: ResponseEvalResult,
    /** 实际生成次数（含首次） */
    val attempts: Int,
    val regenerated: Boolean,
)

/**
 * 回复评估器接口：默认启发式实现，未来可替换为小模型。
 */
interface ResponseEvaluator {
    suspend fun evaluate(
        text: String,
        context: ResponseEvalContext,
    ): ResponseEvalResult
}
