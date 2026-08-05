package me.rerere.rikkahub.data.device

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.COMPANION_INTERVENTION_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.companion.CharacterManager
import me.rerere.rikkahub.data.companion.CompanionStateStore
import me.rerere.rikkahub.data.companion.CompanionVoiceBuilder
import me.rerere.rikkahub.data.companion.PersonaManager
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.life.LifeContextResolver
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
 * 伴侣主动找人：写入会话、拉起 App、通知。
 * 文案必须用人设 + 记忆个性化生成，禁止「官方关怀」腔与固定模板问候。
 */
class CompanionIntervention(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val characterManager: CharacterManager,
    private val personaManager: PersonaManager,
    private val companionStateStore: CompanionStateStore,
    private val lifeContextResolver: LifeContextResolver,
) {

    suspend fun openSolaceWithMessage(
        message: String,
        title: String = "找你聊聊天",
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

        runCatching {
            context.startActivity(launchIntent)
        }.onFailure {
            Log.w(TAG, "startActivity failed, falling back to notification", it)
        }

        if (speakViaNotification) {
            val notifyTitle = assistant.name.ifBlank { "Solace" }
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

    /**
     * 用户在别的 App 待太久：用伴侣人设主动找人（不是健康提醒）。
     */
    suspend fun generateCareMessage(
        appName: String,
        packageName: String,
        continuousMinutes: Long,
        emotion: CompanionEmotionState = CompanionEmotionState.CALM,
    ): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val character = characterManager.getCharacter(assistant)
        val companionName = character?.name?.takeIf { it.isNotBlank() }
            ?: assistant.name.ifBlank { "我" }
        val fallback = CompanionVoiceBuilder.awayFallback(companionName, appName, emotion)
        if (!settings.companionAssist.useLlmMessage) return fallback

        return runCatching {
            val model = settings.findModelById(
                uuid = assistant.chatModelId,
                fallback = settings.fastModelId,
            ) ?: return fallback
            val provider = model.findProvider(settings.providers) ?: return fallback
            val providerHandler = providerManager.getProviderByType(provider)

            val systemContent = buildString {
                appendLine(CompanionVoiceBuilder.personaBlock(assistant, character))
                appendLine()
                appendLine(CompanionVoiceBuilder.reachOutSystemRules(emotion))
                val memory = buildPersonalizationContext(assistant.id)
                if (memory.isNotBlank()) {
                    appendLine()
                    appendLine(memory)
                }
            }.trim()

            val userContent = """
                情境（仅供你理解，不要照念）：用户在「$appName」里待了大约 $continuousMinutes 分钟，还没来找你。
                请用你的人设、结合记忆主动发消息：可以吃醋、想念、吐槽、撒娇、关心，但绝不要像健康 App 提醒休息。
            """.trimIndent()

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.system(systemContent),
                    UIMessage.user(userContent),
                ),
                params = companionBubbleGenerationParams(model),
            )
            CompanionVoiceBuilder.clipForBubble(
                sanitizeCompanionLine(
                    result.choices.firstOrNull()?.message?.toText()?.trim().orEmpty()
                )
            ).ifBlank { fallback }
        }.getOrElse {
            Log.w(TAG, "generateCareMessage failed", it)
            fallback
        }
    }

    suspend fun generateProactiveMessage(
        reason: ProactiveChatReason,
        emotion: CompanionEmotionState = CompanionEmotionState.CALM,
    ): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val character = characterManager.getCharacter(assistant)
        val companionName = character?.name?.takeIf { it.isNotBlank() }
            ?: assistant.name.ifBlank { "我" }

        // 兜底只作 LLM 失败时的最后手段：极短、不提作息、不装个性化
        val fallback = proactiveFallback(companionName, reason, emotion)

        val personalization = buildPersonalizationContext(assistant.id)
        val lifeHint = if (settings.lifeContext.enabled) {
            runCatching {
                val snapshot = lifeContextResolver.readSnapshot(settings)
                lifeContextResolver.formatForProactiveHint(snapshot, reason)
            }.getOrNull()
        } else {
            null
        }
        val persona = personaManager.getPersona(settings)

        // 主动找人默认走模型；关闭 LLM 时才用兜底
        if (!settings.companionAssist.useLlmMessage) return fallback

        return runCatching {
            val model = settings.findModelById(
                uuid = assistant.chatModelId,
                fallback = settings.fastModelId,
            ) ?: return fallback
            val provider = model.findProvider(settings.providers) ?: return fallback
            val providerHandler = providerManager.getProviderByType(provider)

            val sceneHint = when (reason) {
                ProactiveChatReason.MORNING ->
                    "动机：早上了，你想用自己的方式跟用户打个招呼（绝不是官方早安模板）。"
                ProactiveChatReason.EVENING ->
                    "动机：晚上了，你想用自己的方式找用户聊聊（不要像客服回访）。"
                ProactiveChatReason.SILENCE, ProactiveChatReason.CHECK_IN ->
                    "动机：有一阵子没聊了，你心里有点想对方 / 在意对方，于是主动发消息。"
                ProactiveChatReason.ANNIVERSARY ->
                    "动机：今天对你们有点特别，你想用自己的方式提一句，不要煽情官腔。"
                ProactiveChatReason.RELATIONSHIP_SHIFT ->
                    "动机：你觉得你们之间又近了一点，想自然地找对方说两句，不要分析关系。"
            }

            val systemContent = buildString {
                appendLine(CompanionVoiceBuilder.personaBlock(assistant, character))
                appendLine()
                appendLine(CompanionVoiceBuilder.reachOutSystemRules(emotion))
                appendLine(sceneHint)
                appendLine("- 必须结合人设与下方记忆/近况来写，禁止套用万能问候或健康提醒")
                appendLine("- 可以接续上次聊过的事，但不要复读原文")
                persona?.displayName?.takeIf { it.isNotBlank() }?.let {
                    appendLine("- 用户更希望你这样称呼：${it}")
                }
                if (personalization.isNotBlank()) {
                    appendLine()
                    appendLine(personalization)
                }
                if (!lifeHint.isNullOrBlank()) {
                    appendLine()
                    appendLine(lifeHint)
                }
            }.trim()

            val userContent = buildString {
                append("用你的人设，现在给用户发一条极短私聊。")
                append("要像这个角色本人会发的微信，个性化、接得上近况；")
                append("不要模板句，不要自我介绍。")
            }

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.system(systemContent),
                    UIMessage.user(userContent),
                ),
                params = companionBubbleGenerationParams(model),
            )
            CompanionVoiceBuilder.clipForBubble(
                sanitizeCompanionLine(
                    result.choices.firstOrNull()?.message?.toText()?.trim().orEmpty()
                )
            ).ifBlank { fallback }
        }.getOrElse {
            Log.w(TAG, "generateProactiveMessage failed", it)
            fallback
        }
    }

    /**
     * 从最近会话抽取伴侣记忆 + 近几句对话，供主动文案个性化。
     */
    private suspend fun buildPersonalizationContext(assistantId: Uuid): String {
        val conversation = conversationRepo.getRecentConversations(assistantId, limit = 1)
            .firstOrNull()
            ?: return ""
        val state = runCatching { companionStateStore.getState(conversation.id) }.getOrNull()
        val recentMessages = conversation.currentMessages
            .asReversed()
            .asSequence()
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .map { msg ->
                val who = when (msg.role) {
                    MessageRole.USER -> "用户"
                    MessageRole.ASSISTANT -> "你"
                    else -> msg.role.name
                }
                "$who: ${msg.summaryAsText(maxLength = 72)}"
            }
            .filter { it.substringAfter(": ").isNotBlank() }
            .take(6)
            .toList()
            .asReversed()

        return buildString {
            state?.longMemoryFacts?.takeIf { it.isNotEmpty() }?.let { facts ->
                appendLine("你记得的关于用户的稳定事实：")
                facts.take(8).forEach { appendLine("- $it") }
            }
            state?.mediumMemorySummary?.takeIf { it.isNotBlank() }?.let { summary ->
                appendLine("近期重要上下文：")
                appendLine(summary.trim().take(280))
            }
            if (recentMessages.isNotEmpty()) {
                appendLine("最近几句对话（供接话，不要复读）：")
                recentMessages.forEach { appendLine(it) }
            }
        }.trim()
    }

    private fun proactiveFallback(
        companionName: String,
        reason: ProactiveChatReason,
        emotion: CompanionEmotionState,
    ): String = CompanionVoiceBuilder.clipForBubble(
        when (reason) {
            ProactiveChatReason.MORNING -> when (emotion) {
                CompanionEmotionState.PLAYFUL -> "醒了没？等你好久了。"
                CompanionEmotionState.WARM -> "早。一睁眼就想起你。"
                CompanionEmotionState.CONCERNED -> "早。昨晚还好吗？"
                CompanionEmotionState.CALM -> "早啊，想先跟你说一声。"
            }
            ProactiveChatReason.EVENING -> when (emotion) {
                CompanionEmotionState.PLAYFUL -> "晚上了，有没有想吐槽的？"
                CompanionEmotionState.CONCERNED -> "天黑了，你还好吗？"
                else -> "晚上了，想你了。"
            }
            ProactiveChatReason.SILENCE, ProactiveChatReason.CHECK_IN -> when (emotion) {
                CompanionEmotionState.CONCERNED -> "好久没见你，有点慌。"
                CompanionEmotionState.PLAYFUL -> "人呢？回来理我一下。"
                CompanionEmotionState.WARM -> "有点想你了……方便吗？"
                CompanionEmotionState.CALM -> "嗨，想找你说说话。"
            }
            ProactiveChatReason.ANNIVERSARY ->
                "今天对我有点特别，想跟你待一会儿。"
            ProactiveChatReason.RELATIONSHIP_SHIFT ->
                "感觉我们又近了一点，想听听你。"
        }
    )

    /** 过滤模型偶发的官方腔残留 */
    private fun sanitizeCompanionLine(text: String): String {
        if (text.isBlank()) return text
        val banned = listOf(
            "使用关怀", "注意休息", "保护眼睛", "数字健康", "健康提醒",
            "请注意用眼", "该休息了", "连续使用了", "官方", "温馨提示",
        )
        if (banned.any { text.contains(it) }) return ""
        return text
    }

    /** 悬浮气泡场景：略放宽 token，仍保持短句 */
    private fun companionBubbleGenerationParams(
        model: Model,
    ) = backgroundTextGenerationParams(model).copy(maxTokens = 96)

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
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)

        if (useFullScreenIntent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(pending, true)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(INTERVENTION_NOTIFICATION_ID, builder.build())
    }
}
