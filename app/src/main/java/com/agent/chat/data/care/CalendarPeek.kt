package com.agent.chat.data.care

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class UpcomingEvent(
    val title: String,
    val beginAt: Long,
)

/**
 * 只读窥探近期日程，供关怀上下文使用（非工具协议）。
 */
@Singleton
class CalendarPeek @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun upcoming(withinHours: Int = 12, limit: Int = 3): List<UpcomingEvent> {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return emptyList()

        val now = System.currentTimeMillis()
        val end = now + withinHours * 3600_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
        )
        val result = mutableListOf<UpcomingEvent>()
        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                while (cursor.moveToNext() && result.size < limit) {
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
                    val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
                    if (title.isBlank() || begin <= 0L) continue
                    result.add(UpcomingEvent(title = title.trim(), beginAt = begin))
                }
            }
        }
        return result
    }

    fun formatForPrompt(events: List<UpcomingEvent>): String? {
        if (events.isEmpty()) return null
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return buildString {
            append("【近期日程】（有权限时注入；请自然关心，勿机械宣读）")
            events.forEach { e ->
                append("\n- ")
                append(fmt.format(Date(e.beginAt)))
                append(" ")
                append(e.title)
            }
        }
    }
}
