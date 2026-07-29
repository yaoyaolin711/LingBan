package com.agent.chat.data.ai.tool.impl

import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.objectSchema
import com.agent.chat.data.ai.tool.stringProp
import com.agent.chat.data.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class MemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository,
) : AgentTool {

    override val name: String = "memory"

    override val description: String = """
        跨对话长期记忆。用 action 控制：create 新增、edit 更新、delete 删除、list 列出。
        可记：称呼偏好、聊天风格、计划、关系细节等。不要存敏感隐私。
        不要主动把记忆原文念给用户听，除非对方明确要求。
    """.trimIndent()

    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "action" to mapOf(
                "type" to "string",
                "enum" to listOf("create", "edit", "delete", "list"),
                "description" to "操作类型",
            ),
            "id" to stringProp("记忆 id（edit/delete 必填）"),
            "content" to stringProp("记忆内容（create/edit 必填）"),
            "importance" to mapOf(
                "type" to "integer",
                "description" to "重要度 1-10，默认 5",
            ),
        ),
        required = listOf("action"),
    )

    override suspend fun execute(argsJson: String, context: ToolExecutionContext): ToolResult {
        val personaId = context.personaId
            ?: return ToolResult(false, "当前会话未绑定人设，无法使用记忆")
        val args = runCatching { JSONObject(argsJson) }.getOrElse {
            return ToolResult(false, "参数不是合法 JSON")
        }
        val action = args.optString("action").trim().lowercase()
        return when (action) {
            "create" -> {
                val content = args.optString("content").trim()
                if (content.isEmpty()) return ToolResult(false, "content 不能为空")
                val importance = args.optInt("importance", 5)
                val memory = memoryRepository.saveMemory(
                    personaId = personaId,
                    conversationId = context.conversationId,
                    content = content,
                    importance = importance,
                )
                ToolResult(
                    success = true,
                    message = "已创建记忆",
                    data = JSONObject()
                        .put("id", memory.id)
                        .put("content", memory.content)
                        .put("importance", memory.importance),
                )
            }
            "edit" -> {
                val id = args.optString("id").trim()
                val content = args.optString("content").trim()
                if (id.isEmpty() || content.isEmpty()) {
                    return ToolResult(false, "edit 需要 id 与 content")
                }
                val target = memoryRepository.getById(id)
                    ?: return ToolResult(false, "找不到记忆 id=$id")
                if (target.personaId != personaId) {
                    return ToolResult(false, "记忆不属于当前人设")
                }
                val importance = if (args.has("importance")) args.optInt("importance") else target.importance
                val updated = memoryRepository.updateMemoryContent(
                    memory = target,
                    content = content,
                    importance = importance,
                    conversationId = context.conversationId,
                )
                ToolResult(
                    success = true,
                    message = "已更新记忆",
                    data = JSONObject()
                        .put("id", updated.id)
                        .put("content", updated.content)
                        .put("importance", updated.importance),
                )
            }
            "delete" -> {
                val id = args.optString("id").trim()
                if (id.isEmpty()) return ToolResult(false, "delete 需要 id")
                memoryRepository.deleteMemory(id)
                ToolResult(true, "已删除记忆", JSONObject().put("id", id))
            }
            "list" -> {
                val list = memoryRepository.retrieveForPrompt(
                    personaId = personaId,
                    queryText = "",
                    maxTokens = 2000,
                    maxItems = 50,
                ).memories
                val arr = JSONArray()
                list.forEach { m ->
                    arr.put(
                        JSONObject()
                            .put("id", m.id)
                            .put("content", m.content)
                            .put("importance", m.importance)
                            .put("category", m.category.storageKey),
                    )
                }
                ToolResult(true, "共 ${list.size} 条记忆", JSONObject().put("memories", arr))
            }
            else -> ToolResult(false, "未知 action: $action")
        }
    }
}
