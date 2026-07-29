package com.agent.chat.data.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

interface AIProvider {
    /**
     * 流式聊天，产出结构化事件（正文 / tool_calls）。
     */
    suspend fun chatStreamEvents(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ): Flow<ChatStreamEvent>

    /**
     * 仅收集正文 token，兼容智能导入、记忆摘要等纯文本场景。
     */
    suspend fun chatStream(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ): Flow<String> = chatStreamEvents(messages, config).mapNotNull { event ->
        (event as? ChatStreamEvent.ContentDelta)?.text
    }
}
