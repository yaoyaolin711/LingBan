package com.agent.chat.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationExporter {

    fun buildPlainText(title: String, messages: List<Message>): String {
        val sb = StringBuilder()
        sb.appendLine("# $title")
        sb.appendLine()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        messages.forEach { message ->
            val role = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
            }
            sb.appendLine("[$role] ${formatter.format(Date(message.createdAt))}")
            sb.appendLine(message.content)
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    fun shareText(context: Context, title: String, messages: List<Message>) {
        val text = buildPlainText(title, messages)
        val file = File(context.cacheDir, "exports").apply { mkdirs() }
            .resolve(safeFileName(title) + ".txt")
        file.writeText(text, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出文本"))
    }

    fun shareImage(context: Context, title: String, messages: List<Message>) {
        val bitmap = renderConversationBitmap(title, messages)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = dir.resolve(safeFileName(title) + ".png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出图片"))
    }

    private fun renderConversationBitmap(title: String, messages: List<Message>): Bitmap {
        val width = 1080
        val padding = 48
        val contentWidth = width - padding * 2
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1C1E.toInt()
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5C6370.toInt()
            textSize = 28f
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1C1E.toInt()
            textSize = 36f
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F5F2.toInt() }
        val userBubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2F6F5E.toInt() }
        val assistantBubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val userTextPaint = TextPaint(bodyPaint).apply { color = 0xFFFFFFFF.toInt() }

        data class Block(val role: MessageRole, val layout: StaticLayout, val height: Int)

        val blocks = messages.map { message ->
            val paint = if (message.role == MessageRole.USER) userTextPaint else bodyPaint
            val layout = StaticLayout.Builder.obtain(
                message.content.ifBlank { " " },
                0,
                message.content.ifBlank { " " }.length,
                paint,
                (contentWidth * 0.78f).toInt(),
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
            Block(message.role, layout, layout.height + 48)
        }

        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        var totalHeight = padding + titleLayout.height + 40
        blocks.forEach { totalHeight += it.height + 24 }
        totalHeight += padding

        val bitmap = Bitmap.createBitmap(width, totalHeight.coerceAtLeast(400), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        var y = padding.toFloat()
        canvas.save()
        canvas.translate(padding.toFloat(), y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 16
        canvas.drawText("Agent Chat 导出会话", padding.toFloat(), y + 24, metaPaint)
        y += 48

        blocks.forEach { block ->
            val bubbleWidth = block.layout.width + 40f
            val bubbleHeight = block.layout.height + 36f
            val left = if (block.role == MessageRole.USER) {
                width - padding - bubbleWidth
            } else {
                padding.toFloat()
            }
            val paint = if (block.role == MessageRole.USER) userBubble else assistantBubble
            canvas.drawRoundRect(
                left,
                y,
                left + bubbleWidth,
                y + bubbleHeight,
                28f,
                28f,
                paint,
            )
            canvas.save()
            canvas.translate(left + 20f, y + 18f)
            block.layout.draw(canvas)
            canvas.restore()
            y += bubbleHeight + 24f
        }
        return bitmap
    }

    private fun safeFileName(title: String): String {
        val base = title.ifBlank { "conversation" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(40)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${base}_$stamp"
    }
}
