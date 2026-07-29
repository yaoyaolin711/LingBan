package com.agent.chat.data.ai.response

import com.agent.chat.data.expression.ExpressionPolicies
import com.agent.chat.domain.model.ExpressionProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionStyleEvaluatorTest {

    private val evaluator = HeuristicResponseEvaluator()

    private fun eval(text: String, profile: ExpressionProfile): ResponseEvalResult = runBlocking {
        evaluator.evaluate(
            text = text,
            context = ResponseEvalContext(
                expressionProfile = profile,
                expressionPolicy = ExpressionPolicies.fromProfile(profile),
            ),
        )
    }

    @Test
    fun naturalComfort_passes_defaultProfile() {
        val profile = ExpressionProfile(naturalness = 90, dramaticLevel = 20, poeticLevel = 10)
        val result = eval("今天辛苦了，早点休息。", profile)
        assertTrue(result.passed)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun poeticMetaphor_fails_lowPoeticProfile() {
        val profile = ExpressionProfile(naturalness = 90, dramaticLevel = 20, poeticLevel = 10)
        val result = eval("我的世界仿佛因为你的疲惫而失去颜色。", profile)
        assertFalse(result.passed)
        assertTrue(result.reasons.any { it.contains("文学") || it.contains("戏剧") || it.contains("真人") })
    }

    @Test
    fun mentorProfile_rejects_dramaticMonologue() {
        val profile = ExpressionProfile(naturalness = 90, dramaticLevel = 5, poeticLevel = 5)
        val result = eval("我的内心一阵温暖，此刻我静静陪伴着你。", profile)
        assertFalse(result.passed)
    }

    @Test
    fun romanticProfile_allows_warmNatural_not_poetic() {
        val profile = ExpressionProfile(naturalness = 90, dramaticLevel = 30, poeticLevel = 15)
        val ok = eval("想你了，今天别太累，早点睡。", profile)
        val bad = eval("我的世界仿佛因为你的疲惫而失去颜色。", profile)
        assertTrue(ok.passed)
        assertFalse(bad.passed)
    }
}
