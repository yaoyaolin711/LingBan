package com.agent.chat.data.ai.tool.impl

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.objectSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class ScreenStateTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "get_screen_state"
    override val description = "查看手机屏幕状态（亮屏/息屏、是否锁屏）。无需额外权限。"
    override val parametersSchema: Map<String, Any> = objectSchema(properties = emptyMap())

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val screenOn = pm.isInteractive
        val locked = km.isKeyguardLocked

        val status = when {
            !screenOn -> "息屏"
            locked -> "亮屏但锁屏中"
            else -> "亮屏已解锁"
        }

        return ToolResult(
            true,
            status,
            JSONObject()
                .put("screenOn", screenOn)
                .put("locked", locked)
                .put("status", status),
        )
    }
}
