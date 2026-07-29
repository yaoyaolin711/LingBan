package com.agent.chat.data.provider.network

import com.agent.chat.data.provider.ChatContentPart
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.provider.ChatToolCallMessage
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * ChatMessage 需要把 text content 和 multimodal contentParts 都序列化到同一个 content 字段，
 * 反射适配器无法处理这种重复 JSON 名称，所以这里手写一个适配器。
 */
class ChatMessageJsonAdapter(moshi: Moshi) : JsonAdapter<ChatMessage>() {

    private val stringAdapter = moshi.adapter(String::class.java)
    private val contentPartsAdapter = moshi.adapter<List<ChatContentPart>>(
        Types.newParameterizedType(List::class.java, ChatContentPart::class.java),
    )
    private val toolCallsAdapter = moshi.adapter<List<ChatToolCallMessage>>(
        Types.newParameterizedType(List::class.java, ChatToolCallMessage::class.java),
    )

    override fun fromJson(reader: JsonReader): ChatMessage? {
        throw UnsupportedOperationException("ChatMessageJsonAdapter only supports request serialization")
    }

    override fun toJson(writer: JsonWriter, value: ChatMessage?) {
        if (value == null) {
            writer.nullValue()
            return
        }

        writer.beginObject()
        writer.name("role")
        stringAdapter.toJson(writer, value.role)

        val contentParts = value.contentParts
        if (contentParts != null) {
            writer.name("content")
            contentPartsAdapter.toJson(writer, contentParts)
        } else if (value.content != null) {
            writer.name("content")
            stringAdapter.toJson(writer, value.content)
        }

        value.toolCalls?.let { toolCalls ->
            writer.name("tool_calls")
            toolCallsAdapter.toJson(writer, toolCalls)
        }
        value.toolCallId?.let { toolCallId ->
            writer.name("tool_call_id")
            stringAdapter.toJson(writer, toolCallId)
        }
        value.name?.let { name ->
            writer.name("name")
            stringAdapter.toJson(writer, name)
        }
        writer.endObject()
    }

    companion object {
        val FACTORY = JsonAdapter.Factory { type, _, moshi ->
            if (Types.getRawType(type) == ChatMessage::class.java) {
                ChatMessageJsonAdapter(moshi).nullSafe()
            } else {
                null
            }
        }
    }
}
