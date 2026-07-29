package com.agent.chat.data.ai.tool.impl

import android.os.Build
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.objectSchema
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class DeviceInfoTool @Inject constructor() : AgentTool {
    override val name = "get_device_info"
    override val description = "获取设备品牌、型号与 Android 版本等基础信息。"
    override val parametersSchema: Map<String, Any> = objectSchema(properties = emptyMap())

    override suspend fun execute(argsJson: String, context: ToolExecutionContext): ToolResult {
        return ToolResult(
            success = true,
            message = "设备信息",
            data = JSONObject()
                .put("brand", Build.BRAND)
                .put("model", Build.MODEL)
                .put("manufacturer", Build.MANUFACTURER)
                .put("android_release", Build.VERSION.RELEASE)
                .put("sdk_int", Build.VERSION.SDK_INT),
        )
    }
}
