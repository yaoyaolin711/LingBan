package com.agent.chat.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ToolSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<ToolSettings> = _snapshot.asStateFlow()

    fun get(): ToolSettings = _snapshot.value

    fun update(transform: (ToolSettings) -> ToolSettings) {
        val next = transform(get())
        prefs.edit()
            .putBoolean(KEY_MEMORY, next.memoryEnabled)
            .putBoolean(KEY_TIME, next.timeEnabled)
            .putBoolean(KEY_BATTERY, next.batteryEnabled)
            .putBoolean(KEY_DEVICE, next.deviceEnabled)
            .putBoolean(KEY_CALENDAR, next.calendarEnabled)
            .putBoolean(KEY_ALARM, next.alarmEnabled)
            .putBoolean(KEY_LOCATION, next.locationEnabled)
            .putBoolean(KEY_APP_USAGE, next.appUsageEnabled)
            .putBoolean(KEY_NOTIFICATION, next.notificationEnabled)
            .putBoolean(KEY_MUSIC, next.musicEnabled)
            .putBoolean(KEY_SMS, next.smsEnabled)
            .putBoolean(KEY_SCREEN_STATE, next.screenStateEnabled)
            .putBoolean(KEY_SCREEN_CONTENT, next.screenContentEnabled)
            .apply()
        _snapshot.value = next
    }

    private fun read(): ToolSettings = ToolSettings(
        memoryEnabled = prefs.getBoolean(KEY_MEMORY, true),
        timeEnabled = prefs.getBoolean(KEY_TIME, true),
        batteryEnabled = prefs.getBoolean(KEY_BATTERY, true),
        deviceEnabled = prefs.getBoolean(KEY_DEVICE, true),
        calendarEnabled = prefs.getBoolean(KEY_CALENDAR, true),
        alarmEnabled = prefs.getBoolean(KEY_ALARM, true),
        locationEnabled = prefs.getBoolean(KEY_LOCATION, false),
        appUsageEnabled = prefs.getBoolean(KEY_APP_USAGE, false),
        notificationEnabled = prefs.getBoolean(KEY_NOTIFICATION, false),
        musicEnabled = prefs.getBoolean(KEY_MUSIC, false),
        smsEnabled = prefs.getBoolean(KEY_SMS, false),
        screenStateEnabled = prefs.getBoolean(KEY_SCREEN_STATE, false),
        screenContentEnabled = prefs.getBoolean(KEY_SCREEN_CONTENT, false),
    )

    companion object {
        private const val PREFS_NAME = "tool_settings"
        private const val KEY_MEMORY = "memory"
        private const val KEY_TIME = "time"
        private const val KEY_BATTERY = "battery"
        private const val KEY_DEVICE = "device"
        private const val KEY_CALENDAR = "calendar"
        private const val KEY_ALARM = "alarm"
        private const val KEY_LOCATION = "location"
        private const val KEY_APP_USAGE = "app_usage"
        private const val KEY_NOTIFICATION = "notification"
        private const val KEY_MUSIC = "music"
        private const val KEY_SMS = "sms"
        private const val KEY_SCREEN_STATE = "screen_state"
        private const val KEY_SCREEN_CONTENT = "screen_content"
    }
}

data class ToolSettings(
    val memoryEnabled: Boolean = true,
    val timeEnabled: Boolean = true,
    val batteryEnabled: Boolean = true,
    val deviceEnabled: Boolean = true,
    val calendarEnabled: Boolean = true,
    val alarmEnabled: Boolean = true,
    val locationEnabled: Boolean = false,
    val appUsageEnabled: Boolean = false,
    val notificationEnabled: Boolean = false,
    val musicEnabled: Boolean = false,
    val smsEnabled: Boolean = false,
    val screenStateEnabled: Boolean = false,
    val screenContentEnabled: Boolean = false,
)
