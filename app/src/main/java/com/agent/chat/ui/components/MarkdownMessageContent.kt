package com.agent.chat.ui.components

import com.agent.chat.ui.theme.AgentThemeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val FenceRegex = Regex("```([\\w+#.-]*)\\n([\\s\\S]*?)```")
private val ImageRegex = Regex("!\\[([^]]*)]\\(([^)]+)\\)")
// Enhanced inline pattern: bold, italic, strikethrough, inline-code, link
private val InlinePattern = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|~~[^~]+~~|`[^`]+`|\\[[^]]+]\\([^)]+\\))")

@Stable
private sealed class MarkdownBlock {
    data class Text(val value: String) : MarkdownBlock()
    data class Heading(val level: Int, val value: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class Image(val alt: String, val url: String) : MarkdownBlock()
    data class Quote(val value: String) : MarkdownBlock()
    data class ListItem(val bullet: String, val value: String, val ordered: Boolean = false) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

@Stable
private class MarkdownCache {
    var source: String = ""
    var blocks: List<MarkdownBlock> = emptyList()
    var stablePrefix: Int = 0

    fun update(markdown: String): List<MarkdownBlock> {
        if (markdown == source) return blocks
        if (markdown.startsWith(source) && canAppendFast(markdown)) {
            val appended = markdown.substring(source.length)
            source = markdown
            return appendIncremental(appended)
        }
        source = markdown
        blocks = parseMarkdownBlocks(markdown)
        stablePrefix = (blocks.size - 1).coerceAtLeast(0)
        return blocks
    }

    private fun canAppendFast(newSource: String): Boolean {
        val lastFence = newSource.lastIndexOf("```")
        val sourceFence = source.lastIndexOf("```")
        return lastFence == sourceFence || lastFence < source.length
    }

    private fun appendIncremental(appended: String): List<MarkdownBlock> {
        if (blocks.isEmpty()) {
            blocks = parseMarkdownBlocks(source)
            return blocks
        }
        val last = blocks.last()
        val rebuilt = when (last) {
            is MarkdownBlock.Text -> {
                val newText = last.value + appended
                val parsed = expandTextSegment(newText)
                if (parsed.size == 1 && parsed[0] is MarkdownBlock.Text) {
                    blocks.toMutableList().apply { set(lastIndex, parsed[0]) }
                } else {
                    parseMarkdownBlocks(source)
                }
            }
            else -> parseMarkdownBlocks(source)
        }
        blocks = rebuilt
        stablePrefix = (rebuilt.size - 1).coerceAtLeast(0)
        return blocks
    }
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
    val cache = remember { MarkdownCache() }
    val blocks = remember(markdown) { cache.update(markdown) }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex
            when (block) {
                is MarkdownBlock.Text -> {
                    if (block.value.isNotBlank()) {
                        val cursor = if (showStreamingCursor && isLastBlock) "▍" else ""
                        val rendered = remember(block.value, cursor, textColor) {
                            renderInlineMarkdown(
                                text = block.value.trimEnd() + cursor,
                                textColor = textColor,
                            )
                        }
                        SelectionContainer {
                            Text(
                                text = rendered,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                            )
                        }
                    }
                }
                is MarkdownBlock.Heading -> {
                    val cursor = if (showStreamingCursor && isLastBlock) "▍" else ""
                    val style: TextStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    }
                    SelectionContainer {
                        Text(
                            text = block.value.trimEnd() + cursor,
                            style = style,
                            color = textColor,
                            modifier = Modifier.padding(vertical = if (block.level == 1) 8.dp else 4.dp),
                        )
                    }
                    if (block.level <= 2) {
                        Divider(
                            color = textColor.copy(alpha = 0.12f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (block.ordered) {
                            Text(
                                text = block.bullet,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor.copy(alpha = 0.6f),
                                modifier = Modifier.width(20.dp),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(textColor.copy(alpha = 0.5f)),
                            )
                        }
                        val rendered = remember(block.value, textColor) {
                            renderInlineMarkdown(block.value, textColor)
                        }
                        SelectionContainer {
                            Text(
                                text = rendered,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                            )
                        }
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    Divider(
                        color = textColor.copy(alpha = 0.2f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.accent.copy(alpha = 0.5f)),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    colors.surfaceMuted,
                                    RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            val rendered = remember(block.value, textColor) {
                                renderInlineMarkdown(block.value, textColor)
                            }
                            SelectionContainer {
                                Text(
                                    text = rendered,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showStreamingCursor && blocks.none {
                it is MarkdownBlock.Text && it.value.isNotBlank()
            }
        ) {
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
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val highlighted = remember(code, language) {
        highlightCode(code, language, codeColor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (language.isNotBlank()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelLarge,
                    color = codeColor.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = if (copied) "已复制" else "复制代码",
                tint = if (copied) Color(0xFF4CAF50) else codeColor.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(code))
                        copied = true
                    },
            )
        }
        if (language.isNotBlank()) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 6.dp))
        }
        SelectionContainer {
            Text(
                text = highlighted,
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

private val OrderedListRegex = Regex("^(\\d+)\\.\\s+(.*)")

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
        val trimmed = line.trimStart()
        when {
            // Horizontal rule
            trimmed.matches(Regex("[-*_]{3,}\\s*")) -> {
                flushText(); flushQuote()
                result += MarkdownBlock.HorizontalRule
            }
            // Headings
            trimmed.startsWith("#") -> {
                flushText(); flushQuote()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val heading = trimmed.drop(level).trimStart()
                result += MarkdownBlock.Heading(level, heading)
            }
            // Blockquote
            trimmed.startsWith(">") -> {
                flushText()
                val body = trimmed.removePrefix(">").trimStart()
                if (quoteBuf.isNotEmpty()) quoteBuf.append('\n')
                quoteBuf.append(body)
            }
            // Unordered list
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                flushText(); flushQuote()
                val value = trimmed.substring(2)
                result += MarkdownBlock.ListItem(bullet = "•", value = value, ordered = false)
            }
            // Ordered list
            OrderedListRegex.matches(trimmed) -> {
                val match = OrderedListRegex.find(trimmed)!!
                flushText(); flushQuote()
                result += MarkdownBlock.ListItem(
                    bullet = "${match.groupValues[1]}.",
                    value = match.groupValues[2],
                    ordered = true,
                )
            }
            else -> {
                flushQuote()
                if (textBuf.isNotEmpty() || line.isNotEmpty()) {
                    if (textBuf.isNotEmpty()) textBuf.append('\n')
                    textBuf.append(line)
                }
            }
        }
        if (index == lines.lastIndex) {
            flushQuote()
            flushText()
        }
    }
    return result
}

private val LinkInlineRegex = Regex("\\[([^]]+)]\\(([^)]+)\\)")

private fun renderInlineMarkdown(text: String, textColor: Color) = buildAnnotatedString {
    var index = 0
    InlinePattern.findAll(text).forEach { match ->
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
            token.startsWith("*") && token.endsWith("*") && token.length > 2 -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                    append(token.removeSurrounding("*"))
                }
            }
            token.startsWith("~~") && token.endsWith("~~") -> {
                withStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        color = textColor.copy(alpha = 0.7f),
                    ),
                ) {
                    append(token.removeSurrounding("~~"))
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
            LinkInlineRegex.matches(token) -> {
                val m = LinkInlineRegex.find(token)!!
                withStyle(
                    SpanStyle(
                        color = Color(0xFF4A88F5),
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append(m.groupValues[1])
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

private val KeywordSets: Map<String, Set<String>> = mapOf(
    "kt" to setOf(
        "fun", "val", "var", "class", "object", "if", "else", "when", "return",
        "suspend", "import", "package", "data", "sealed", "interface", "true", "false", "null",
        "override", "private", "internal", "protected", "open", "abstract", "companion",
        "inline", "reified", "crossinline", "noinline", "typealias", "enum", "annotation",
        "try", "catch", "finally", "throw", "for", "while", "do", "break", "continue",
        "in", "is", "as", "by", "constructor", "init", "this", "super",
    ),
    "java" to setOf(
        "public", "private", "protected", "class", "interface", "return", "if", "else", "new",
        "void", "static", "final", "true", "false", "null", "abstract", "extends", "implements",
        "try", "catch", "finally", "throw", "throws", "for", "while", "do", "break", "continue",
        "switch", "case", "default", "synchronized", "volatile", "transient", "instanceof",
        "import", "package", "this", "super", "enum",
    ),
    "js" to setOf(
        "function", "const", "let", "var", "return", "if", "else", "class",
        "import", "export", "async", "await", "true", "false", "null", "undefined",
        "new", "this", "super", "try", "catch", "finally", "throw", "for", "while",
        "do", "break", "continue", "switch", "case", "default", "typeof", "instanceof",
        "yield", "of", "from", "extends", "static", "get", "set", "delete",
    ),
    "py" to setOf(
        "def", "class", "return", "if", "elif", "else", "import", "from",
        "True", "False", "None", "async", "await", "try", "except", "finally",
        "raise", "for", "while", "break", "continue", "pass", "yield", "with",
        "as", "in", "is", "not", "and", "or", "lambda", "global", "nonlocal",
        "del", "assert", "self", "cls",
    ),
    "go" to setOf(
        "func", "var", "const", "type", "struct", "interface", "return", "if", "else",
        "for", "range", "switch", "case", "default", "break", "continue", "go", "select",
        "chan", "defer", "fallthrough", "goto", "package", "import", "map", "make", "new",
        "true", "false", "nil", "append", "len", "cap",
    ),
    "rust" to setOf(
        "fn", "let", "mut", "const", "struct", "enum", "impl", "trait", "pub", "use",
        "mod", "crate", "self", "super", "return", "if", "else", "match", "for", "while",
        "loop", "break", "continue", "move", "ref", "async", "await", "where", "type",
        "true", "false", "Some", "None", "Ok", "Err", "unsafe", "extern", "dyn",
    ),
    "sql" to setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "CREATE", "DROP", "ALTER", "TABLE", "INDEX", "JOIN", "LEFT", "RIGHT", "INNER",
        "OUTER", "ON", "AND", "OR", "NOT", "IN", "IS", "NULL", "AS", "ORDER", "BY",
        "GROUP", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "EXISTS",
        "BETWEEN", "LIKE", "CASE", "WHEN", "THEN", "ELSE", "END", "PRIMARY", "KEY",
        "FOREIGN", "REFERENCES", "DEFAULT", "CHECK", "CONSTRAINT", "UNIQUE",
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "create", "drop", "alter", "table", "index", "join", "left", "right", "inner",
        "outer", "on", "and", "or", "not", "in", "is", "null", "as", "order", "by",
        "group", "having", "limit", "offset", "union", "all", "distinct", "exists",
    ),
    "sh" to setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
        "function", "return", "exit", "echo", "export", "source", "local", "readonly",
        "set", "unset", "shift", "true", "false", "in",
    ),
    "swift" to setOf(
        "func", "var", "let", "class", "struct", "enum", "protocol", "extension",
        "return", "if", "else", "guard", "switch", "case", "default", "for", "while",
        "repeat", "break", "continue", "import", "self", "Self", "super", "init",
        "true", "false", "nil", "throws", "throw", "try", "catch", "async", "await",
        "public", "private", "internal", "open", "fileprivate", "static", "override",
    ),
    "c" to setOf(
        "int", "char", "float", "double", "void", "long", "short", "unsigned", "signed",
        "struct", "union", "enum", "typedef", "return", "if", "else", "for", "while",
        "do", "break", "continue", "switch", "case", "default", "sizeof", "static",
        "extern", "const", "volatile", "register", "auto", "goto", "NULL", "include",
        "define", "ifdef", "ifndef", "endif", "true", "false",
    ),
)

private fun resolveKeywords(language: String): Set<String> {
    val lang = language.lowercase()
    return KeywordSets[lang]
        ?: KeywordSets.entries.firstOrNull { (k, _) ->
            when (k) {
                "kt" -> lang in setOf("kotlin")
                "js" -> lang in setOf("javascript", "ts", "typescript", "jsx", "tsx")
                "py" -> lang in setOf("python")
                "sh" -> lang in setOf("bash", "zsh", "shell")
                "c" -> lang in setOf("cpp", "c++", "h", "hpp")
                else -> false
            }
        }?.value
        ?: KeywordSets["kt"]!!
}

private fun highlightCode(code: String, language: String, baseColor: Color) = buildAnnotatedString {
    val keywordColor = Color(0xFF4A5FD9)
    val stringColor = Color(0xFF2B7A78)
    val commentColor = Color(0xFF6B6B72)
    val numberColor = Color(0xFF5B6BC8)
    val keywords = resolveKeywords(language)

    val lines = code.split('\n')
    lines.forEachIndexed { lineIndex, line ->
        val commentIdx = when {
            line.trimStart().startsWith("//") -> line.indexOf("//")
            line.trimStart().startsWith("#") &&
                language.lowercase() in setOf("py", "python", "sh", "bash", "zsh", "shell", "yaml", "yml") ->
                line.indexOf("#")
            line.trimStart().startsWith("--") &&
                language.lowercase() in setOf("sql", "lua", "haskell") ->
                line.indexOf("--")
            else -> -1
        }
        val codePart = if (commentIdx >= 0) line.substring(0, commentIdx) else line
        val commentPart = if (commentIdx >= 0) line.substring(commentIdx) else ""

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
                    while (end < codePart.length && (codePart[end].isDigit() || codePart[end] == '.' || codePart[end] == 'f' || codePart[end] == 'L')) end++
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
