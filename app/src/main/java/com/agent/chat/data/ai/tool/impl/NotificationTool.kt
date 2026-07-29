package com.agent.chat.data.ai.tool.impl

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.integerProp
import com.agent.chat.data.ai.tool.objectSchema
import com.agent.chat.data.notification.NotificationHistoryStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class NotificationTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyStore: NotificationHistoryStore,
) : AgentTool {
    override val name: String = "get_recent_notifications"
    override val description: String = "读取最近通知（需用户开启通知访问）。默认不返回通知正文正文，仅返回应用名+标题+大概时间。"
    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "limit" to integerProp("返回最近 N 条，默认 5，最大 10"),
            "includeText" to mapOf(
                "type" to "boolean",
                "description" to "是否包含通知正文摘要（更敏感，默认 false）",
            ),
        ),
    )

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult =
        withContext(Dispatchers.IO) {
            val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            val limit = args.optInt("limit", 5).coerceIn(1, 10)
            val includeText = args.optBoolean("includeText", false)

            if (!hasNotificationAccess()) {
                return@withContext ToolResult(
                    false,
                    "未开启通知访问权限。请在系统设置中为本应用开启通知访问后再试。",
                    JSONObject().put("settings_action", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                )
            }

            val now = System.currentTimeMillis()
            val items = historyStore.getRecent(limit)
            val arr = JSONArray()
            items.forEach { item ->
                val minutesAgo = ((now - item.postedAt) / 60_000L).coerceAtLeast(0)
                val obj = JSONObject()
                    .put("package", item.packageName)
                    .put("app", item.appName)
                    .put("title", item.title)
                    .put("minutesAgo", minutesAgo)
                if (includeText && !item.textPreview.isNullOrBlank()) {
                    obj.put("text", item.textPreview)
                }
                arr.put(obj)
            }

            ToolResult(
                true,
                "已获取最近通知（${arr.length()} 条）",
                JSONObject().put("items", arr),
            )
        }

    private fun hasNotificationAccess(): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabled.contains(context.packageName)
    }
}

