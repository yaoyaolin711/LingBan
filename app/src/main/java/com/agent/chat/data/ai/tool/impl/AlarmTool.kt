package com.agent.chat.data.ai.tool.impl

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.integerProp
import com.agent.chat.data.ai.tool.objectSchema
import com.agent.chat.data.ai.tool.stringProp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class AlarmTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "set_alarm"
    override val description = "调起系统闹钟应用，设置指定小时与分钟的闹钟（24 小时制）。"
    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "hour" to integerProp("小时 0-23"),
            "minute" to integerProp("分钟 0-59"),
            "message" to stringProp("闹钟备注，可选"),
            "skip_ui" to mapOf(
                "type" to "boolean",
                "description" to "若系统支持则尽量跳过确认界面，默认 false",
            ),
        ),
        required = listOf("hour", "minute"),
    )

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult {
        val args = runCatching { JSONObject(argsJson) }.getOrElse {
            return ToolResult(false, "参数不是合法 JSON")
        }
        val hour = args.optInt("hour", -1)
        val minute = args.optInt("minute", -1)
        if (hour !in 0..23 || minute !in 0..59) {
            return ToolResult(false, "hour/minute 无效")
        }
        val message = args.optString("message").ifBlank { "提醒" }
        val skipUi = args.optBoolean("skip_ui", false)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult(
                true,
                "已打开系统闹钟设置",
                JSONObject()
                    .put("hour", hour)
                    .put("minute", minute)
                    .put("message", message),
            )
        } catch (e: Exception) {
            ToolResult(false, "无法打开闹钟应用: ${e.message}")
        }
    }
}
