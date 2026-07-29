package com.agent.chat.data.provider

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ChatMessage(
    val role: String,
    val content: String? = null,
    @Json(name = "tool_calls")
    val toolCalls: List<ChatToolCallMessage>? = null,
    @Json(name = "tool_call_id")
    val toolCallId: String? = null,
    /** tool 角色消息的函数名（部分兼容端点需要） */
    val name: String? = null,
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        fun system(content: String) = ChatMessage(ROLE_SYSTEM, content)
        fun user(content: String) = ChatMessage(ROLE_USER, content)
        fun assistant(content: String) = ChatMessage(ROLE_ASSISTANT, content)
        fun assistantToolCalls(toolCalls: List<ChatToolCallMessage>, content: String? = null) =
            ChatMessage(role = ROLE_ASSISTANT, content = content, toolCalls = toolCalls)
        fun tool(toolCallId: String, content: String, name: String? = null) =
            ChatMessage(role = ROLE_TOOL, content = content, toolCallId = toolCallId, name = name)
    }
}

@JsonClass(generateAdapter = false)
data class ChatToolCallMessage(
    val id: String,
    val type: String = "function",
    val function: ChatToolCallFunction,
)

@JsonClass(generateAdapter = false)
data class ChatToolCallFunction(
    val name: String,
    val arguments: String,
)
