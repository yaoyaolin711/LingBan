package com.agent.chat.data.ai.tool.impl

import android.content.Context
import android.provider.Settings
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.integerProp
import com.agent.chat.data.ai.tool.objectSchema
import com.agent.chat.data.screen.AgentAccessibilityService
import com.agent.chat.data.screen.ScreenContentStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ScreenContentTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "get_screen_content"
    override val description = "读取用户当前手机屏幕上的文字内容和正在使用的 App。需要用户开启无障碍服务。"
    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "maxItems" to integerProp("最多返回多少条屏幕文字，默认 20，最大 50"),
        ),
    )

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult {
        if (!AgentAccessibilityService.isRunning()) {
            return ToolResult(
                false,
                "未开启无障碍服务。请在系统设置 → 无障碍 → Solace 中开启，让 AI 能感知你的屏幕。",
                JSONObject().put("settings_action", Settings.ACTION_ACCESSIBILITY_SETTINGS),
            )
        }

        val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        val maxItems = args.optInt("maxItems", 20).coerceIn(1, 50)

        val snapshot = ScreenContentStore.snapshot.value
        val ageMs = System.currentTimeMillis() - snapshot.updatedAt
        val ageMinutes = (ageMs / 60_000L).coerceAtLeast(0)

        val pm = context.packageManager
        val appName = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(snapshot.foregroundApp, 0)).toString()
        }.getOrDefault(snapshot.foregroundApp)

        val textsArr = JSONArray()
        snapshot.screenTexts.take(maxItems).forEach { textsArr.put(it) }

        return ToolResult(
            true,
            "用户正在使用「$appName」",
            JSONObject()
                .put("foregroundApp", appName)
                .put("foregroundPackage", snapshot.foregroundApp)
                .put("screenTexts", textsArr)
                .put("ageMinutes", ageMinutes),
        )
    }
}
