package com.agent.chat.data.provider.network

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
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
        val FACTORY = JsonAdapter.Factory { type, _, moshi ->
            when {
                Types.getRawType(type) == Any::class.java -> ObjectJsonAdapter().nullSafe()
                isStringAnyMap(type) -> MapStringAnyJsonAdapter(
                    moshi.adapter(Any::class.java),
                ).nullSafe()
                else -> null
            }
        }

        private fun isStringAnyMap(type: Type): Boolean {
            if (Types.getRawType(type) != Map::class.java) return false
            val args = (type as? ParameterizedType)?.actualTypeArguments ?: return false
            return args.size == 2 &&
                args[0] == String::class.java &&
                args[1] == Any::class.java
        }

        fun register(builder: Moshi.Builder): Moshi.Builder =
            builder.add(FACTORY)
    }
}

class MapStringAnyJsonAdapter(
    private val anyAdapter: JsonAdapter<Any>,
) : JsonAdapter<Map<String, Any>>() {

    override fun fromJson(reader: JsonReader): Map<String, Any>? {
        if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Map<String, Any>>()
            return null
        }
        reader.beginObject()
        val map = LinkedHashMap<String, Any>()
        while (reader.hasNext()) {
            val key = reader.nextName()
            val value = anyAdapter.fromJson(reader)
            if (value != null) {
                map[key] = value
            }
        }
        reader.endObject()
        return map
    }

    override fun toJson(writer: JsonWriter, value: Map<String, Any>?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        value.forEach { (key, entryValue) ->
            writer.name(key)
            anyAdapter.toJson(writer, entryValue)
        }
        writer.endObject()
    }
}
