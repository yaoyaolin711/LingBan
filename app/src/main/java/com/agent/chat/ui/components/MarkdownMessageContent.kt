package com.agent.chat.ui.components

import com.agent.chat.ui.theme.AgentThemeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val FenceRegex = Regex("```([\\w+#.-]*)\\n([\\s\\S]*?)```")
private val ImageRegex = Regex("!\\[([^]]*)]\\(([^)]+)\\)")

private sealed class MarkdownBlock {
    data class Text(val value: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class Image(val alt: String, val url: String) : MarkdownBlock()
    data class Quote(val value: String) : MarkdownBlock()
}

@Composable
fun MarkdownMessageContent(
    markdown: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    isUser: Boolean = false,
    showStreamingCursor: Boolean = false,
) {
    val colors = AgentThemeColors

    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Text -> {
                    if (block.value.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = renderInlineMarkdown(
                                    text = block.value.trimEnd() + if (showStreamingCursor) "▍" else "",
                                    textColor = textColor,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                            )
                        }
                    }
                }
                is MarkdownBlock.Code -> {
                    CodeBlock(
                        language = block.language,
                        code = block.code.trimEnd(),
                        isUser = isUser,
                    )
                }
                is MarkdownBlock.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = block.alt.ifBlank { "图片" },
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                is MarkdownBlock.Quote -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceMuted)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "引用",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = block.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        if (showStreamingCursor && blocks.none { it is MarkdownBlock.Text && it.value.isNotBlank() }) {
            Text(
                text = "▍",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    isUser: Boolean,
) {
    val background = if (isUser) {
        Color(0x14000000)
    } else {
        Color(0xFFF4F5F7)
    }
    val codeColor = Color(0xFF1A1A1E)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(12.dp),
    ) {
        if (language.isNotBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelLarge,
                color = codeColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        SelectionContainer {
            Text(
                text = highlightCode(code, language, codeColor),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isEmpty()) return emptyList()
    val blocks = mutableListOf<MarkdownBlock>()
    var lastIndex = 0
    FenceRegex.findAll(markdown).forEach { match ->
        if (match.range.first > lastIndex) {
            blocks += expandTextSegment(markdown.substring(lastIndex, match.range.first))
        }
        blocks += MarkdownBlock.Code(
            language = match.groupValues[1].trim(),
            code = match.groupValues[2],
        )
        lastIndex = match.range.last + 1
    }
    if (lastIndex < markdown.length) {
        blocks += expandTextSegment(markdown.substring(lastIndex))
    }
    return blocks
}

private fun expandTextSegment(segment: String): List<MarkdownBlock> {
    if (segment.isBlank()) return emptyList()
    val result = mutableListOf<MarkdownBlock>()
    var last = 0
    ImageRegex.findAll(segment).forEach { match ->
        if (match.range.first > last) {
            result += splitQuotesAndText(segment.substring(last, match.range.first))
        }
        result += MarkdownBlock.Image(
            alt = match.groupValues[1],
            url = match.groupValues[2].trim(),
        )
        last = match.range.last + 1
    }
    if (last < segment.length) {
        result += splitQuotesAndText(segment.substring(last))
    }
    return result
}

private fun splitQuotesAndText(segment: String): List<MarkdownBlock> {
    if (segment.isBlank()) return emptyList()
    val lines = segment.split('\n')
    val result = mutableListOf<MarkdownBlock>()
    val textBuf = StringBuilder()
    val quoteBuf = StringBuilder()

    fun flushText() {
        if (textBuf.isNotEmpty()) {
            result += MarkdownBlock.Text(textBuf.toString())
            textBuf.clear()
        }
    }

    fun flushQuote() {
        if (quoteBuf.isNotEmpty()) {
            result += MarkdownBlock.Quote(quoteBuf.toString().trim())
            quoteBuf.clear()
        }
    }

    lines.forEachIndexed { index, raw ->
        val line = raw
        val isQuote = line.trimStart().startsWith(">")
        if (isQuote) {
            flushText()
            val body = line.trimStart().removePrefix(">").trimStart()
            if (quoteBuf.isNotEmpty()) quoteBuf.append('\n')
            quoteBuf.append(body)
        } else {
            flushQuote()
            if (textBuf.isNotEmpty() || line.isNotEmpty()) {
                if (textBuf.isNotEmpty()) textBuf.append('\n')
                textBuf.append(line)
            }
        }
        if (index == lines.lastIndex) {
            flushQuote()
            flushText()
        }
    }
    return result
}

private fun renderInlineMarkdown(text: String, textColor: Color) = buildAnnotatedString {
    var index = 0
    val pattern = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
    pattern.findAll(text).forEach { match ->
        if (match.range.first > index) {
            append(text.substring(index, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = textColor)) {
                    append(token.removeSurrounding("**"))
                }
            }
            token.startsWith("`") && token.endsWith("`") -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = textColor.copy(alpha = 0.12f),
                        color = textColor,
                    ),
                ) {
                    append(token.removeSurrounding("`"))
                }
            }
            else -> append(token)
        }
        index = match.range.last + 1
    }
    if (index < text.length) {
        append(text.substring(index))
    }
}

