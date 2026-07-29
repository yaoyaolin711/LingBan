package com.agent.chat.data.ai.tool

import com.agent.chat.data.ai.tool.impl.AlarmTool
import com.agent.chat.data.ai.tool.impl.AppUsageTool
import com.agent.chat.data.ai.tool.impl.BatteryTool
import com.agent.chat.data.ai.tool.impl.CalendarTool
import com.agent.chat.data.ai.tool.impl.DeviceInfoTool
import com.agent.chat.data.ai.tool.impl.LocationTool
import com.agent.chat.data.ai.tool.impl.MemoryTool
import com.agent.chat.data.ai.tool.impl.TimeTool
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
        }
        return all
    }

    fun definitions(settings: ToolSettings = toolSettingsStore.get()): List<ChatToolDefinition> =
        enabledTools(settings).map { it.toDefinition() }

    fun find(name: String, settings: ToolSettings = toolSettingsStore.get()): AgentTool? =
        enabledTools(settings).find { it.name == name }
}
