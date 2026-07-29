package com.agent.chat.data.ai.prompt

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 assets 加载 Prompt 文案，避免业务代码硬编码长字符串。
 */
@Singleton
class PromptAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val cache = ConcurrentHashMap<String, String>()
    private val catalogAdapter = moshi.adapter(PromptCatalogJson::class.java)
    private val labelsType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        String::class.java,
    )
    private val labelsAdapter = moshi.adapter<Map<String, String>>(labelsType)

    @Volatile
    private var catalog: PromptCatalogJson? = null

    @Volatile
    private var labels: Map<String, String> = emptyMap()

    fun catalog(): PromptCatalogJson {
        catalog?.let { return it }
        val json = loadRaw(CATALOG_PATH)
        val parsed = catalogAdapter.fromJson(json)
            ?: PromptCatalogJson()
        catalog = parsed
        return parsed
    }

    fun label(key: String, fallback: String = ""): String {
        ensureLabels()
        return labels[key]?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun loadAsset(relativePath: String): String {
        val path = relativePath.trim().removePrefix("/")
        return cache.getOrPut(path) { loadRaw(path) }
    }

    fun baseHumanPath(rolePlayEnabled: Boolean): String = humanConversationPath(rolePlayEnabled)

    fun humanConversationPath(rolePlayEnabled: Boolean): String {
        val assets = catalog().assets
        return if (rolePlayEnabled) {
            assets["human_conversation_roleplay"]
                ?: assets["base_human_roleplay"]
                ?: DEFAULT_HUMAN_RP
        } else {
            assets["human_conversation"]
                ?: assets["base_human"]
                ?: DEFAULT_HUMAN
        }
    }

    /**
     * 简单模板替换：`{{key}}` → vars[key]
     */
    fun render(template: String, vars: Map<String, String>): String {
        var out = template
        vars.forEach { (k, v) ->
            out = out.replace("{{$k}}", v, ignoreCase = true)
        }
        return out
    }

    private fun ensureLabels() {
        if (labels.isNotEmpty()) return
        synchronized(this) {
            if (labels.isNotEmpty()) return
            val path = catalog().assets["labels"] ?: DEFAULT_LABELS
            labels = runCatching {
                labelsAdapter.fromJson(loadRaw(path)).orEmpty()
            }.getOrDefault(emptyMap())
        }
    }

    private fun loadRaw(path: String): String {
        return context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    companion object {
        private const val CATALOG_PATH = "prompts/catalog.json"
        private const val DEFAULT_HUMAN = "prompts/human_conversation.txt"
        private const val DEFAULT_HUMAN_RP = "prompts/human_conversation_roleplay.txt"
        private const val DEFAULT_LABELS = "prompts/labels.json"
    }
}

@JsonClass(generateAdapter = false)
data class PromptCatalogJson(
    val version: Int = 1,
    val layers: List<String> = emptyList(),
    val assets: Map<String, String> = emptyMap(),
)
