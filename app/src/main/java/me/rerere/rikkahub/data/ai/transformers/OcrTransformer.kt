package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.cache.LruCache
import me.rerere.common.cache.SingleFileCacheStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.ocr.LanOcrClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.net.URI
import kotlin.time.Duration.Companion.days

private const val TAG = "OcrTransformer"

object OcrTransformer : InputMessageTransformer, KoinComponent {
    private val cache by lazy {
        val context = get<Context>()
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json
        )
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
            expireAfterWriteMillis = 3.days.inWholeMilliseconds,
        )
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) {
            return messages
        }

        val hasImages = messages.any { message ->
            message.parts.any { it is UIMessagePart.Image && it.url.startsWith("file:") }
        }
        if (!hasImages) return messages

        return withContext(Dispatchers.IO) {
            try {
                val settings = get<SettingsStore>().settingsFlow.value
                ctx.processingStatus.value = if (settings.ocrUseLanService) {
                    "正在通过局域网 OCR 识别图片..."
                } else {
                    "正在识别图片..."
                }
                messages.map { message ->
                    message.copy(
                        parts = message.parts.map { part ->
                            when {
                                part is UIMessagePart.Image && part.url.startsWith("file:") -> {
                                    UIMessagePart.Text(performOcr(part))
                                }

                                else -> part
                            }
                        }
                    )
                }
            } finally {
                ctx.processingStatus.value = null
            }
        }
    }

    suspend fun performOcr(part: UIMessagePart.Image): String = runCatching {
        // Check cache first
        cache.get(part.url)?.let { cachedResult ->
            Log.i(TAG, "performOcr: Using cached result for ${part.url}")
            return cachedResult
        }

        val settings = get<SettingsStore>().settingsFlow.value
        if (settings.ocrUseLanService && settings.ocrLanServiceUrl.isNotBlank()) {
            val imageFile = parseLocalFile(part.url)
            val lanResult = LanOcrClient.recognize(
                baseUrl = settings.ocrLanServiceUrl,
                imageFile = imageFile
            )
            if (lanResult.isSuccess) {
                val ocrResult = wrapOcrResult(lanResult.getOrThrow())
                cache.put(part.url, ocrResult)
                return ocrResult
            }
            Log.w(TAG, "performOcr: LAN OCR failed", lanResult.exceptionOrNull())
            if (!settings.ocrLanFallbackToModel) {
                return "[ERROR, LAN OCR failed: ${lanResult.exceptionOrNull()?.message ?: "unknown error"}]"
            }
        }

        val model = settings.findModelById(settings.ocrModelId) ?: return "[Image]"
        val providerSetting = model.findProvider(settings.providers) ?: return "[Image]"
        val provider = get<ProviderManager>().getProviderByType(providerSetting)
        val result = provider.generateText(
            providerSetting = providerSetting,
            messages = listOf(
                UIMessage.system(settings.ocrPrompt),
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Image(part.url))
                )
            ),
            params = TextGenerationParams(
                model = model,
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
            ),
        )
        val content = result.choices[0].message?.toText() ?: "[ERROR, OCR failed]"
        Log.i(TAG, "performOcr: $content")
        val ocrResult = wrapOcrResult(content)

        // Cache the result
        cache.put(part.url, ocrResult)
        return ocrResult
    }.getOrElse {
        "[ERROR, OCR failed: $it]"
    }

    private fun parseLocalFile(url: String): File {
        return runCatching { File(URI(url)) }
            .getOrElse {
                if (url.startsWith("file://")) {
                    File(url.removePrefix("file://"))
                } else {
                    File(url.removePrefix("file:"))
                }
            }
    }

    private fun wrapOcrResult(content: String): String = """
            <image_file_ocr>
               $content
            </image_file_ocr>
            * The image_file_ocr tag contains a description of an image that the user uploaded to you, not the user's prompt.
        """.trimIndent()
}
