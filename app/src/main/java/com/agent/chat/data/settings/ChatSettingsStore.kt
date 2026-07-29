package com.agent.chat.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 聊天展示与伴侣感相关偏好。
 */
@Singleton
class ChatSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<ChatSettings> = _snapshot.asStateFlow()

    fun get(): ChatSettings = _snapshot.value

    fun setNaturalChatPaceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NATURAL_CHAT_PACE, enabled).apply()
        _snapshot.value = read()
    }

    fun setCompanionStyleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPANION_STYLE, enabled).apply()
        _snapshot.value = read()
    }

    fun setSplitBubbleByNewline(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPLIT_BY_NEWLINE, enabled).apply()
        _snapshot.value = read()
    }

    fun setUserNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname.trim()).apply()
        _snapshot.value = read()
    }

    fun setUserInterest(interest: String) {
        prefs.edit().putString(KEY_INTEREST, interest.trim()).apply()
        _snapshot.value = read()
    }

    fun setUserOccupation(occupation: String) {
        prefs.edit().putString(KEY_OCCUPATION, occupation.trim()).apply()
        _snapshot.value = read()
    }

    fun setUserGoal(goal: String) {
        prefs.edit().putString(KEY_GOAL, goal.trim()).apply()
        _snapshot.value = read()
    }

    fun setThemeMode(mode: String) {
        val normalized = when (mode.lowercase()) {
            "dark" -> "dark"
            else -> "light"
        }
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
        _snapshot.value = read()
    }

    fun setHasExplored(explored: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_EXPLORED, explored).apply()
        _snapshot.value = read()
    }

    fun setProactiveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROACTIVE, enabled).apply()
        _snapshot.value = read()
    }

    fun setProactiveIdleHours(hours: Int) {
        prefs.edit().putInt(KEY_PROACTIVE_IDLE_HOURS, hours.coerceIn(1, 72)).apply()
        _snapshot.value = read()
    }

    fun touchLastUserActivity() {
        prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
        _snapshot.value = read()
    }

    fun markProactiveNudge(kind: String) {
        prefs.edit()
            .putLong(KEY_LAST_NUDGE_AT, System.currentTimeMillis())
            .putString(KEY_LAST_NUDGE_KIND, kind)
            .apply()
        _snapshot.value = read()
    }

    private fun read(): ChatSettings = ChatSettings(
        naturalChatPaceEnabled = prefs.getBoolean(KEY_NATURAL_CHAT_PACE, true),
        companionStyleEnabled = prefs.getBoolean(KEY_COMPANION_STYLE, true),
        splitBubbleByNewline = prefs.getBoolean(KEY_SPLIT_BY_NEWLINE, true),
        userNickname = prefs.getString(KEY_NICKNAME, "")?.orEmpty().orEmpty(),
        userInterest = prefs.getString(KEY_INTEREST, "")?.orEmpty().orEmpty(),
        userOccupation = prefs.getString(KEY_OCCUPATION, "")?.orEmpty().orEmpty(),
        userGoal = prefs.getString(KEY_GOAL, "")?.orEmpty().orEmpty(),
        themeMode = prefs.getString(KEY_THEME_MODE, "light")?.takeIf { it.isNotBlank() } ?: "light",
        hasExplored = prefs.getBoolean(KEY_HAS_EXPLORED, false),
        proactiveEnabled = prefs.getBoolean(KEY_PROACTIVE, false),
        proactiveIdleHours = prefs.getInt(KEY_PROACTIVE_IDLE_HOURS, 6).coerceIn(1, 72),
        lastUserActivityAt = prefs.getLong(KEY_LAST_ACTIVITY, 0L),
        lastProactiveNudgeAt = prefs.getLong(KEY_LAST_NUDGE_AT, 0L),
        lastProactiveNudgeKind = prefs.getString(KEY_LAST_NUDGE_KIND, "").orEmpty(),
    )

    companion object {
        private const val PREFS_NAME = "chat_settings"
        private const val KEY_NATURAL_CHAT_PACE = "natural_chat_pace"
        private const val KEY_COMPANION_STYLE = "companion_style"
        private const val KEY_SPLIT_BY_NEWLINE = "split_by_newline"
        private const val KEY_NICKNAME = "user_nickname"
        private const val KEY_INTEREST = "user_interest"
        private const val KEY_OCCUPATION = "user_occupation"
        private const val KEY_GOAL = "user_goal"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HAS_EXPLORED = "has_explored"
        private const val KEY_PROACTIVE = "proactive_enabled"
        private const val KEY_PROACTIVE_IDLE_HOURS = "proactive_idle_hours"
        private const val KEY_LAST_ACTIVITY = "last_user_activity"
        private const val KEY_LAST_NUDGE_AT = "last_proactive_nudge_at"
        private const val KEY_LAST_NUDGE_KIND = "last_proactive_nudge_kind"
    }
}

data class ChatSettings(
    /** 流式完整后拆分并模拟输入节奏；关闭则一次性展示 */
    val naturalChatPaceEnabled: Boolean = true,
    /** 注入口语化伴侣风格层 */
    val companionStyleEnabled: Boolean = true,
    /** 优先按模型换行拆气泡 */
    val splitBubbleByNewline: Boolean = true,
    val userNickname: String = "",
    val userInterest: String = "",
    val userOccupation: String = "",
    val userGoal: String = "",
    /** light / dark */
    val themeMode: String = "light",
    /** 是否完成首次「开始探索」 */
    val hasExplored: Boolean = false,
    val proactiveEnabled: Boolean = false,
    val proactiveIdleHours: Int = 6,
    val lastUserActivityAt: Long = 0L,
    val lastProactiveNudgeAt: Long = 0L,
    val lastProactiveNudgeKind: String = "",
) {
    val isDarkTheme: Boolean get() = themeMode.equals("dark", ignoreCase = true)
}
