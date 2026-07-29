package com.agent.chat.data.provider

/** 流式聊天事件：正文增量 / 工具调用增量 / 结束 */
sealed class ChatStreamEvent {
    data class ContentDelta(val text: String) : ChatStreamEvent()
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val name: String? = null,
        val argumentsDelta: String? = null,
    ) : ChatStreamEvent()
    data class Finished(val finishReason: String?) : ChatStreamEvent()
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AssistantStreamResult(
    val content: String,
    val toolCalls: List<ToolCall>,
    val finishReason: String?,
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}
