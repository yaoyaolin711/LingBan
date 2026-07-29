package com.agent.chat.data.ai

import com.agent.chat.data.ai.tool.LocalToolRegistry
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.provider.AIProvider
import com.agent.chat.data.provider.AssistantStreamResult
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ChatStreamEvent
import com.agent.chat.data.provider.ChatToolCallFunction
import com.agent.chat.data.provider.ChatToolCallMessage
import com.agent.chat.data.provider.ModelConfig
import com.agent.chat.data.provider.ToolCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class ToolChatEvent {
    data class ContentDelta(val text: String) : ToolChatEvent()
    data class ToolStarted(val call: ToolCall) : ToolChatEvent()
    data class ToolFinished(
        val call: ToolCall,
        val success: Boolean,
        val resultPreview: String,
    ) : ToolChatEvent()
    data class Completed(val finalContent: String) : ToolChatEvent()
}

@Singleton
class ToolChatOrchestrator @Inject constructor(
    private val aiProvider: AIProvider,
    private val toolRegistry: LocalToolRegistry,
) {

    fun run(
        messages: List<ChatMessage>,
        baseConfig: ModelConfig,
        context: ToolExecutionContext,
        maxSteps: Int = MAX_STEPS,
    ): Flow<ToolChatEvent> = flow {
        val working = messages.toMutableList()
        val tools = toolRegistry.definitions()
        val config = if (tools.isEmpty()) {
            baseConfig.copy(tools = null, toolChoice = null)
        } else {
            baseConfig.copy(tools = tools, toolChoice = baseConfig.toolChoice ?: "auto")
        }

        for (step in 0 until maxSteps) {
            val round = collectRound(working, config)
            if (!round.hasToolCalls) {
                // 最终轮：按已收集正文分片推送，便于 UI 流式展示
                if (round.content.isNotEmpty()) {
                    emit(ToolChatEvent.ContentDelta(round.content))
                }
                emit(ToolChatEvent.Completed(round.content))
                return@flow
            }

            working.add(
                ChatMessage.assistantToolCalls(
                    toolCalls = round.toolCalls.map { tc ->
                        ChatToolCallMessage(
                            id = tc.id,
                            function = ChatToolCallFunction(
                                name = tc.name,
                                arguments = tc.arguments,
                            ),
                        )
                    },
                    content = round.content.ifBlank { null },
                ),
            )

            for (call in round.toolCalls) {
                emit(ToolChatEvent.ToolStarted(call))
                val tool = toolRegistry.find(call.name)
                val result = if (tool == null) {
                    ToolResult(false, "未知工具: ${call.name}")
                } else {
                    runCatching {
                        tool.execute(call.arguments.ifBlank { "{}" }, context)
                    }.getOrElse { e ->
                        ToolResult(false, "工具执行失败: ${e.message}")
                    }
                }
                emit(
                    ToolChatEvent.ToolFinished(
                        call = call,
                        success = result.success,
                        resultPreview = result.message,
                    ),
                )
                working.add(
                    ChatMessage.tool(
                        toolCallId = call.id,
                        content = result.toJsonString(),
                        name = call.name,
                    ),
                )
            }

            if (step == maxSteps - 1) {
                emit(ToolChatEvent.Completed("（工具调用步数已达上限，请再试一次）"))
            }
        }
    }

    private suspend fun collectRound(
        messages: List<ChatMessage>,
        config: ModelConfig,
    ): AssistantStreamResult {
        val content = StringBuilder()
        val builders = LinkedHashMap<Int, ToolCallBuilder>()
        var finishReason: String? = null

        aiProvider.chatStreamEvents(messages, config).collect { event ->
            when (event) {
                is ChatStreamEvent.ContentDelta -> content.append(event.text)
                is ChatStreamEvent.ToolCallDelta -> {
                    val b = builders.getOrPut(event.index) { ToolCallBuilder() }
                    if (!event.id.isNullOrBlank()) b.id = event.id
                    if (!event.name.isNullOrBlank()) b.name = event.name
                    if (!event.argumentsDelta.isNullOrEmpty()) {
                        b.arguments.append(event.argumentsDelta)
                    }
                }
                is ChatStreamEvent.Finished -> finishReason = event.finishReason
            }
        }

        val toolCalls = builders.entries
            .sortedBy { it.key }
            .mapNotNull { (_, b) ->
                val id = b.id ?: return@mapNotNull null
                val name = b.name ?: return@mapNotNull null
                ToolCall(id = id, name = name, arguments = b.arguments.toString())
            }

        return AssistantStreamResult(
            content = content.toString(),
            toolCalls = toolCalls,
            finishReason = finishReason,
        )
    }

    private class ToolCallBuilder {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    companion object {
        const val MAX_STEPS = 8
    }
}
