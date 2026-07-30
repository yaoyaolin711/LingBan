package me.rerere.rikkahub.data.device

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.COMPANION_INTERVENTION_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.backgroundTextGenerationParams
import me.rerere.rikkahub.utils.sendNotification
import java.time.Instant
import kotlin.uuid.Uuid

private const val TAG = "CompanionIntervention"
private const val INTERVENTION_NOTIFICATION_ID = 3101

/**
 * 把 Solace 拉到前台、写入关怀会话、可选全屏通知.
 */
class CompanionIntervention(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) {

    suspend fun openSolaceWithMessage(
        message: String,
        title: String = "使用关怀",
        useFullScreenIntent: Boolean = true,
        speakViaNotification: Boolean = true,
    ): Uuid {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val conversationId = Uuid.random()
        val conversation = Conversation(
            id = conversationId,
            assistantId = settings.assistantId,
            title = title,
            messageNodes = listOf(
                UIMessage.assistant(message).toMessageNode()
            ),
            createAt = Instant.now(),
            updateAt = Instant.now(),
        )
        conversationRepo.insertConversation(conversation)

        val launchIntent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
            putExtra("companion_intervention", true)
        }

        // FGS / 工具调用侧直接尝试拉起 Activity
        runCatching {
            context.startActivity(launchIntent)
        }.onFailure {
            Log.w(TAG, "startActivity failed, falling back to notification", it)
        }

        if (speakViaNotification) {
            val notifyTitle = assistant.name.ifBlank { "Solace" }.let { "$it · 该休息一下了" }
            postInterventionNotification(
                conversationId = conversationId,
                message = message,
                notificationTitle = notifyTitle,
                useFullScreenIntent = useFullScreenIntent,
                launchIntent = launchIntent,
            )
        }
        return conversationId
    }

    fun notifyUser(title: String, content: String, conversationId: Uuid? = null) {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (conversationId != null) {
                putExtra("conversationId", conversationId.toString())
            }
        }
        val pending = PendingIntent.getActivity(
            context,
            conversationId?.hashCode() ?: title.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        context.sendNotification(
            channelId = COMPANION_INTERVENTION_NOTIFICATION_CHANNEL_ID,
            notificationId = INTERVENTION_NOTIFICATION_ID + 1
        ) {
            this.title = title
            this.content = content
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_REMINDER
            contentIntent = pending
        }
    }

    suspend fun generateCareMessage(
        appName: String,
        packageName: String,
        continuousMinutes: Long,
    ): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val assistantName = assistant.name.ifBlank { "Solace" }
        val fallback =
            "你已经在「$appName」上连续待了大约 ${continuousMinutes} 分钟了。休息一下眼睛和身体吧，我在这里陪你。"
        if (!settings.companionAssist.useLlmMessage) return fallback

        return runCatching {
            // 优先用助手绑定模型，保证语气与日常聊天一致；否则回退快速模型
            val model = settings.findModelById(
                uuid = assistant.chatModelId,
                fallback = settings.fastModelId,
            ) ?: return fallback
            val provider = model.findProvider(settings.providers) ?: return fallback
            val providerHandler = providerManager.getProviderByType(provider)

            // 把用户设定的助手人设作为 system，再附加「使用关怀」任务约束
            val persona = assistant.systemPrompt.trim()
            val systemContent = buildString {
                if (persona.isNotEmpty()) {
                    appendLine(persona)
                    appendLine()
                } else {
                    appendLine("你是 $assistantName，用户的 AI 伴侣。")
                    appendLine()
                }
                appendLine("【使用关怀任务】")
                appendLine("现在需要你以自己的人设与口吻，提醒用户注意休息。")
                appendLine("要求：保持人设一致；简短（1-3 句）；不要列表；不要 emoji；只输出要对用户说的那几句话。")
            }.trim()

            val userContent = """
                用户刚才在应用「$appName」（包名 $packageName）连续使用了约 $continuousMinutes 分钟，还没回来找你。
                请用你自己的性格说几句关心/提醒休息的话。
            """.trimIndent()

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.system(systemContent),
                    UIMessage.user(userContent),
                ),
                params = backgroundTextGenerationParams(model),
            )
            result.choices.firstOrNull()?.message?.toText()?.trim().orEmpty()
                .ifBlank { fallback }
        }.getOrElse {
            Log.w(TAG, "generateCareMessage failed", it)
            fallback
        }
    }

    suspend fun generateProactiveMessage(reason: ProactiveChatReason): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val assistantName = assistant.name.ifBlank { "Solace" }
        val fallback = when (reason) {
            ProactiveChatReason.MORNING -> "早安。新的一天开始了，我想起你，就来打个招呼。"
            ProactiveChatReason.EVENING -> "晚上了，今天过得怎么样？有空的话跟我说说话也很好。"
            ProactiveChatReason.SILENCE -> "有一会儿没聊了，我有点想你。你现在方便吗？"
        }
        if (!settings.companionAssist.useLlmMessage) return fallback

        return runCatching {
            val model = settings.findModelById(
                uuid = assistant.chatModelId,
                fallback = settings.fastModelId,
            ) ?: return fallback
            val provider = model.findProvider(settings.providers) ?: return fallback
            val providerHandler = providerManager.getProviderByType(provider)

            val persona = assistant.systemPrompt.trim()
            val reasonHint = when (reason) {
                ProactiveChatReason.MORNING -> "现在是早上，你想主动跟用户说一声早安/开启新一天的话。"
                ProactiveChatReason.EVENING -> "现在是晚上，你想主动关心用户今天过得怎样，或道一声晚安。"
                ProactiveChatReason.SILENCE -> "已经有一段时间没和用户聊天了，你想主动找对方说说话、问问近况。"
            }
            val systemContent = buildString {
                if (persona.isNotEmpty()) {
                    appendLine(persona)
                    appendLine()
                } else {
                    appendLine("你是 $assistantName，用户的 AI 伴侣。")
                    appendLine()
                }
                appendLine("【主动聊天任务】")
                appendLine("你要像真人一样主动发起一段对话开场白。")
                appendLine(reasonHint)
                appendLine("要求：严格保持人设与口吻；自然、像私聊；1-3 句；不要列表；不要 emoji；不要自我介绍成 AI；只输出要对用户说的话。")
            }.trim()

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.system(systemContent),
                    UIMessage.user("请现在主动找用户说几句话。"),
                ),
                params = backgroundTextGenerationParams(model),
            )
            result.choices.firstOrNull()?.message?.toText()?.trim().orEmpty()
                .ifBlank { fallback }
        }.getOrElse {
            Log.w(TAG, "generateProactiveMessage failed", it)
            fallback
        }
    }

    private fun postInterventionNotification(
        conversationId: Uuid,
        message: String,
        notificationTitle: String,
        useFullScreenIntent: Boolean,
        launchIntent: Intent,
    ) {
        val pending = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, COMPANION_INTERVENTION_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(notificationTitle)
            .setContentText(message.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)

        if (useFullScreenIntent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(pending, true)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(INTERVENTION_NOTIFICATION_ID, builder.build())
    }
}
