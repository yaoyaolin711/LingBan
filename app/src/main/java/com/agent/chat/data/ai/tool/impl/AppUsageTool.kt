package com.agent.chat.data.ai.tool.impl

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.integerProp
import com.agent.chat.data.ai.tool.objectSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AppUsageTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "get_app_usage"
    override val description = "获取今日前几名的应用使用时长（分钟）。需用户授予「使用情况访问」权限，否则返回引导。"
    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "limit" to integerProp("返回前几名，默认 5，最大 15"),
        ),
    )

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult {
        if (!hasUsageAccess()) {
            return ToolResult(
                false,
                "未授予使用情况访问权限。请引导用户打开系统设置 → 应用 → 特殊应用权限 → 使用情况访问，为本应用开启。",
                JSONObject().put("settings_action", Settings.ACTION_USAGE_ACCESS_SETTINGS),
            )
        }
        val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        val limit = args.optInt("limit", 5).coerceIn(1, 15)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 24L * 3600_000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .orEmpty()
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
        val pm = context.packageManager
        val arr = JSONArray()
        stats.forEach { s ->
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
            }.getOrDefault(s.packageName)
            val minutes = (s.totalTimeInForeground / 60_000L).toInt()
            arr.put(
                JSONObject()
                    .put("app", label)
                    .put("package", s.packageName)
                    .put("minutes", minutes),
            )
        }
        return ToolResult(true, "今日使用 Top ${arr.length()}", JSONObject().put("apps", arr))
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
