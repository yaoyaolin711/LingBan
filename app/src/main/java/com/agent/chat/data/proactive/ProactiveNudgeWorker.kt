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
import com.agent.chat.data.ai.PromptContextInjector
import com.agent.chat.data.provider.AIProvider
import com.agent.chat.data.provider.ChatMessage
import com.agent.chat.data.repository.ChatRepository
import com.agent.chat.data.repository.MemoryRepository
import com.agent.chat.data.repository.PersonaRepository
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.data.settings.ChatSettingsStore
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
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val settings = deps.chatSettingsStore().get()
        if (!settings.proactiveEnabled) return Result.success()

        val idleMs = TimeUnit.HOURS.toMillis(settings.proactiveIdleHours.toLong())
        val last = settings.lastUserActivityAt
        if (last <= 0L || System.currentTimeMillis() - last < idleMs) {
            return Result.success()
        }

        val conversations = deps.chatRepository().observeConversations().first()
        val target = conversations.firstOrNull() ?: return Result.success()
        val persona = target.personaId?.let { deps.personaRepository().getPersona(it) }
        val memories = target.personaId
            ?.let { deps.memoryRepository().getForPrompt(it) }
            .orEmpty()
        val provider = target.providerConfigId
            ?.let { deps.providerConfigRepository().getConfig(it) }
            ?: deps.providerConfigRepository().getDefaultConfig()
            ?: return Result.success()

        val system = buildString {
            append(
                PromptContextInjector.buildSystemPrompt(
                    persona = persona,
                    memories = memories,
                    companionStyleEnabled = settings.companionStyleEnabled,
                    userNickname = settings.userNickname,
                ),
            )
            append("\n\n")
            append(
                """
                【主动消息】用户已经有一段时间没说话了。请像朋友突然想起对方一样，发一句简短自然的关心或闲聊。
                规则：不要提定时/提醒/系统；不要复述工具或数据来源；不要列表；一两句即可；一句一行。
                """.trimIndent(),
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

        val now = System.currentTimeMillis()
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
        // 避免连续轰炸：刷新活动时间
        deps.chatSettingsStore().touchLastUserActivity()

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
