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
)
