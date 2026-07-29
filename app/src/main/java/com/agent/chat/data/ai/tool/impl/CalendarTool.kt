package com.agent.chat.data.ai.tool.impl

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.integerProp
import com.agent.chat.data.ai.tool.objectSchema
import com.agent.chat.data.ai.tool.stringProp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class CalendarTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "calendar_events"
    override val description = """
        读写系统日历。action=list 列出未来几天日程；action=create 创建简单事件。
        缺日历权限时返回引导用户去授权的说明。
    """.trimIndent()

    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "action" to mapOf(
                "type" to "string",
                "enum" to listOf("list", "create"),
                "description" to "list 或 create",
            ),
            "days" to integerProp("list 时查看未来几天，默认 7"),
            "title" to stringProp("create 时事件标题"),
            "begin_millis" to mapOf(
                "type" to "integer",
                "description" to "create 时开始时间 Unix 毫秒",
            ),
            "end_millis" to mapOf(
                "type" to "integer",
                "description" to "create 时结束时间 Unix 毫秒，默认开始后 1 小时",
            ),
            "description" to stringProp("可选描述"),
        ),
        required = listOf("action"),
    )

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult {
        val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrElse {
            return ToolResult(false, "参数不是合法 JSON")
        }
        val action = args.optString("action", "list").lowercase()
        val needWrite = action == "create"
        if (!hasPermission(needWrite)) {
            return ToolResult(
                false,
                if (needWrite) {
                    "缺少日历写入权限。请在系统设置中为本应用开启日历权限后再试。"
                } else {
                    "缺少日历读取权限。请在系统设置中为本应用开启日历权限后再试。"
                },
            )
        }
        return when (action) {
            "list" -> listEvents(args.optInt("days", 7).coerceIn(1, 30))
            "create" -> createEvent(args)
            else -> ToolResult(false, "未知 action: $action")
        }
    }

    private fun hasPermission(write: Boolean): Boolean {
        val read = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!write) return read
        val w = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        return read && w
    }

    private fun listEvents(days: Int): ToolResult {
        val now = System.currentTimeMillis()
        val end = now + days * 24L * 3600_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DESCRIPTION,
        )
        val arr = JSONArray()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
            var count = 0
            while (cursor.moveToNext() && count < 30) {
                val title = cursor.getString(titleIdx).orEmpty()
                val begin = cursor.getLong(beginIdx)
                val endMs = cursor.getLong(endIdx)
                arr.put(
                    JSONObject()
                        .put("title", title)
                        .put("begin", fmt.format(begin))
                        .put("end", fmt.format(endMs))
                        .put("begin_millis", begin)
                        .put("end_millis", endMs),
                )
                count++
            }
        }
        return ToolResult(true, "找到 ${arr.length()} 条日程", JSONObject().put("events", arr))
    }

    private fun createEvent(args: JSONObject): ToolResult {
        val title = args.optString("title").trim()
        if (title.isEmpty()) return ToolResult(false, "title 不能为空")
        val begin = args.optLong("begin_millis", -1L)
        if (begin <= 0L) return ToolResult(false, "begin_millis 无效")
        val end = args.optLong("end_millis", begin + 3600_000L)
        val desc = args.optString("description").orEmpty()
        val calendarId = primaryCalendarId()
            ?: return ToolResult(false, "未找到可用日历账户")
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, desc)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return ToolResult(false, "创建日程失败")
        val id = ContentUris.parseId(uri)
        return ToolResult(
            true,
            "已创建日程",
            JSONObject().put("event_id", id).put("title", title),
        )
    }

    private fun primaryCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE}=1",
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                if (fallback == null) fallback = id
                if (primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1) return id
            }
            return fallback
        }
        return null
    }
}
