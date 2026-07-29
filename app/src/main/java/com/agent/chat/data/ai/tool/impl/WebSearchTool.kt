package com.agent.chat.data.ai.tool.impl

import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.objectSchema
import com.agent.chat.data.ai.tool.stringProp
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 网络搜索工具 — 通过 DuckDuckGo Lite 实现，无需 API Key。
 *
 * 参数：
 *   query  — 搜索关键词（必填）
 *   count  — 返回结果数量，默认 5，最多 10
 *
 * 实现策略：
 *   1. 先请求 DuckDuckGo Instant Answer API（JSON），提取摘要 + 相关主题。
 *   2. 返回结构化文本供 AI 引用。
 *   3. 无摘要时提示 AI 直接回复已知信息。
 *
 * 注意：DuckDuckGo Instant Answer API 属于公开接口，使用中请遵守其服务条款。
 */
@Singleton
class WebSearchTool @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : AgentTool {

    override val name: String = "web_search"
    override val description: String =
        "搜索互联网获取最新信息、新闻、事实核查。返回搜索摘要和相关链接。" +
            "当用户询问当前时事、最新数据、不确定的事实时使用此工具。"

    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "query" to stringProp("搜索关键词或问题"),
            "count" to mapOf(
                "type" to "integer",
                "description" to "返回结果数量 (1-10)，默认 5",
                "minimum" to 1,
                "maximum" to 10,
            ),
        ),
        required = listOf("query"),
    )

    override suspend fun execute(argsJson: String, context: ToolExecutionContext): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val args = JSONObject(argsJson)
                val query = args.optString("query").trim()
                val count = args.optInt("count", 5).coerceIn(1, 10)

                if (query.isBlank()) {
                    return@withContext ToolResult(false, "搜索关键词不能为空")
                }

                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_redirect=1&no_html=1&skip_disambig=1"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (compatible; SolaceApp/1.0)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext ToolResult(
                        success = false,
                        message = "搜索请求失败 (HTTP ${response.code})",
                    )
                }

                val body = response.body?.string() ?: return@withContext ToolResult(
                    false,
                    "搜索响应为空",
                )
                response.close()

                parseSearchResponse(body, query, count)
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    message = "搜索失败：${e.message?.take(120).orEmpty()}",
                )
            }
        }
    }

    private fun parseSearchResponse(json: String, query: String, count: Int): ToolResult {
        return try {
            val root = JSONObject(json)
            val buf = StringBuilder()

            // Abstract / featured snippet
            val abstract = root.optString("Abstract", "").trim()
            val abstractSource = root.optString("AbstractSource", "")
            val abstractUrl = root.optString("AbstractURL", "")

            if (abstract.isNotBlank()) {
                buf.appendLine("**摘要** (来自 $abstractSource)")
                buf.appendLine(abstract)
                if (abstractUrl.isNotBlank()) buf.appendLine("来源：$abstractUrl")
                buf.appendLine()
            }

            // Answer (short direct answer)
            val answer = root.optString("Answer", "").trim()
            if (answer.isNotBlank()) {
                buf.appendLine("**直接回答**")
                buf.appendLine(answer)
                buf.appendLine()
            }

            // Related topics
            val topics = root.optJSONArray("RelatedTopics")
            if (topics != null && topics.length() > 0) {
                buf.appendLine("**相关结果**")
                var added = 0
                for (i in 0 until topics.length()) {
                    if (added >= count) break
                    val item = topics.optJSONObject(i) ?: continue
                    val text = item.optString("Text", "").trim()
                    val firstUrl = item.optString("FirstURL", "")
                    if (text.isNotBlank()) {
                        buf.appendLine("${added + 1}. $text")
                        if (firstUrl.isNotBlank()) buf.appendLine("   链接：$firstUrl")
                        added++
                    }
                }
            }

            val resultText = buf.toString().trim()
            if (resultText.isBlank()) {
                ToolResult(
                    success = true,
                    message = "搜索「$query」未找到摘要信息，建议使用已知知识回答或提示用户访问搜索引擎查阅。",
                )
            } else {
                ToolResult(
                    success = true,
                    message = resultText,
                    data = JSONObject().apply {
                        put("query", query)
                        put("abstract", abstract)
                        put("answer", answer)
                    },
                )
            }
        } catch (e: Exception) {
            ToolResult(false, "解析搜索结果失败：${e.message}")
        }
    }
}
