package com.agent.chat.data.notification

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class NotificationHistoryItem(
    val packageName: String,
    val appName: String,
    val title: String,
    val textPreview: String?,
    val postedAt: Long,
)

@Singleton
class NotificationHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun add(item: NotificationHistoryItem, maxItems: Int = MAX_ITEMS) {
        synchronized(lock) {
            val current = readItems().toMutableList()
            current.add(item)
            current.sortByDescending { it.postedAt }
            val trimmed = current.take(maxItems)
            writeItems(trimmed)
        }
    }

    fun getRecent(limit: Int): List<NotificationHistoryItem> {
        val safe = limit.coerceIn(1, MAX_LIMIT)
        synchronized(lock) {
            return readItems()
                .sortedByDescending { it.postedAt }
                .take(safe)
        }
    }

    private fun readItems(): List<NotificationHistoryItem> {
        val raw = prefs.getString(KEY_ITEMS_JSON, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<NotificationHistoryItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val postedAt = obj.optLong("postedAt", 0L)
            if (postedAt <= 0L) continue
            out.add(
                NotificationHistoryItem(
                    packageName = obj.optString("packageName"),
                    appName = obj.optString("appName"),
                    title = obj.optString("title"),
                    textPreview = obj.optString("textPreview", null).takeIf { it?.isNotBlank() == true },
                    postedAt = postedAt,
                ),
            )
        }
        return out
    }

    private fun writeItems(items: List<NotificationHistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("packageName", item.packageName)
                    .put("appName", item.appName)
                    .put("title", item.title)
                    .put("textPreview", item.textPreview ?: JSONObject.NULL)
                    .put("postedAt", item.postedAt),
            )
        }
        prefs.edit().putString(KEY_ITEMS_JSON, arr.toString()).apply()
    }

    fun resolveAppName(packageName: String): String {
        val pm = context.packageManager
        return runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    companion object {
        private const val PREFS_NAME = "notification_history"
        private const val KEY_ITEMS_JSON = "items_json"
        private const val MAX_ITEMS = 30
        private const val MAX_LIMIT = 10
    }
}

