package com.agent.chat.data.care

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

/**
 * 按人设维度保存「待跟进」短线索，跨会话注入关怀上下文。
 */
@Singleton
class FollowUpStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(personaId: String?): List<String> {
        if (personaId.isNullOrBlank()) return emptyList()
        val raw = prefs.getString(key(personaId), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun merge(personaId: String?, candidates: List<String>) {
        if (personaId.isNullOrBlank() || candidates.isEmpty()) return
        val merged = (candidates + get(personaId))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX)
        val arr = JSONArray()
        merged.forEach { arr.put(it) }
        prefs.edit().putString(key(personaId), arr.toString()).apply()
    }

    fun clear(personaId: String?) {
        if (personaId.isNullOrBlank()) return
        prefs.edit().remove(key(personaId)).apply()
    }

    fun formatForPrompt(personaId: String?): String? {
        val items = get(personaId)
        if (items.isEmpty()) return null
        return buildString {
            append("【待跟进】用户曾提到这些，合适时自然问起一句即可，勿逐条盘问：")
            items.forEach { append("\n- ").append(it) }
        }
    }

    private fun key(personaId: String) = "fu_$personaId"

    companion object {
        private const val PREFS = "follow_ups"
        private const val MAX = 8
    }
}
