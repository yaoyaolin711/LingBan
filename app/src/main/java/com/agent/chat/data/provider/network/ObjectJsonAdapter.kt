package com.agent.chat.data.provider.network

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * 支持将 Map/List/基本类型递归序列化为 JSON，供 tools.parameters 使用。
 */
class ObjectJsonAdapter : JsonAdapter<Any>() {

    override fun fromJson(reader: JsonReader): Any? = read(reader)

    override fun toJson(writer: JsonWriter, value: Any?) {
        write(writer, value)
    }

    private fun read(reader: JsonReader): Any? {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray()
                val list = ArrayList<Any?>()
                while (reader.hasNext()) list.add(read(reader))
                reader.endArray()
                list
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                reader.beginObject()
                val map = LinkedHashMap<String, Any?>()
                while (reader.hasNext()) {
                    map[reader.nextName()] = read(reader)
                }
                reader.endObject()
                map
            }
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> {
                val raw = reader.nextString()
                raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw
            }
            JsonReader.Token.BOOLEAN -> reader.nextBoolean()
            JsonReader.Token.NULL -> {
                reader.nextNull<Any>()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun write(writer: JsonWriter, value: Any?) {
        when (value) {
            null -> writer.nullValue()
            is Boolean -> writer.value(value)
            is Number -> writer.value(value)
            is String -> writer.value(value)
            is Map<*, *> -> {
                writer.beginObject()
                value.forEach { (k, v) ->
                    if (k is String) {
                        writer.name(k)
                        write(writer, v)
                    }
                }
                writer.endObject()
            }
            is Collection<*> -> {
                writer.beginArray()
                value.forEach { write(writer, it) }
                writer.endArray()
            }
            is Array<*> -> {
                writer.beginArray()
                value.forEach { write(writer, it) }
                writer.endArray()
            }
            else -> writer.value(value.toString())
        }
    }

    companion object {
        val FACTORY = JsonAdapter.Factory { type, _, _ ->
            if (Types.getRawType(type) == Any::class.java) {
                ObjectJsonAdapter().nullSafe()
            } else {
                null
            }
        }

        fun register(builder: Moshi.Builder): Moshi.Builder =
            builder.add(FACTORY)
    }
}
