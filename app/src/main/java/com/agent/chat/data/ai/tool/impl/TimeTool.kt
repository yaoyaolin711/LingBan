package com.agent.chat.data.ai.tool.impl

import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.objectSchema
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class TimeTool @Inject constructor() : AgentTool {
    override val name = "get_current_time"
    override val description = "获取用户设备当前的本地日期、时间与星期。需要判断时段、是否该吃饭睡觉、隔了多久时调用。"
    override val parametersSchema: Map<String, Any> = objectSchema(properties = emptyMap())

    override suspend fun execute(argsJson: String, context: ToolExecutionContext): ToolResult {
        val now = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val weekDays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
        val week = weekDays[now.get(Calendar.DAY_OF_WEEK) - 1]
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 5..10 -> "早上"
            in 11..13 -> "中午"
            in 14..17 -> "下午"
            in 18..22 -> "晚上"
            else -> "深夜"
        }
        return ToolResult(
            success = true,
            message = "当前时间",
            data = JSONObject()
                .put("datetime", fmt.format(now.time))
                .put("weekday", week)
                .put("period", period)
                .put("timezone", TimeZoneId()),
        )
    }

    private fun TimeZoneId(): String =
        java.util.TimeZone.getDefault().id
}
