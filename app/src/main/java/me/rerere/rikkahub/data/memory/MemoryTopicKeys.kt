package me.rerere.rikkahub.data.memory

/**
 * Closed L2 profile topic whitelist + conservative content→topic inference.
 * Unmatched content stays as episode (no forced promotion).
 */
object MemoryTopicKeys {
    const val PROFILE_NAME = "profile.name"
    const val PROFILE_BIRTHDAY = "profile.birthday"
    const val PROFILE_LOCALE = "profile.locale"
    const val PREFERENCE_ADDRESSING = "preference.addressing"
    const val PREFERENCE_REPLY_STYLE = "preference.reply_style"
    const val PREFERENCE_LIKE = "preference.like"
    const val PREFERENCE_DISLIKE = "preference.dislike"

    val ALL = setOf(
        PROFILE_NAME,
        PROFILE_BIRTHDAY,
        PROFILE_LOCALE,
        PREFERENCE_ADDRESSING,
        PREFERENCE_REPLY_STYLE,
        PREFERENCE_LIKE,
        PREFERENCE_DISLIKE,
    )

    /**
     * Returns a whitelist topicKey when content clearly matches a profile field; otherwise null.
     */
    fun inferTopicKey(content: String): String? {
        val text = content.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()

        return when {
            matchesAny(
                lower,
                "生日", "birthday", "出生日期", "出生年月", "born on", "date of birth"
            ) -> PROFILE_BIRTHDAY

            matchesAny(
                lower,
                "叫我", "称呼我", "preferred name", "call me", "address me", "昵称是", "名字是",
                "my name is", "i am called", "我叫"
            ) && !matchesAny(lower, "不喜欢叫", "don't call") -> {
                when {
                    matchesAny(lower, "叫我", "称呼", "address", "call me", "preferred name", "昵称") ->
                        PREFERENCE_ADDRESSING
                    else -> PROFILE_NAME
                }
            }

            matchesAny(
                lower,
                "用中文", "用英文", "简体", "繁体", "locale", "language preference",
                "prefer chinese", "prefer english", "回复语言"
            ) -> PROFILE_LOCALE

            matchesAny(
                lower,
                "简短回复", "简洁回复", "详细回复", "reply style", "brief replies",
                "short answers", "concise", "prefer brief", "回答要短", "回答要详细"
            ) -> PREFERENCE_REPLY_STYLE

            matchesAny(
                lower,
                "不喜欢", "讨厌", "don't like", "do not like", "hate ", "厌恶"
            ) -> PREFERENCE_DISLIKE

            matchesAny(
                lower,
                "喜欢", "偏好", "i like", "prefers ", "preference:"
            ) -> PREFERENCE_LIKE

            else -> null
        }
    }

    private fun matchesAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }
}
