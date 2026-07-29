package com.agent.chat.data.ai.response

import com.agent.chat.data.expression.ExpressionStylePolicy
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 规则启发式评估器：检查「表达是否符合用户选择的风格」，不检查「有没有情感」。
 */
@Singleton
class HeuristicResponseEvaluator @Inject constructor() : ResponseEvaluator {

    override suspend fun evaluate(
        text: String,
        context: ResponseEvalContext,
    ): ResponseEvalResult {
        val raw = text.trim()
        if (raw.isEmpty() || raw == "（无回复）") {
            return ResponseEvalResult(
                passed = false,
                scores = emptyScores(),
                reasons = listOf("空回复"),
            )
        }

        val style = context.expressionPolicy
        val dramatic = scoreDramatic(raw)
        val poetic = scorePoetic(raw)
        val monologue = scoreInnerMonologue(raw)
        val humor = scoreHumor(raw)
        val emoji = scoreEmoji(raw)
        val naturalness = scoreNaturalness(raw, dramatic, poetic, monologue)
        val length = scoreLength(raw, style)

        val styleFit = computeStyleFit(
            naturalness = naturalness,
            dramatic = dramatic,
            poetic = poetic,
            monologue = monologue,
            humor = humor,
            emoji = emoji,
            style = style,
        )

        val scores = ResponseScores(
            naturalnessScore = naturalness,
            dramaticScore = dramatic,
            poeticScore = poetic,
            innerMonologue = monologue,
            humorScore = humor,
            lengthScore = length,
            styleFitScore = styleFit,
        )

        val reasons = mutableListOf<String>()
        if (naturalness < style.naturalnessFloor) {
            reasons += "真人感不足(natural=${"%.2f".format(naturalness)}<${"%.2f".format(style.naturalnessFloor)})"
        }
        if (dramatic > style.dramaticCap) {
            reasons += "戏剧化超出设定(dramatic=${"%.2f".format(dramatic)}>${"%.2f".format(style.dramaticCap)})"
        }
        if (poetic > style.poeticCap) {
            reasons += "文学化超出设定(poetic=${"%.2f".format(poetic)}>${"%.2f".format(style.poeticCap)})"
        }
        if (monologue > style.monoCap) {
            reasons += "旁白/独白超出设定(mono=${"%.2f".format(monologue)}>${"%.2f".format(style.monoCap)})"
        }
        if (humor > style.humorCap) {
            reasons += "幽默程度超出设定(humor=${"%.2f".format(humor)}>${"%.2f".format(style.humorCap)})"
        }
        if (emoji > style.emojiCap) {
            reasons += "Emoji 超出设定"
        }
        if (length < style.lengthFloor) {
            reasons += "长度不合适(len=${"%.2f".format(length)})"
        }

        return ResponseEvalResult(
            passed = reasons.isEmpty(),
            scores = scores,
            reasons = reasons,
        )
    }

    private fun computeStyleFit(
        naturalness: Float,
        dramatic: Float,
        poetic: Float,
        monologue: Float,
        humor: Float,
        emoji: Float,
        style: ExpressionStylePolicy,
    ): Float {
        var fit = 0.78f
        if (naturalness >= style.naturalnessFloor) fit += 0.08f else fit -= 0.25f
        if (dramatic <= style.dramaticCap) fit += 0.04f else fit -= (dramatic - style.dramaticCap) * 0.6f
        if (poetic <= style.poeticCap) fit += 0.04f else fit -= (poetic - style.poeticCap) * 0.65f
        if (monologue <= style.monoCap) fit += 0.03f else fit -= (monologue - style.monoCap) * 0.5f
        if (humor <= style.humorCap) fit += 0.02f else fit -= 0.08f
        if (emoji <= style.emojiCap) fit += 0.01f else fit -= 0.06f
        return fit.coerceIn(0f, 1f)
    }

    private fun scoreDramatic(text: String): Float {
        var score = 0f
        DRAMATIC_PHRASES.forEach { if (text.contains(it)) score += 0.18f }
        val bangs = text.count { it == '！' || it == '!' }
        score += (bangs * 0.06f).coerceAtMost(0.3f)
        val ellipsis = Regex("""…{2,}|\.{3,}""").findAll(text).count()
        score += (ellipsis * 0.05f).coerceAtMost(0.2f)
        if (Regex("""[「『].{8,}[」』]""").containsMatchIn(text)) score += 0.08f
        return score.coerceIn(0f, 1f)
    }

