package me.rerere.rikkahub.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "MlKitScreenOcr"

data class ScreenOcrBlock(
    val text: String,
    val x: Int,
    val y: Int,
    val bounds: String,
)

data class ScreenOcrResult(
    val engine: String,
    val text: String,
    val blocks: List<ScreenOcrBlock>,
)

/**
 * On-device ML Kit OCR (Chinese + Latin). Returns text blocks with screen-mapped centers.
 */
object MlKitScreenOcr {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(
        imageFile: File,
        screenWidth: Int,
        screenHeight: Int,
    ): ScreenOcrResult? {
        if (!imageFile.exists()) return null
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return null
        return try {
            recognizeBitmap(bitmap, screenWidth, screenHeight)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun recognizeBitmap(
        bitmap: Bitmap,
        screenWidth: Int,
        screenHeight: Int,
    ): ScreenOcrResult? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText: Text = suspendCancellableCoroutine { cont ->
                val task = recognizer.process(image)
                task.addOnSuccessListener { text ->
                    if (cont.isActive) cont.resume(text)
                }
                task.addOnFailureListener { e ->
                    Log.w(TAG, "ML Kit OCR failed", e)
                    if (cont.isActive) cont.resumeWith(Result.failure(e))
                }
            }

            val scaleX = if (bitmap.width > 0) screenWidth.toFloat() / bitmap.width else 1f
            val scaleY = if (bitmap.height > 0) screenHeight.toFloat() / bitmap.height else 1f

            fun mapBox(text: String, box: Rect): ScreenOcrBlock {
                val left = (box.left * scaleX).toInt()
                val top = (box.top * scaleY).toInt()
                val right = (box.right * scaleX).toInt()
                val bottom = (box.bottom * scaleY).toInt()
                return ScreenOcrBlock(
                    text = text.take(80),
                    x = (left + right) / 2,
                    y = (top + bottom) / 2,
                    bounds = "$left,$top,$right,$bottom",
                )
            }

            val blocks = visionText.textBlocks.mapNotNull { block ->
                val t = block.text.trim()
                if (t.isEmpty()) return@mapNotNull null
                val box = block.boundingBox ?: return@mapNotNull null
                mapBox(t, box)
            }

            // Prefer line-level when blocks are too coarse
            val lines = visionText.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val t = line.text.trim()
                if (t.isEmpty()) return@mapNotNull null
                val box = line.boundingBox ?: return@mapNotNull null
                mapBox(t, box)
            }

            val useBlocks = if (lines.size >= blocks.size && lines.isNotEmpty()) lines else blocks
            ScreenOcrResult(
                engine = "mlkit_zh_en",
                text = visionText.text.trim(),
                blocks = useBlocks.take(80),
            )
        } catch (e: Exception) {
            Log.w(TAG, "recognizeBitmap failed", e)
            null
        }
    }
}
