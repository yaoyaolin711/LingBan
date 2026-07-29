package com.agent.chat.data.provider

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 多模态消息内容片段（用于视觉 API）。
 * 序列化时 type="text" 或 type="image_url"。
 */
@JsonClass(generateAdapter = false)
data class ChatContentPart(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url")
    val imageUrl: ImageUrlContent? = null,
)

@JsonClass(generateAdapter = false)
data class ImageUrlContent(
    val url: String,
    val detail: String = "auto",
)

@JsonClass(generateAdapter = false)
data class ChatMessage(
    val role: String,
    /** 纯文本内容（非视觉消息） */
    val content: String? = null,
    /** 多模态内容片段（视觉消息，content 为 null 时使用） */
    @Json(name = "content")
    val contentParts: List<ChatContentPart>? = null,
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

        /**
         * 视觉消息：文本 + base64 图片（data URI）或 https URL。
         * imageDataUri — 格式 "data:image/jpeg;base64,..." 或 https URL
         */
        fun userWithImage(text: String, imageDataUri: String): ChatMessage {
            val parts = buildList {
                if (text.isNotBlank()) {
                    add(ChatContentPart(type = "text", text = text))
                }
                add(
                    ChatContentPart(
                        type = "image_url",
                        imageUrl = ImageUrlContent(url = imageDataUri),
                    ),
                )
            }
            return ChatMessage(role = ROLE_USER, contentParts = parts)
        }
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
