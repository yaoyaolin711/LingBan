package com.agent.chat.data.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agent.chat.MainActivity
import com.agent.chat.data.ai.prompt.PromptAssetLoader
import com.agent.chat.data.ai.prompt.PromptComposeRequest
import com.agent.chat.data.ai.prompt.PromptComposer
import com.agent.chat.data.ai.prompt.UserContext
import com.agent.chat.data.care.CareContextBuilder
import com.agent.chat.data.care.DayPeriod
import com.agent.chat.data.care.CareHeuristics
import com.agent.chat.data.care.HomeArrivalDetector
import com.agent.chat.data.provider.AIProvider
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.repository.PersonaRepository
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.data.interaction.InteractionPreferenceStore
import com.agent.chat.domain.model.Message
import com.agent.chat.domain.model.MessageRole
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class ProactiveNudgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun chatSettingsStore(): ChatSettingsStore
        fun chatRepository(): ChatRepository
        fun personaRepository(): PersonaRepository
        fun memoryRepository(): MemoryRepository
        fun providerConfigRepository(): ProviderConfigRepository
        fun aiProvider(): AIProvider
        fun careContextBuilder(): CareContextBuilder
        fun promptComposer(): PromptComposer
        fun promptAssetLoader(): PromptAssetLoader
        fun interactionPreferenceStore(): InteractionPreferenceStore
        fun homeArrivalDetector(): HomeArrivalDetector
        fun proactiveContextCollector(): ProactiveContextCollector
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val settings = deps.chatSettingsStore().get()
        if (!settings.proactiveEnabled) return Result.success()

        val now = System.currentTimeMillis()
        // 主动消息冷却：至少间隔 3 小时，避免刷屏
        if (settings.lastProactiveNudgeAt > 0L &&
            now - settings.lastProactiveNudgeAt < TimeUnit.HOURS.toMillis(3)
        ) {
            return Result.success()
        }

        // 优先检测“到家”事件；该事件不依赖 idleLongEnough
        val arrivalDecision = deps.homeArrivalDetector().detect(now)

        val period = CareHeuristics.dayPeriod(now)
        val idleMs = TimeUnit.HOURS.toMillis(settings.proactiveIdleHours.toLong())
        val idleLongEnough = settings.lastUserActivityAt > 0L &&
            now - settings.lastUserActivityAt >= idleMs

        val (kind, scenarioHint) = arrivalDecision?.let { decision ->
            decision.kind to decision.scenarioHint
        } ?: deps.careContextBuilder().proactiveScenarioHint()

        // 日程提醒可在未完全闲置时触发；时段问候仍要求闲置
        val allowByScenario = if (arrivalDecision != null) {
            true
        } else {
            when {
                kind == "calendar" -> true
                period == DayPeriod.LATE_NIGHT && idleLongEnough -> true
                idleLongEnough -> true
                else -> false
            }
        }
        if (!allowByScenario) return Result.success()

        // 同类型问候一天内不重复（日历除外）
        if (kind != "calendar" &&
            kind == settings.lastProactiveNudgeKind &&
            settings.lastProactiveNudgeAt > 0L &&
            now - settings.lastProactiveNudgeAt < TimeUnit.HOURS.toMillis(18)
        ) {
            return Result.success()
        }

        val conversations = deps.chatRepository().observeConversations().first()
        val target = conversations.firstOrNull() ?: return Result.success()
        val recent = deps.chatRepository().getMessages(target.id).takeLast(16)
        val persona = target.personaId?.let { deps.personaRepository().getPersona(it) }
        val memories = target.personaId
            ?.let {
                deps.memoryRepository().retrieveForPrompt(
                    personaId = it,
                    queryText = "主动关心问候",
                    recentMessages = recent,
                ).memories
            }
            .orEmpty()
        val provider = target.providerConfigId
            ?.let { deps.providerConfigRepository().getConfig(it) }
            ?: deps.providerConfigRepository().getDefaultConfig()
            ?: return Result.success()

        val care = deps.careContextBuilder().build(
            personaId = target.personaId,
            recentMessages = recent,
        )
        val execContext = com.agent.chat.data.ai.tool.ToolExecutionContext(
            personaId = target.personaId,
            conversationId = target.id,
        )
        val senseContext = deps.proactiveContextCollector().collect(execContext)
        val system = buildString {
            append(
                deps.promptComposer().compose(
                    PromptComposeRequest(
                        persona = persona,
                        memories = memories,
                        userContext = UserContext(
                            nickname = settings.userNickname,
                            interest = settings.userInterest,
                            occupation = settings.userOccupation,
                            goal = settings.userGoal,
                        ),
                        conversationHistory = recent,
                        careContext = care,
                        conversationGoal = "主动关心：像真人突然想起对方，发一句短问候",
                        modelName = provider.modelName,
                        providerName = provider.name,
                        conversationId = target.id,
                        baseHumanEnabled = settings.companionStyleEnabled,
                        chatMode = settings.chatMode,
                        rolePlayEnabled = settings.rolePlayEnabled,
                        interactionPreference = deps.interactionPreferenceStore().get(),
                        userMessage = "",
                    ),
                ).systemPrompt,
            )
            if (senseContext.isNotBlank()) {
                append("\n\n")
                append(senseContext)
            }
            append("\n\n")
            val nudgePath = deps.promptAssetLoader().catalog().assets["proactive_nudge"]
                ?: "prompts/proactive_nudge.txt"
            append(
                deps.promptAssetLoader().render(
                    deps.promptAssetLoader().loadAsset(nudgePath),
                    mapOf("scenario" to scenarioHint),
                ).trim(),
            )
        }
        val config = deps.providerConfigRepository().toModelConfig(
            provider,
            temperature = persona?.defaultTemperature ?: 0.85f,
        )
        val text = buildString {
            deps.aiProvider().chatStream(
                messages = listOf(
                    ChatMessage.system(system),
                    ChatMessage.user("（此刻想主动跟用户说一句）"),
                ),
                config = config,
            ).collect { append(it) }
        }.trim().ifBlank { return Result.success() }

        val msg = Message(
            id = "assistant_nudge_$now",
            conversationId = target.id,
            role = MessageRole.ASSISTANT,
            content = text,
            createdAt = now,
        )
        deps.chatRepository().ensureConversationExists(target.id)
        deps.chatRepository().saveMessage(msg)
        deps.chatRepository().touchConversation(target.id)
        deps.chatSettingsStore().markProactiveNudge(kind)

        showNotification(applicationContext, target.id, persona?.name ?: "伙伴", text)
        return Result.success()
    }

    private fun showNotification(context: Context, conversationId: String, title: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "伴侣消息",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val pending = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "proactive_nudge"
        const val CHANNEL_ID = "companion_nudge"
        const val NOTIFICATION_ID = 42
        const val EXTRA_CONVERSATION_ID = "conversation_id"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<ProactiveNudgeWorker>(15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
