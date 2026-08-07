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
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.life.LifeContextResolver
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.backgroundTextGenerationParams
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.utils.sendNotification
import java.time.Instant
import kotlin.uuid.Uuid

private const val TAG = "CompanionIntervention"
private const val INTERVENTION_NOTIFICATION_ID = 3101
private const val RECENT_CONTEXT_MESSAGE_LIMIT = 8

/**
 * 伴侣主动找人：写入**最近会话**、拉起 App、通知。
 * 文案必须用人设 + 记忆 + 近期对话上下文生成，禁止「陌生人式」问候。
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
    private val memoryRepository: me.rerere.rikkahub.data.repository.MemoryRepository,
) {

    suspend fun resolveRecentConversation(assistantId: Uuid): Conversation? {
        return conversationRepo.getRecentConversations(assistantId, limit = 3)
            .firstOrNull { !it.isGroup && it.currentMessages.isNotEmpty() }
            ?: conversationRepo.getRecentConversations(assistantId, limit = 1)
                .firstOrNull { !it.isGroup }
    }

    /**
     * 将主动消息追加到最近会话；无最近会话时才新建。
     * @return 写入的会话 id
     */
    suspend fun openSolaceWithMessage(
        message: String,
        title: String = "找你聊聊天",
        useFullScreenIntent: Boolean = true,
        speakViaNotification: Boolean = true,
        assistantId: Uuid? = null,
    ): Uuid {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getAssistantById(assistantId ?: settings.assistantId)
            ?: settings.getCurrentAssistant()
        val trimmed = message.trim()
        require(trimmed.isNotEmpty()) { "proactive message is empty" }

        val existing = resolveRecentConversation(assistant.id)
        val conversation = if (existing != null) {
            val updated = existing
                .updateCurrentMessages(existing.currentMessages + UIMessage.assistant(trimmed))
                .copy(updateAt = Instant.now())
            conversationRepo.updateConversation(updated)
            updated
        } else {
            val conversationId = Uuid.random()
            val created = Conversation(
                id = conversationId,
                assistantId = assistant.id,
                title = title.ifBlank { assistant.name.ifBlank { "对话" } },
                messageNodes = listOf(UIMessage.assistant(trimmed).toMessageNode()),
                createAt = Instant.now(),
                updateAt = Instant.now(),
            )
            conversationRepo.insertConversation(created)
            created
        }

        context.writeStringPreference("lastConversationId", conversation.id.toString())

        val launchIntent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversation.id.toString())
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
                conversationId = conversation.id,
                message = trimmed,
                notificationTitle = notifyTitle,
                useFullScreenIntent = useFullScreenIntent,
                launchIntent = launchIntent,
            )
        }
        return conversation.id
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
        emotion: CompanionEmotionState = CompanionEmotionState.CALM,
        conversation: Conversation? = null,
    ): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val thread = conversation ?: resolveRecentConversation(assistant.id)
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

            val systemContent = buildReachOutSystemContent(
                assistant = assistant,
                character = character,
                emotion = emotion,
                sceneHint = """
                    情境（仅供理解）：用户在「$appName」里待了大约 $continuousMinutes 分钟。
                    请接着你们最近的聊天自然发一句，可以吃醋、想念、吐槽，不要像健康 App。
                """.trimIndent(),
                conversation = thread,
                reason = ProactiveChatReason.CHECK_IN,
            )

            val userContent = buildContinueThreadUserPrompt(hasThread = thread != null)

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = buildReachOutMessages(systemContent, thread, userContent),
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
        conversation: Conversation? = null,
    ): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val thread = conversation ?: resolveRecentConversation(assistant.id)
        val character = characterManager.getCharacter(assistant)
        val companionName = character?.name?.takeIf { it.isNotBlank() }
            ?: assistant.name.ifBlank { "我" }

        val fallback = proactiveFallback(companionName, reason, emotion, hasThread = thread != null)

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
                    "动机：早上了，接着最近的话题自然说两句，不要官方早安模板。"
                ProactiveChatReason.EVENING ->
                    "动机：晚上了，接着最近的话题聊聊今天，不要客服回访腔。"
                ProactiveChatReason.SILENCE, ProactiveChatReason.CHECK_IN ->
                    "动机：有一阵子没聊了，接着上次聊过的事自然找对方，不要重新自我介绍。"
                ProactiveChatReason.ANNIVERSARY ->
                    "动机：今天对你们有点特别，结合最近对话提一句，不要煽情官腔。"
                ProactiveChatReason.RELATIONSHIP_SHIFT ->
                    "动机：你们更亲近了，结合最近对话自然说两句，不要分析关系。"
            }

            val systemContent = buildReachOutSystemContent(
                assistant = assistant,
                character = character,
                emotion = emotion,
                sceneHint = sceneHint,
                conversation = thread,
                reason = reason,
            )

            val userContent = buildContinueThreadUserPrompt(hasThread = thread != null)

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = buildReachOutMessages(systemContent, thread, userContent),
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

    private suspend fun buildReachOutSystemContent(
        assistant: me.rerere.rikkahub.data.model.Assistant,
        character: me.rerere.rikkahub.data.companion.model.CompanionCharacterCard?,
        emotion: CompanionEmotionState,
        sceneHint: String,
        conversation: Conversation?,
        reason: ProactiveChatReason = ProactiveChatReason.SILENCE,
    ): String {
        val settings = settingsStore.settingsFlow.value
        val personalization = buildPersonalizationContext(assistant, conversation, reason)
        val lifeHint = if (settings.lifeContext.enabled) {
            runCatching {
                val snapshot = lifeContextResolver.readSnapshot(settings)
                lifeContextResolver.formatForProactiveHint(snapshot, ProactiveChatReason.SILENCE)
            }.getOrNull()
        } else {
            null
        }
        val persona = personaManager.getPersona(settings)

        return buildString {
            appendLine(CompanionVoiceBuilder.personaBlock(assistant, character))
            appendLine()
            appendLine(CompanionVoiceBuilder.reachOutSystemRules(emotion))
            appendLine(sceneHint)
            appendLine("- 必须接着下方记忆与最近对话接话，禁止像第一次认识一样打招呼")
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
    }

    private fun buildContinueThreadUserPrompt(hasThread: Boolean): String = buildString {
        if (hasThread) {
            append("你们正在同一条聊天记录里。请根据上方最近对话，")
            append("像接着微信聊天一样发下一条消息。")
            append("不要「你好/早安/在吗」式开场，不要重复刚说过的话。")
        } else {
            append("还没有历史对话。用你的人设发一条极短私聊，")
            append("自然、有个性，但不要模板问候。")
        }
    }

    private fun buildReachOutMessages(
        systemContent: String,
        conversation: Conversation?,
        userContent: String,
    ): List<UIMessage> {
        val history = conversation?.currentMessages
            ?.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            ?.takeLast(RECENT_CONTEXT_MESSAGE_LIMIT)
            ?: emptyList()
        return buildList {
            add(UIMessage.system(systemContent))
            addAll(history)
            add(UIMessage.user(userContent))
        }
    }

    private suspend fun buildPersonalizationContext(
        assistant: me.rerere.rikkahub.data.model.Assistant,
        conversation: Conversation?,
        reason: ProactiveChatReason,
    ): String {
        val assistantId = assistant.id
        val thread = conversation ?: conversationRepo.getRecentConversations(assistantId, limit = 1)
            .firstOrNull { !it.isGroup }
            ?: return ""
        val state = runCatching { companionStateStore.getState(thread.id) }.getOrNull()
        val recentMessages = thread.currentMessages
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

        val recalled = if (assistant.enableMemory) {
            val memoryScope = if (assistant.useGlobalMemory) {
                me.rerere.rikkahub.data.repository.MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistantId.toString()
            }
            val emotionTag = me.rerere.rikkahub.data.repository.emotionTagForProactiveReason(reason.name)
            runCatching {
                memoryRepository.recallForEmotion(memoryScope, emotionTag)
            }.getOrDefault(me.rerere.rikkahub.data.repository.MemoryTurnHints(emptyList()))
        } else {
            me.rerere.rikkahub.data.repository.MemoryTurnHints(emptyList())
        }

        return buildString {
            state?.longMemoryFacts?.takeIf { it.isNotEmpty() }?.let { facts ->
                appendLine("你记得的关于用户的稳定事实：")
                facts.take(8).forEach { appendLine("- $it") }
            }
            state?.mediumMemorySummary?.takeIf { it.isNotBlank() }?.let { summary ->
                appendLine("近期重要上下文：")
                appendLine(summary.trim().take(280))
            }
            if (!recalled.isEmpty) {
                appendLine("与这次主动找人相关的具体记忆（已排序，优先用第一条，不要编造）：")
                recalled.relationLine?.takeIf { it.isNotBlank() }?.let { line ->
                    appendLine("相关关系：$line")
                }
                recalled.memories.forEachIndexed { index, memory ->
                    appendLine("${index + 1}. ${memory.content.take(120)}")
                }
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
        hasThread: Boolean,
    ): String = CompanionVoiceBuilder.clipForBubble(
        when {
            hasThread -> when (emotion) {
                CompanionEmotionState.CONCERNED -> "刚才说到哪了？我有点放不下。"
                CompanionEmotionState.PLAYFUL -> "喂，接着聊啊。"
                CompanionEmotionState.WARM -> "还在吗？想接着刚才的说。"
                CompanionEmotionState.CALM -> "想起刚才聊的，再说两句？"
            }
            reason == ProactiveChatReason.MORNING -> when (emotion) {
                CompanionEmotionState.PLAYFUL -> "醒了没？等你好久了。"
                CompanionEmotionState.WARM -> "早。一睁眼就想起你。"
                CompanionEmotionState.CONCERNED -> "早。昨晚还好吗？"
                CompanionEmotionState.CALM -> "早啊，想先跟你说一声。"
            }
            reason == ProactiveChatReason.EVENING -> when (emotion) {
                CompanionEmotionState.PLAYFUL -> "晚上了，有没有想吐槽的？"
                CompanionEmotionState.CONCERNED -> "天黑了，你还好吗？"
                else -> "晚上了，想你了。"
            }
            else -> when (emotion) {
                CompanionEmotionState.CONCERNED -> "好久没见你，有点慌。"
                CompanionEmotionState.PLAYFUL -> "人呢？回来理我一下。"
                CompanionEmotionState.WARM -> "有点想你了……方便吗？"
                CompanionEmotionState.CALM -> "嗨，想找你说说话。"
            }
        }
    )

    private fun sanitizeCompanionLine(text: String): String {
        if (text.isBlank()) return text
        val banned = listOf(
            "使用关怀", "注意休息", "保护眼睛", "数字健康", "健康提醒",
            "请注意用眼", "该休息了", "连续使用了", "官方", "温馨提示",
            "你好呀", "你好啊", "在吗", "早上好", "晚上好",
        )
        if (banned.any { text.contains(it) }) return ""
        return text
    }

    private fun companionBubbleGenerationParams(model: Model) =
        backgroundTextGenerationParams(model).copy(maxTokens = 96)

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
