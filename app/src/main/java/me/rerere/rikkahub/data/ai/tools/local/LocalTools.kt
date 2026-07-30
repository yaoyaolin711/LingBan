package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.device.CompanionIntervention
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.tts.provider.TTSManager

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val ttsManager: TTSManager,
    private val settingsStore: SettingsStore,
    private val companionIntervention: CompanionIntervention,
) {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val clipboardTool by lazy { buildClipboardTool(context) }

    val ttsTool by lazy { buildTextToSpeechTool(eventBus, ttsManager, settingsStore) }

    val askUserTool by lazy { buildAskUserTool() }

    val screenTimeTool by lazy { buildScreenTimeTool(context, eventBus) }

    val calendarQueryTool by lazy { buildCalendarQueryTool(context) }

    val calendarCreateTool by lazy { buildCalendarCreateTool(context) }

    val getForegroundAppTool by lazy { buildGetForegroundAppTool(context, eventBus) }

    val getAppSessionTool by lazy { buildGetAppSessionTool(context, eventBus) }

    val openSolaceTool by lazy { buildOpenSolaceTool(companionIntervention) }

    val notifyUserTool by lazy { buildNotifyUserTool(companionIntervention) }

    val deviceShellTool by lazy { buildDeviceShellTool(settingsStore) }

    val phoneControlTools by lazy { buildPhoneControlTools(context) }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.ScreenTime)) {
            tools.add(screenTimeTool)
        }
        if (options.contains(LocalToolOption.Calendar)) {
            tools.add(calendarQueryTool)
            tools.add(calendarCreateTool)
        }
        if (options.contains(LocalToolOption.DeviceAssist)) {
            tools.add(getForegroundAppTool)
            tools.add(getAppSessionTool)
            tools.add(openSolaceTool)
            tools.add(notifyUserTool)
            if (settingsStore.settingsFlow.value.companionAssist.enableAdvancedShell) {
                tools.add(deviceShellTool)
            }
        }
        if (options.contains(LocalToolOption.PhoneControl)) {
            tools.addAll(phoneControlTools)
        }
        return tools
    }
}
