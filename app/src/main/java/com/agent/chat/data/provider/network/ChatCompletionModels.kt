package com.agent.chat.data.provider.network

import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ChatToolDefinition
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @Json(name = "response_format")
    val responseFormat: ResponseFormat? = null,
    val tools: List<ChatToolDefinition>? = null,
    @Json(name = "tool_choice")
    val toolChoice: String? = null,
)

@JsonClass(generateAdapter = false)
data class ResponseFormat(
    val type: String,
)

@JsonClass(generateAdapter = false)
data class ChatCompletionChunk(
    val choices: List<Choice> = emptyList(),
) {
    @JsonClass(generateAdapter = false)
    data class Choice(
        val delta: Delta? = null,
        @Json(name = "finish_reason") val finishReason: String? = null,
    )

    @JsonClass(generateAdapter = false)
    data class Delta(
        val role: String? = null,
        val content: String? = null,
        @Json(name = "tool_calls")
        val toolCalls: List<ToolCallDelta>? = null,
    )

    @JsonClass(generateAdapter = false)
    data class ToolCallDelta(
        val index: Int = 0,
        val id: String? = null,
        val type: String? = null,
        val function: ToolCallFunctionDelta? = null,
    )

    @JsonClass(generateAdapter = false)
    data class ToolCallFunctionDelta(
        val name: String? = null,
        val arguments: String? = null,
    )
}
