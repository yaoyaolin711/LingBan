package com.agent.chat.data.interaction

import android.content.Context
import com.agent.chat.domain.model.InteractionPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户级互动偏好持久化（SharedPreferences）。
 *
 * 单设备单用户；与 Persona / 会话无关。
 */
@Singleton
class InteractionPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<InteractionPreference> = _snapshot.asStateFlow()

    fun get(): InteractionPreference = _snapshot.value

    fun save(preference: InteractionPreference) {
        prefs.edit()
            .putBoolean(KEY_ROMANTIC, preference.romanticConversation)
            .putBoolean(KEY_FLIRTING, preference.flirting)
            .putBoolean(KEY_INTIMATE, preference.intimateConversation)
            .putBoolean(KEY_ROLEPLAY, preference.roleplay)
            .apply()
        _snapshot.value = read()
    }

    fun setRomanticConversation(enabled: Boolean) = save(get().copy(romanticConversation = enabled))
    fun setFlirting(enabled: Boolean) = save(get().copy(flirting = enabled))
    fun setIntimateConversation(enabled: Boolean) = save(get().copy(intimateConversation = enabled))
    fun setRoleplay(enabled: Boolean) = save(get().copy(roleplay = enabled))

    private fun read(): InteractionPreference = InteractionPreference(
        romanticConversation = prefs.getBoolean(KEY_ROMANTIC, DEFAULT_ROMANTIC),
        flirting = prefs.getBoolean(KEY_FLIRTING, DEFAULT_FLIRTING),
        intimateConversation = prefs.getBoolean(KEY_INTIMATE, DEFAULT_INTIMATE),
        roleplay = prefs.getBoolean(KEY_ROLEPLAY, DEFAULT_ROLEPLAY),
    )

    companion object {
        private const val PREFS_NAME = "interaction_preference"
        private const val KEY_ROMANTIC = "romantic_conversation"
        private const val KEY_FLIRTING = "flirting"
        private const val KEY_INTIMATE = "intimate_conversation"
        private const val KEY_ROLEPLAY = "roleplay"

        /** 默认全部关闭：普通交流 */
        const val DEFAULT_ROMANTIC = false
        const val DEFAULT_FLIRTING = false
        const val DEFAULT_INTIMATE = false
        const val DEFAULT_ROLEPLAY = false
    }
}
