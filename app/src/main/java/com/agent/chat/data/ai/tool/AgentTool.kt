package com.agent.chat.data.ai.tool

import com.agent.chat.data.provider.ChatFunctionDefinition
import com.agent.chat.data.provider.ChatToolDefinition
import org.json.JSONObject

data class ToolExecutionContext(
    val personaId: String?,
    val conversationId: String,
)

data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: JSONObject? = null,
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("success", success)
        obj.put("message", message)
        if (data != null) {
            obj.put("data", data)
        }
        return obj.toString()
    }
}

interface AgentTool {
    val name: String
    val description: String
    /** JSON Schema properties + required */
    val parametersSchema: Map<String, Any>
    suspend fun execute(argsJson: String, context: ToolExecutionContext): ToolResult

    fun toDefinition(): ChatToolDefinition = ChatToolDefinition(
        type = "function",
        function = ChatFunctionDefinition(
            name = name,
            description = description,
            parameters = parametersSchema,
        ),
    )
}

fun stringProp(description: String) = mapOf(
    "type" to "string",
    "description" to description,
)

fun integerProp(description: String) = mapOf(
    "type" to "integer",
    "description" to description,
)

fun objectSchema(
    properties: Map<String, Any>,
    required: List<String> = emptyList(),
): Map<String, Any> = buildMap {
    put("type", "object")
    put("properties", properties)
    if (required.isNotEmpty()) put("required", required)
}