private fun highlightCode(code: String, language: String, baseColor: Color) = buildAnnotatedString {
    val keywordColor = Color(0xFF4A5FD9)
    val stringColor = Color(0xFF2B7A78)
    val commentColor = Color(0xFF6B6B72)
    val numberColor = Color(0xFF5B6BC8)

    val keywords = when (language.lowercase()) {
        "kt", "kotlin" -> listOf(
            "fun", "val", "var", "class", "object", "if", "else", "when", "return",
            "suspend", "import", "package", "data", "sealed", "interface", "true", "false", "null",
        )
        "java" -> listOf(
            "public", "private", "class", "interface", "return", "if", "else", "new",
            "void", "static", "final", "true", "false", "null",
        )
        "js", "javascript", "ts", "typescript" -> listOf(
            "function", "const", "let", "var", "return", "if", "else", "class",
            "import", "export", "async", "await", "true", "false", "null",
        )
        "py", "python" -> listOf(
            "def", "class", "return", "if", "elif", "else", "import", "from",
            "True", "False", "None", "async", "await",
        )
        else -> listOf(
            "fun", "val", "var", "class", "return", "if", "else", "true", "false", "null",
            "def", "function", "const", "let", "import",
        )
    }

    val lines = code.split('\n')
    lines.forEachIndexed { lineIndex, line ->
        var remaining = line
        val commentIdx = when {
            remaining.trimStart().startsWith("//") -> remaining.indexOf("//")
            remaining.trimStart().startsWith("#") &&
                language.lowercase() in setOf("py", "python", "sh", "bash") ->
                remaining.indexOf("#")
            else -> -1
        }
        val codePart = if (commentIdx >= 0) remaining.substring(0, commentIdx) else remaining
        val commentPart = if (commentIdx >= 0) remaining.substring(commentIdx) else ""

        var i = 0
        while (i < codePart.length) {
            when {
                codePart[i] == '"' || codePart[i] == '\'' -> {
                    val quote = codePart[i]
                    val end = codePart.indexOf(quote, i + 1).let { if (it < 0) codePart.length else it + 1 }
                    withStyle(SpanStyle(color = stringColor)) {
                        append(codePart.substring(i, end))
                    }
                    i = end
                }
                codePart[i].isDigit() -> {
                    var end = i + 1
                    while (end < codePart.length && (codePart[end].isDigit() || codePart[end] == '.')) end++
                    withStyle(SpanStyle(color = numberColor)) {
                        append(codePart.substring(i, end))
                    }
                    i = end
                }
                codePart[i].isLetter() || codePart[i] == '_' -> {
                    var end = i + 1
                    while (end < codePart.length && (codePart[end].isLetterOrDigit() || codePart[end] == '_')) end++
                    val word = codePart.substring(i, end)
                    if (word in keywords) {
                        withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)) {
                            append(word)
                        }
                    } else {
                        withStyle(SpanStyle(color = baseColor)) { append(word) }
                    }
                    i = end
                }
                else -> {
                    withStyle(SpanStyle(color = baseColor)) { append(codePart[i]) }
                    i++
                }
            }
        }
        if (commentPart.isNotEmpty()) {
            withStyle(SpanStyle(color = commentColor)) { append(commentPart) }
        }
        if (lineIndex != lines.lastIndex) append('\n')
    }
}
