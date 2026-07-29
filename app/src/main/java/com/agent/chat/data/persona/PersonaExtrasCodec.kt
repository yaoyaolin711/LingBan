package com.agent.chat.data.persona

import com.agent.chat.domain.model.LorebookEntry
import com.agent.chat.domain.model.OutputRegex
import com.agent.chat.domain.model.PresetMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonaExtrasCodec @Inject constructor(
    moshi: Moshi,
) {
    private val presetType = Types.newParameterizedType(List::class.java, PresetMessage::class.java)
    private val loreType = Types.newParameterizedType(List::class.java, LorebookEntry::class.java)
    private val regexType = Types.newParameterizedType(List::class.java, OutputRegex::class.java)

    private val presetAdapter = moshi.adapter<List<PresetMessage>>(presetType)
    private val loreAdapter = moshi.adapter<List<LorebookEntry>>(loreType)
    private val regexAdapter = moshi.adapter<List<OutputRegex>>(regexType)

    fun encodePresets(list: List<PresetMessage>): String =
        presetAdapter.toJson(list)

    fun decodePresets(json: String?): List<PresetMessage> =
        runCatching { presetAdapter.fromJson(json.orEmpty().ifBlank { "[]" }) }
            .getOrNull()
            .orEmpty()

    fun encodeLorebook(list: List<LorebookEntry>): String =
        loreAdapter.toJson(list)

    fun decodeLorebook(json: String?): List<LorebookEntry> =
        runCatching { loreAdapter.fromJson(json.orEmpty().ifBlank { "[]" }) }
            .getOrNull()
            .orEmpty()

    fun encodeRegexes(list: List<OutputRegex>): String =
        regexAdapter.toJson(list)

    fun decodeRegexes(json: String?): List<OutputRegex> =
        runCatching { regexAdapter.fromJson(json.orEmpty().ifBlank { "[]" }) }
            .getOrNull()
            .orEmpty()
}
