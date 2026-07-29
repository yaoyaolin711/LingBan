package com.agent.chat.data.ai.tool.impl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.objectSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class BatteryTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "get_battery"
    override val description = "获取设备当前电量百分比与充电状态。关心用户是否该充电时可调用。"
    override val parametersSchema: Map<String, Any> = objectSchema(properties = emptyMap())

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ToolResult(false, "无法读取电池状态")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return ToolResult(
            success = true,
            message = "电量 $pct%",
            data = JSONObject()
                .put("percent", pct)
                .put("charging", charging),
        )
    }
}