    private fun scorePoetic(text: String): Float {
        var score = 0f
        POETIC_PHRASES.forEach { if (text.contains(it)) score += 0.2f }
        POETIC_PATTERNS.forEach { if (it.containsMatchIn(text)) score += 0.15f }
        val commas = text.count { it == '，' || it == ',' }
        if (commas >= 6 && text.length > 80) score += 0.12f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreInnerMonologue(text: String): Float {
        var score = 0f
        MONOLOGUE_PATTERNS.forEach { if (it.containsMatchIn(text)) score += 0.28f }
        val stars = Regex("""\*[^*\n]{2,40}\*""").findAll(text).count()
        score += (stars * 0.2f).coerceAtMost(0.5f)
        val parens = Regex("""[（(][^）)]{0,20}(心想|内心|暗自|默默)[^）)]*[）)]""").findAll(text).count()
        score += (parens * 0.25f).coerceAtMost(0.5f)
        return score.coerceIn(0f, 1f)
    }

    private fun scoreHumor(text: String): Float {
        var score = 0f
        if (text.contains("哈哈") || text.contains("hhh")) score += 0.2f
        if (listOf("笑死", "绝了", "离谱", "整活").any { it in text }) score += 0.15f
        if (text.any { isEmojiCodePoint(it.code) }) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreEmoji(text: String): Float {
        var emojiCount = 0
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (isEmojiCodePoint(codePoint)) emojiCount++
            i += Character.charCount(codePoint)
        }
        return when {
            emojiCount == 0 -> 0f
            emojiCount == 1 -> 0.15f
            emojiCount <= 3 -> 0.35f
            else -> (0.35f + (emojiCount - 3) * 0.12f).coerceAtMost(1f)
        }
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean =
        codePoint in 0x1F300..0x1FAFF ||
            codePoint in 0x2600..0x27BF

    private fun scoreNaturalness(
        text: String,
        dramatic: Float,
        poetic: Float,
        monologue: Float,
    ): Float {
        var score = 0.78f
        score -= dramatic * 0.45f
        score -= poetic * 0.5f
        score -= monologue * 0.4f
        if (text.contains("作为 AI") || text.contains("作为人工智能") || text.contains("语言模型")) {
            score -= 0.35f
        }
        if (text.contains("根据工具") || text.contains("JSON")) score -= 0.2f
        if (listOf("嗯", "哈哈", "啦", "呀", "吧", "哦", "诶", "辛苦", "早点休息").any { text.contains(it) }) {
            score += 0.1f
        }
        if (listOf("首先", "其次", "综上所述").any { text.contains(it) }) score -= 0.2f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreLength(text: String, policy: ExpressionStylePolicy): Float {
        val sentences = splitSentences(text)
        val chars = text.length
        var score = 1f
        when {
            sentences.isEmpty() -> score = 0.2f
            sentences.size < 1 -> score -= 0.2f
            sentences.size > policy.idealSentenceMax ->
                score -= ((sentences.size - policy.idealSentenceMax) * 0.12f).coerceAtMost(0.6f)
        }
        if (chars > policy.charIdealMax) {
            score -= ((chars - policy.charIdealMax) / 200f).coerceAtMost(0.5f)
        }
        if (chars < 6) score -= 0.3f
        if (Regex("""(?m)^\s*[-*•\d]+[、.．)]""").containsMatchIn(text)) score -= 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("""[。！？!?\n]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun emptyScores() = ResponseScores(0f, 1f, 1f, 0f, 0f, 0f, 0f)

    companion object {
        private val DRAMATIC_PHRASES = listOf(
            "我的内心一阵温暖",
            "我的心被触动",
            "此刻我静静陪伴",
            "泪流满面",
            "心都要碎了",
            "颤抖着",
            "哽咽",
            "紧紧相拥",
            "世界只剩下",
            "目光深情",
            "胸口一阵",
            "热泪盈眶",
            "灵魂深处",
        )

        private val POETIC_PHRASES = listOf(
            "仿佛",
            "如同",
            "宛如",
            "恰似",
            "失去颜色",
            "我的世界",
            "你的疲惫",
            "静静陪伴",
            "时光流逝",
            "命运",
        )

        private val POETIC_PATTERNS = listOf(
            Regex("""世界.{0,6}(失去|黯淡|灰暗)"""),
            Regex("""[因为由于].{0,12}(而|让).{0,12}(失去|碎|裂)"""),
        )

        private val MONOLOGUE_PATTERNS = listOf(
            Regex("""内心(独白|想|一阵|涌)"""),
            Regex("""心想[：:]"""),
            Regex("""暗自(想|嘀咕)"""),
            Regex("""我在心里"""),
            Regex("""【旁白】|【内心】"""),
        )
    }
}
