package com.agent.chat.data.ai.tool.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.integerProp
import com.agent.chat.data.ai.tool.objectSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SmsTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "get_recent_sms"
    override val description = "读取最近收到的短信摘要（发件人+时间+正文前80字）。需要短信权限，默认只读收件箱。"
    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "limit" to integerProp("返回最近 N 条，默认 5，最大 10"),
        ),
    )

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult =
        withContext(Dispatchers.IO) {
            if (!hasSmsPermission()) {
                return@withContext ToolResult(
                    false,
                    "缺少短信读取权限。请在系统设置中为本应用开启短信权限后再试。",
                )
            }

            val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            val limit = args.optInt("limit", 5).coerceIn(1, 10)

            val now = System.currentTimeMillis()
            val arr = JSONArray()
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.READ,
                    ),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC",
                )
                var count = 0
                while (cursor != null && cursor.moveToNext() && count < limit) {
                    val address = cursor.getString(0) ?: "未知"
                    val body = cursor.getString(1) ?: ""
                    val date = cursor.getLong(2)
                    val read = cursor.getInt(3) == 1
                    val minutesAgo = ((now - date) / 60_000L).coerceAtLeast(0)

                    arr.put(
                        JSONObject()
                            .put("from", address)
                            .put("preview", body.take(80))
                            .put("minutesAgo", minutesAgo)
                            .put("read", read),
                    )
                    count++
                }
            } finally {
                cursor?.close()
            }

            ToolResult(
                true,
                "已获取最近短信（${arr.length()} 条）",
                JSONObject().put("messages", arr),
            )
        }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
}
