package com.agent.chat.data.persona

import com.agent.chat.domain.model.LorebookEntry
import com.agent.chat.domain.model.PresetMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解析 SillyTavern / TavernAI 角色卡（JSON V1/V2，或 PNG 内嵌 tEXt `chara` Base64）。
 * 映射为灵伴人设草稿，不直接落库。
 */
@Singleton
class CharacterCardImporter @Inject constructor(
    moshi: Moshi,
) {
    private val cardAdapter = moshi.adapter(SillyTavernCardRoot::class.java)

    fun import(raw: ByteArray, mimeHint: String? = null): ParsedPersonaDraft {
        val text = detectJsonPayload(raw, mimeHint)
            ?: throw IllegalArgumentException("无法识别角色卡格式，请使用 JSON 或 SillyTavern PNG 卡")
        return importFromJsonText(text)
    }

    fun importFromJsonText(json: String): ParsedPersonaDraft {
        val trimmed = json.trim()
        require(trimmed.isNotEmpty()) { "角色卡内容为空" }

        val root = runCatching { cardAdapter.fromJson(trimmed) }.getOrNull()
            ?: throw IllegalArgumentException("角色卡 JSON 解析失败")

        val data = root.data ?: root
        val name = listOfNotNull(data.name, root.name)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?: throw IllegalArgumentException("角色卡缺少 name")

        val systemPrompt = buildSystemPrompt(data, name)
        require(systemPrompt.isNotBlank()) { "角色卡缺少可用设定（description / personality）" }

        val opening = listOfNotNull(data.firstMes, data.first_mes, root.firstMes, root.first_mes)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            .orEmpty()

        val description = listOfNotNull(data.creatorNotes, data.creator_notes, data.personality)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?.take(120)
            .orEmpty()
            .ifBlank { "从 SillyTavern 角色卡导入" }

        val presets = parseMesExample(
            listOfNotNull(data.mesExample, data.mes_example, root.mesExample, root.mes_example)
                .firstOrNull()
                .orEmpty(),
        )

        val lore = parseCharacterBook(data.characterBook ?: data.character_book)

        return ParsedPersonaDraft(
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            openingLine = opening,
            presetMessages = presets,
            lorebookEntries = lore,
        )
    }

    private fun buildSystemPrompt(data: SillyTavernCardRoot, name: String): String {
        val parts = mutableListOf<String>()
        parts.add("你是 $name。")
        data.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts.add("【角色描述】\n$it")
        }
        data.personality?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts.add("【性格】\n$it")
        }
        data.scenario?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts.add("【场景】\n$it")
        }
        val system = listOfNotNull(data.systemPrompt, data.system_prompt)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
        if (!system.isNullOrEmpty()) {
            parts.add("【额外系统提示】\n$system")
        }
        val post = listOfNotNull(data.postHistoryInstructions, data.post_history_instructions)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
        if (!post.isNullOrEmpty()) {
            parts.add("【历史后指令】\n$post")
        }
        return parts.joinToString("\n\n").trim()
    }

    private fun parseMesExample(raw: String): List<PresetMessage> {
        if (raw.isBlank()) return emptyList()
        val normalized = raw.replace("\r\n", "\n")
        val blocks = normalized.split(Regex("<START>", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val source = if (blocks.size > 1) blocks else listOf(normalized)

        val result = mutableListOf<PresetMessage>()
        val lineRegex = Regex("""^\s*\{\{(user|char)\}\}[:：]\s*(.*)$""", RegexOption.IGNORE_CASE)
        val altRegex = Regex(
            """^\s*(User|Char|Assistant|Human)[:：]\s*(.*)$""",
            RegexOption.IGNORE_CASE,
        )

        for (block in source) {
            for (line in block.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val m = lineRegex.find(trimmed) ?: altRegex.find(trimmed) ?: continue
                val roleToken = m.groupValues[1].lowercase()
                val content = m.groupValues[2].trim()
                if (content.isEmpty()) continue
                val role = when {
                    roleToken.contains("char") || roleToken.contains("assistant") ->
                        PresetMessage.ROLE_ASSISTANT
                    else -> PresetMessage.ROLE_USER
                }
                result.add(PresetMessage(role = role, content = content))
                if (result.size >= 16) return result
            }
        }
        return result
    }

    private fun parseCharacterBook(book: CharacterBook?): List<LorebookEntry> {
        if (book == null) return emptyList()
        return book.entries.orEmpty().mapNotNull { entry ->
            val content = entry.content?.trim().orEmpty()
            if (content.isEmpty()) return@mapNotNull null
            val keys = buildList {
                entry.keys?.forEach { add(it) }
                entry.key?.let { add(it) }
                entry.secondaryKeys?.forEach { add(it) }
            }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (keys.isEmpty()) return@mapNotNull null
            LorebookEntry(
                id = "lore_${UUID.randomUUID().toString().take(8)}",
                keys = keys,
                content = content,
                enabled = entry.enabled != false,
                caseSensitive = entry.caseSensitive == true,
            )
        }.take(40)
    }

    private fun detectJsonPayload(raw: ByteArray, mimeHint: String?): String? {
        val asText = runCatching { String(raw, Charsets.UTF_8) }.getOrNull()?.trim().orEmpty()
        if (asText.startsWith("{") || asText.startsWith("[")) {
            return asText
        }
        val looksPng = raw.size > 8 &&
            raw[0] == 0x89.toByte() &&
            raw[1] == 0x50.toByte() &&
            raw[2] == 0x4E.toByte() &&
            raw[3] == 0x47.toByte()
        if (looksPng || mimeHint?.contains("png", ignoreCase = true) == true) {
            return extractCharaFromPng(raw)
        }
        return null
    }

    /** 读取 PNG tEXt / iTXt 中 keyword=chara 的 Base64 JSON */
    private fun extractCharaFromPng(bytes: ByteArray): String? {
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val length = readInt(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            if (dataEnd + 4 > bytes.size) break
            if ((type == "tEXt" || type == "iTXt") && length > 0) {
                val chunk = bytes.copyOfRange(dataStart, dataEnd)
                val decoded = decodePngTextChunk(chunk, type == "iTXt")
                if (decoded != null) return decoded
            }
            if (type == "IEND") break
            offset = dataEnd + 4
        }
        return null
    }

    private fun decodePngTextChunk(chunk: ByteArray, isItxt: Boolean): String? {
        val nullIndex = chunk.indexOf(0)
        if (nullIndex <= 0) return null
        val keyword = String(chunk, 0, nullIndex, Charsets.ISO_8859_1)
        if (!keyword.equals("chara", ignoreCase = true) &&
            !keyword.equals("ccv3", ignoreCase = true)
        ) {
            return null
        }
        val payload: ByteArray = if (isItxt) {
            // iTXt: keyword\0 compressionFlag\0 compressionMethod\0 language\0 translated\0 text
            var i = nullIndex + 1
            if (i >= chunk.size) return null
            val compressed = chunk[i]
            i += 2 // flag + method
            // skip language tag
            while (i < chunk.size && chunk[i] != 0.toByte()) i++
            i++
            // skip translated keyword
            while (i < chunk.size && chunk[i] != 0.toByte()) i++
            i++
            if (compressed.toInt() != 0) return null // 不处理压缩 iTXt
            if (i >= chunk.size) return null
            chunk.copyOfRange(i, chunk.size)
        } else {
            chunk.copyOfRange(nullIndex + 1, chunk.size)
        }
        val b64 = String(payload, Charsets.ISO_8859_1).trim()
        val jsonBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        return String(jsonBytes, Charsets.UTF_8)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
    }
}

@JsonClass(generateAdapter = false)
data class SillyTavernCardRoot(
    val spec: String? = null,
    @Json(name = "spec_version") val specVersion: String? = null,
    val data: SillyTavernCardRoot? = null,
    val name: String? = null,
    val description: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    @Json(name = "first_mes") val first_mes: String? = null,
    val firstMes: String? = null,
    @Json(name = "mes_example") val mes_example: String? = null,
    val mesExample: String? = null,
    @Json(name = "system_prompt") val system_prompt: String? = null,
    val systemPrompt: String? = null,
    @Json(name = "post_history_instructions") val post_history_instructions: String? = null,
    val postHistoryInstructions: String? = null,
    @Json(name = "creator_notes") val creator_notes: String? = null,
    val creatorNotes: String? = null,
    @Json(name = "character_book") val character_book: CharacterBook? = null,
    val characterBook: CharacterBook? = null,
)

@JsonClass(generateAdapter = false)
data class CharacterBook(
    val entries: List<CharacterBookEntry>? = null,
)

@JsonClass(generateAdapter = false)
data class CharacterBookEntry(
    val keys: List<String>? = null,
    val key: String? = null,
    @Json(name = "secondary_keys") val secondaryKeys: List<String>? = null,
    val content: String? = null,
    val enabled: Boolean? = true,
    @Json(name = "case_sensitive") val caseSensitive: Boolean? = false,
)
