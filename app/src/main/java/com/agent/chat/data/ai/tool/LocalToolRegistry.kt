package com.agent.chat.data.ai.tool

import com.agent.chat.data.ai.tool.impl.AlarmTool
import com.agent.chat.data.ai.tool.impl.AppUsageTool
import com.agent.chat.data.ai.tool.impl.BatteryTool
import com.agent.chat.data.ai.tool.impl.CalendarTool
import com.agent.chat.data.ai.tool.impl.DeviceInfoTool
import com.agent.chat.data.ai.tool.impl.LocationTool
import com.agent.chat.data.ai.tool.impl.MemoryTool
import com.agent.chat.data.ai.tool.impl.MusicTool
import com.agent.chat.data.ai.tool.impl.NotificationTool
import com.agent.chat.data.ai.tool.impl.ScreenContentTool
import com.agent.chat.data.ai.tool.impl.ScreenStateTool
import com.agent.chat.data.ai.tool.impl.SmsTool
import com.agent.chat.data.ai.tool.impl.TimeTool
import com.agent.chat.data.ai.tool.impl.WebSearchTool
import com.agent.chat.data.provider.ChatToolDefinition
import com.agent.chat.data.settings.ToolSettings
import com.agent.chat.data.settings.ToolSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalToolRegistry @Inject constructor(
    private val toolSettingsStore: ToolSettingsStore,
    private val memoryTool: MemoryTool,
    private val timeTool: TimeTool,
    private val batteryTool: BatteryTool,
    private val deviceInfoTool: DeviceInfoTool,
    private val calendarTool: CalendarTool,
    private val alarmTool: AlarmTool,
    private val locationTool: LocationTool,
    private val appUsageTool: AppUsageTool,
    private val notificationTool: NotificationTool,
    private val musicTool: MusicTool,
    private val smsTool: SmsTool,
    private val screenStateTool: ScreenStateTool,
    private val screenContentTool: ScreenContentTool,
    private val webSearchTool: WebSearchTool,
) {

    fun enabledTools(settings: ToolSettings = toolSettingsStore.get()): List<AgentTool> {
        val all = buildList {
            if (settings.memoryEnabled) add(memoryTool)
            if (settings.timeEnabled) add(timeTool)
            if (settings.batteryEnabled) add(batteryTool)
            if (settings.deviceEnabled) add(deviceInfoTool)
            if (settings.calendarEnabled) add(calendarTool)
            if (settings.alarmEnabled) add(alarmTool)
            if (settings.locationEnabled) add(locationTool)
            if (settings.appUsageEnabled) add(appUsageTool)
            if (settings.notificationEnabled) add(notificationTool)
            if (settings.musicEnabled) add(musicTool)
            if (settings.smsEnabled) add(smsTool)
            if (settings.screenStateEnabled) add(screenStateTool)
            if (settings.screenContentEnabled) add(screenContentTool)
            if (settings.webSearchEnabled) add(webSearchTool)
        }
        return all
    }

    fun definitions(settings: ToolSettings = toolSettingsStore.get()): List<ChatToolDefinition> =
        enabledTools(settings).map { it.toDefinition() }

    fun find(name: String, settings: ToolSettings = toolSettingsStore.get()): AgentTool? =
        enabledTools(settings).find { it.name == name }
}
