package com.agent.chat.ui.chat

enum class ChatPresenceStatus {
    Idle,
    Thinking,
    Typing,
}

fun resolveChatPresence(
    isStreaming: Boolean,
    showPaceTyping: Boolean,
    streamingContentEmpty: Boolean,
    hasRunningTools: Boolean,
): ChatPresenceStatus = when {
    hasRunningTools || (isStreaming && streamingContentEmpty) -> ChatPresenceStatus.Thinking
    isStreaming || showPaceTyping -> ChatPresenceStatus.Typing
    else -> ChatPresenceStatus.Idle
}

fun ChatPresenceStatus.label(): String = when (this) {
    ChatPresenceStatus.Idle -> "正在陪伴"
    ChatPresenceStatus.Thinking -> "正在思考"
    ChatPresenceStatus.Typing -> "正在输入"
}
