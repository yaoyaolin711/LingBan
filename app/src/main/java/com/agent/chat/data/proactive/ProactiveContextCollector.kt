package com.agent.chat.data.proactive

import android.content.Context
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.impl.AppUsageTool
import dagger.hilt.android.qualifiers.ApplicationContext
import com.agent.chat.data.ai.tool.impl.BatteryTool
import com.agent.chat.data.ai.tool.impl.NotificationTool
import com.agent.chat.data.ai.tool.impl.ScreenContentTool
import com.agent.chat.data.ai.tool.impl.ScreenStateTool
import com.agent.chat.data.screen.AgentAccessibilityService
import com.agent.chat.data.screen.ScreenContentStore
import com.agent.chat.data.settings.ToolSettings
import com.agent.chat.data.settings.ToolSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在主动消息触发前，自动调用已开启的工具采集用户环境上下文。
 * 结果以自然语言摘要注入 prompt，让 AI 基于真实感知发消息。
 */
@Singleton
class ProactiveContextCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val toolSettingsStore: ToolSettingsStore,
    private val notificationTool: NotificationTool,
    private val appUsageTool: AppUsageTool,
    private val batteryTool: BatteryTool,
    private val screenStateTool: ScreenStateTool,
) {

    suspend fun collect(toolContext: ToolExecutionContext): String {
        val settings = toolSettingsStore.get()
        val parts = mutableListOf<String>()

        if (settings.screenStateEnabled) {
            val r = runCatching { screenStateTool.execute("{}", toolContext) }.getOrNull()
            if (r != null && r.success) {
                parts += "屏幕状态：${r.message}"
            }
        }

        if (settings.batteryEnabled) {
            val r = runCatching { batteryTool.execute("{}", toolContext) }.getOrNull()
            if (r != null && r.success) {
                parts += "电量：${r.message}"
            }
        }

        if (settings.notificationEnabled) {
            val r = runCatching {
                notificationTool.execute("""{"limit":5}""", toolContext)
            }.getOrNull()
            if (r != null && r.success) {
                val data = r.data
                if (data != null) {
                    val items = data.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val sb = StringBuilder("最近通知：")
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            sb.append(item.optString("app"))
                            sb.append("「").append(item.optString("title")).append("」")
                            sb.append("(").append(item.optLong("minutesAgo")).append("分钟前)")
                            if (i < items.length() - 1) sb.append("、")
                        }
                        parts += sb.toString()
                    }
                }
            }
        }

        if (settings.appUsageEnabled) {
            val r = runCatching {
                appUsageTool.execute("""{"limit":3}""", toolContext)
            }.getOrNull()
            if (r != null && r.success) {
                val data = r.data
                if (data != null) {
                    val apps = data.optJSONArray("apps")
                    if (apps != null && apps.length() > 0) {
                        val sb = StringBuilder("今日使用：")
                        for (i in 0 until apps.length()) {
                            val app = apps.getJSONObject(i)
                            sb.append(app.optString("app"))
                            sb.append(" ").append(app.optInt("minutes")).append("分钟")
                            if (i < apps.length() - 1) sb.append("、")
                        }
                        parts += sb.toString()
                    }
                }
            }
        }

        if (settings.screenContentEnabled && AgentAccessibilityService.isRunning()) {
            val snap = ScreenContentStore.snapshot.value
            val ageMs = System.currentTimeMillis() - snap.updatedAt
            if (ageMs < 5 * 60_000L && snap.screenTexts.isNotEmpty()) {
                val pm = context.packageManager
                val appName = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(snap.foregroundApp, 0)).toString()
                }.getOrDefault(snap.foregroundApp)
                parts += "正在使用「$appName」"
                val preview = snap.screenTexts.take(10).joinToString("；")
                if (preview.isNotBlank()) {
                    parts += "屏幕内容摘要：$preview"
                }
            }
        }

        if (parts.isEmpty()) return ""

        return buildString {
            append("【主动感知 · AI 已自动采集的用户环境】\n")
            append("以下信息是你主动获取的，用户没有主动告诉你。可以自然地关心，像真人注意到对方在做什么。\n")
            parts.forEach { append("- ").append(it).append('\n') }
        }.trim()
    }
}
