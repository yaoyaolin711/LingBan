package me.rerere.rikkahub.data.agent.capability.vision

import android.content.Context
import android.util.Log
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.cache.LruCache
import me.rerere.common.cache.SingleFileCacheStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.ocr.LanOcrClient
import me.rerere.rikkahub.data.ocr.MlKitScreenOcr
import me.rerere.rikkahub.data.ocr.ScreenOcrResult
import java.io.File
import java.net.URI
import kotlin.time.Duration.Companion.days

/**
 * OCR execution capability used by [VisionCapabilityRouter] for text-only models
 * and by phone-control screen capture when local OCR is required.
 */
interface OcrCapability {
    fun chatOcrStatusMessage(): String

    suspend fun recognizeChatImage(image: UIMessagePart.Image): String

    suspend fun recognizeScreen(
        imageFile: File,
        settings: Settings,
        screenWidth: Int,
        screenHeight: Int,
    ): ScreenOcrResult?
}

class DefaultOcrCapability(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) : OcrCapability {

    companion object {
        private const val TAG = "OcrCapability"
    }

    private val cache by lazy {
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json,
        )
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
            expireAfterWriteMillis = 3.days.inWholeMilliseconds,
        )
    }

    private fun currentSettings(): Settings = settingsStore.settingsFlow.value

    override fun chatOcrStatusMessage(): String {
        val settings = currentSettings()
        return if (settings.ocrUseLanService) {
            "正在通过局域网 OCR 识别图片..."
        } else {
            "正在识别图片..."
        }
    }

    override suspend fun recognizeChatImage(image: UIMessagePart.Image): String = runCatching {
        cache.get(image.url)?.let { cached ->
            Log.i(TAG, "recognizeChatImage: cache hit ${image.url}")
            return cached
        }

        val imageFile = resolveImageFile(image.url)
        val settings = currentSettings()

        // 1) On-device ML Kit (CN+EN) — works without OCR model / LAN setup
        if (imageFile != null) {
            val mlkit = MlKitScreenOcr.recognize(
                imageFile = imageFile,
                screenWidth = 1080,
                screenHeight = 1920,
            )
            if (mlkit != null && mlkit.text.isNotBlank()) {
                Log.i(TAG, "recognizeChatImage: ML Kit ok chars=${mlkit.text.length}")
                val ocrResult = wrapChatOcrResult(mlkit.text)
                cache.put(image.url, ocrResult)
                return ocrResult
            }
        } else {
            Log.w(TAG, "recognizeChatImage: cannot resolve file for ${image.url}")
        }

        // 2) Optional LAN PaddleOCR
        if (imageFile != null && settings.ocrUseLanService && settings.ocrLanServiceUrl.isNotBlank()) {
            val lanResult = LanOcrClient.recognize(
                baseUrl = settings.ocrLanServiceUrl,
                imageFile = imageFile,
            )
            if (lanResult.isSuccess) {
                val ocrResult = wrapChatOcrResult(lanResult.getOrThrow())
                cache.put(image.url, ocrResult)
                return ocrResult
            }
            Log.w(TAG, "recognizeChatImage: LAN OCR failed", lanResult.exceptionOrNull())
            if (!settings.ocrLanFallbackToModel) {
                return "[ERROR, LAN OCR failed: ${lanResult.exceptionOrNull()?.message ?: "unknown error"}]"
            }
        }

        // 3) Configured OCR vision model (optional)
        val model = settings.findModelById(settings.ocrModelId)
            ?: return wrapChatOcrResult(
                "[OCR empty] Local ML Kit found no text. Configure an OCR model in settings, or describe the image."
            )
        val providerSetting = model.findProvider(settings.providers)
            ?: return wrapChatOcrResult(
                "[OCR empty] OCR model provider missing. Configure OCR in settings, or describe the image."
            )
        val provider = providerManager.getProviderByType(providerSetting)
        val result = provider.generateText(
            providerSetting = providerSetting,
            messages = listOf(
                UIMessage.system(settings.ocrPrompt),
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Image(image.url)),
                ),
            ),
            params = TextGenerationParams(
                model = model,
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
            ),
        )
        val content = result.choices[0].message?.toText() ?: "[ERROR, OCR failed]"
        Log.i(TAG, "recognizeChatImage: $content")
        val ocrResult = wrapChatOcrResult(content)
        cache.put(image.url, ocrResult)
        ocrResult
    }.getOrElse {
        "[ERROR, OCR failed: $it]"
    }

    /**
     * Priority: ML Kit (CN+EN) → LAN PaddleOCR → configured OCR vision model.
     */
    override suspend fun recognizeScreen(
        imageFile: File,
        settings: Settings,
        screenWidth: Int,
        screenHeight: Int,
    ): ScreenOcrResult? {
        if (!imageFile.exists()) return null

        val mlkit = MlKitScreenOcr.recognize(imageFile, screenWidth, screenHeight)
        if (mlkit != null && (mlkit.text.isNotBlank() || mlkit.blocks.isNotEmpty())) {
            return mlkit
        }

        if (settings.ocrUseLanService && settings.ocrLanServiceUrl.isNotBlank()) {
            val lan = LanOcrClient.recognize(settings.ocrLanServiceUrl, imageFile)
            if (lan.isSuccess) {
                val text = lan.getOrThrow().trim()
                if (text.isNotBlank()) {
                    return ScreenOcrResult(engine = "lan_paddleocr", text = text, blocks = emptyList())
                }
            } else {
                Log.w(TAG, "recognizeScreen: LAN OCR failed", lan.exceptionOrNull())
                if (!settings.ocrLanFallbackToModel) return null
            }
        }

        val model = settings.findModelById(settings.ocrModelId) ?: return null
        val providerSetting = model.findProvider(settings.providers) ?: return null
        return runCatching {
            val provider = providerManager.getProviderByType(providerSetting)
            val result = provider.generateText(
                providerSetting = providerSetting,
                messages = listOf(
                    UIMessage.system(
                        settings.ocrPrompt.ifBlank {
                            "Extract all visible text from this phone screenshot. Keep reading order. List button labels."
                        },
                    ),
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Image(imageFile.toURI().toString())),
                    ),
                ),
                params = TextGenerationParams(
                    model = model,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                ),
            )
            val text = result.choices.firstOrNull()?.message?.toText()?.trim().orEmpty()
            if (text.isBlank()) null
            else ScreenOcrResult(engine = "ocr_model", text = text, blocks = emptyList())
        }.onFailure {
            Log.w(TAG, "recognizeScreen: model OCR failed", it)
        }.getOrNull()
    }

    private fun parseLocalFile(url: String): File =
        runCatching { File(URI(url)) }
            .getOrElse {
                if (url.startsWith("file://")) {
                    File(url.removePrefix("file://"))
                } else {
                    File(url.removePrefix("file:"))
                }
            }

    /** Resolve chat image URL to a readable local file (file / content / data URI). */
    private fun resolveImageFile(url: String): File? {
        val u = url.trim()
        return when {
            u.startsWith("content:") -> runCatching {
                val tmp = File(context.cacheDir, "ocr_chat_${u.hashCode().toUInt()}.img")
                context.contentResolver.openInputStream(android.net.Uri.parse(u))?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
                tmp.takeIf { it.exists() && it.length() > 0 }
            }.getOrNull()

            u.startsWith("data:image") -> runCatching {
                val b64 = u.substringAfter("base64,", missingDelimiterValue = "")
                if (b64.isBlank()) return null
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val tmp = File(context.cacheDir, "ocr_chat_data_${bytes.contentHashCode()}.img")
                tmp.writeBytes(bytes)
                tmp.takeIf { it.exists() && it.length() > 0 }
            }.getOrNull()

            else -> parseLocalFile(u).takeIf { it.exists() }
        }
    }

    private fun wrapChatOcrResult(content: String): String = """
            <image_file_ocr>
               $content
            </image_file_ocr>
            * The image_file_ocr tag is the OCR / description of an image the user uploaded in this chat.
            * You CAN see this image content from the text above. Do NOT say you cannot see images, and do NOT ask the user to screenshot the image via see_screen just to read an uploaded chat image.
        """.trimIndent()
}
