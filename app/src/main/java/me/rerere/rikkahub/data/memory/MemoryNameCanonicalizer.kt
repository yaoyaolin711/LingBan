package me.rerere.rikkahub.data.memory

/**
 * Canonicalize stable "name / addressing" profile values for deduplication.
 *
 * Strategy:
 * 1) Strict template extraction (high precision)
 * 2) Quote fallback extraction (medium recall)
 * 3) Normalization + max 16 chars for canonicalValue comparison
 *
 * If extraction is unreliable, return null so we fall back to the old behavior.
 */
object MemoryNameCanonicalizer {
    private const val MAX_CANONICAL_LEN = 16

    fun canonicalizeNameOrAddressing(content: String, topicKey: String): String? {
        val text = content.trim()
        if (text.isEmpty()) return null

        val raw = strictExtract(text, topicKey) ?: quoteFallbackExtract(text)
        if (raw == null) return null

        val normalized = normalize(raw)
        if (normalized.isEmpty()) return null
        if (normalized.length > MAX_CANONICAL_LEN) return null
        return normalized
    }

    private fun strictExtract(text: String, topicKey: String): String? {
        val lower = text.lowercase()

        return when (topicKey) {
            MemoryTopicKeys.PROFILE_NAME -> {
                // 我叫X / 我的名字是X / 名字是X
                Regex("""(?:我叫|我的名字(?:是)?|名字(?:是)?)\s*([^\s，。！？、,.;:!?'"“”「」『』]{1,32})""")
                    .find(text)?.groupValues?.getOrNull(1)
                    ?: Regex("""(?:my name is|i am called)\s*["“”]?([a-z0-9\s]{1,32})["”]?""", RegexOption.IGNORE_CASE)
                        .find(lower)?.groupValues?.getOrNull(1)
            }

            MemoryTopicKeys.PREFERENCE_ADDRESSING -> {
                // 叫我X / 称呼我X / call me X / address me X / 昵称是X
                Regex("""(?:叫我|称呼我|昵称是)\s*([^\s，。！？、,.;:!?'"“”「」『』]{1,32})""")
                    .find(text)?.groupValues?.getOrNull(1)
                    ?: Regex(
                        """(?:call me|address me|preferred name)\s*["“”]?([a-z0-9\s]{1,32})["”]?""",
                        RegexOption.IGNORE_CASE,
                    ).find(lower)?.groupValues?.getOrNull(1)
            }

            MemoryTopicKeys.PREFERENCE_LIKE -> {
                // 我喜欢X / 我偏好X / i like X / prefers X / preference: X
                Regex(
                    """(?:我喜欢|我偏好|我更喜欢|我钟爱|我爱|喜欢|偏好)\s*["“”']?([^\s，。！？、,.;:!?'"“”「」『』]{1,32})["“”']?""",
                ).find(text)?.groupValues?.getOrNull(1)
                    ?: Regex(
                        """(?:i like|i'd like|prefer(?:s)?|prefers|preference:)\s*["“”']?([a-z0-9\s]{1,32})["“”']?""",
                        RegexOption.IGNORE_CASE,
                    ).find(lower)?.groupValues?.getOrNull(1)
            }

            MemoryTopicKeys.PREFERENCE_DISLIKE -> {
                // 我不喜欢X / 我讨厌X / don't like X / do not like X / hate X / 厌恶X
                Regex(
                    """(?:我不喜欢|我讨厌|我厌恶|不喜欢|讨厌|厌恶)\s*["“”']?([^\s，。！？、,.;:!?'"“”「」『』]{1,32})["“”']?""",
                ).find(text)?.groupValues?.getOrNull(1)
                    ?: Regex(
                        """(?:don't like|do not like|hate|can't stand)\s*["“”']?([a-z0-9\s]{1,32})["“”']?""",
                        RegexOption.IGNORE_CASE,
                    ).find(lower)?.groupValues?.getOrNull(1)
            }

            else -> null
        }
    }

    private fun quoteFallbackExtract(text: String): String? {
        // 优先中文引号，其次英文/单引号。只要能抽到长度合理的称呼就返回。
        val candidates = listOf(
            // 「阿雨」
            Regex("""「([^」]{1,32})」"""),
            // 『阿雨』
            Regex("""『([^』]{1,32})』"""),
            // “阿雨”
            Regex("""“([^”]{1,32})”"""),
            // "阿雨"
            Regex("""\"([^"]{1,32})\""""),
            // '阿雨'
            Regex("""'([^']{1,32})'"""),
        )

        for (r in candidates) {
            val m = r.find(text) ?: continue
            val v = m.groupValues.getOrNull(1) ?: continue
            val normalized = normalize(v)
            if (normalized.isNotEmpty() && normalized.length <= MAX_CANONICAL_LEN) {
                return normalized
            }
        }
        return null
    }

    private fun normalize(raw: String): String {
        // 去空格 + 清掉首尾引号等符号（便于 canonical 相等判定）
        return raw
            .trim()
            .replace(Regex("""\s+"""), "")
            .trim('\'', '"', '“', '”', '「', '」', '『', '』')
            .lowercase()
    }
}

