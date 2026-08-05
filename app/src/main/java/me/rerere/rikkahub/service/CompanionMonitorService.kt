package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.rikkahub.COMPANION_MONITOR_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.companion.CompanionSoftActions
import me.rerere.rikkahub.data.companion.ProactiveTriggerManager
import me.rerere.rikkahub.data.companion.policy.CompanionEmotionResolver
import me.rerere.rikkahub.data.companion.policy.CompanionProactivePolicy
import me.rerere.rikkahub.data.companion.policy.ProactiveAction
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.needsCompanionForegroundService
import me.rerere.rikkahub.data.device.CompanionAssistSetting
import me.rerere.rikkahub.data.device.ProactiveChatReason
import me.rerere.rikkahub.data.device.UsageStatsQuery
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import org.koin.android.ext.android.inject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val TAG = "CompanionMonitor"

/**
 * 前台服务: 使用关怀 + 定时主动 + 情绪/关系建议。
 *
 * 决策走 [CompanionProactivePolicy]，执行走 [CompanionSoftActions]
 *（不跑 LLM tool-loop）。
 */
class CompanionMonitorService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.COMPANION_MONITOR_START"
        /** User tapped Stop in notification — disable features and stop */
        const val ACTION_STOP = "me.rerere.rikkahub.action.COMPANION_MONITOR_STOP"
        /** Stop service only without mutating settings */
        const val ACTION_FORCE_STOP = "me.rerere.rikkahub.action.COMPANION_MONITOR_FORCE_STOP"
        const val NOTIFICATION_ID = 3001
        private const val PREFS_NAME = "companion_monitor_state"
        private const val PREFS_USAGE_PREFIX = "usage_last_"
        /** Morning/evening catch-up window after the scheduled hour (hours) */
        private const val GREETING_CATCHUP_HOURS = 2

        fun start(context: Context) {
            val intent = Intent(context, CompanionMonitorService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        /** 仅停止服务，不改设置（供 syncWithSettings 使用） */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, CompanionMonitorService::class.java))
            }.onFailure {
                Log.w(TAG, "stopService failed, try ACTION_FORCE_STOP", it)
                runCatching {
                    // Must NOT use ACTION_STOP — that path clears user settings
                    context.startService(
                        Intent(context, CompanionMonitorService::class.java).apply {
                            action = ACTION_FORCE_STOP
                        }
                    )
                }
            }
        }

        fun syncWithSettings(context: Context, settings: Settings) {
            if (settings.needsCompanionForegroundService()) {
                start(context)
            } else {
                stop(context)
            }
        }

        @Deprecated("Use syncWithSettings(context, settings)")
        fun syncWithSettings(context: Context, assist: CompanionAssistSetting) {
            if (assist.monitorEnabled || assist.proactiveChatEnabled) start(context) else stop(context)
        }
    }

    private val settingsStore: SettingsStore by inject()
    private val softActions: CompanionSoftActions by inject()
    private val conversationRepo: ConversationRepository by inject()
    private val emotionResolver: CompanionEmotionResolver by inject()
    private val proactiveTriggerManager: ProactiveTriggerManager by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FORCE_STOP -> {
                monitorJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP -> {
                serviceScope.launch {
                    settingsStore.update { current ->
                        current.copy(
                            companionAssist = current.companionAssist.copy(
                                monitorEnabled = false,
                                proactiveChatEnabled = false,
                            ),
                            assistants = current.assistants.map {
                                it.copy(proactiveChatEnabled = false)
                            },
                        )
                    }
                }
                monitorJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                if (!startForegroundCompat()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startMonitoring()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        serviceScope.cancel()
    }

    private fun startForegroundCompat(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildMonitorNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildMonitorNotification())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            false
        }
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            launch {
                settingsStore.settingsFlow
                    .map { it.needsCompanionForegroundService() }
                    .distinctUntilChanged()
                    .collect { needed ->
                        if (!needed) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            updateNotification()
                        }
                    }
            }
            while (isActive) {
                val settings = settingsStore.settingsFlow.value
                val assist = settings.companionAssist
                if (!settings.needsCompanionForegroundService()) break
                if (assist.monitorEnabled) {
                    runCatching { tickUsageCare(assist, settings) }
                        .onFailure { Log.w(TAG, "usage care tick failed", it) }
                }
                val proactiveAssistants = settings.assistants.filter { it.proactiveChatEnabled }
                val legacyGlobal = assist.proactiveChatEnabled
                if (proactiveAssistants.isNotEmpty() || legacyGlobal) {
                    runCatching { tickProactiveChat(assist, settings) }
                        .onFailure { Log.w(TAG, "proactive tick failed", it) }
                    runCatching { tickEmotionSuggestion(assist, settings) }
                        .onFailure { Log.w(TAG, "emotion suggestion tick failed", it) }
                }
                delay(assist.effectivePollIntervalSeconds() * 1000L)
            }
        }
    }

    private suspend fun tickUsageCare(assist: CompanionAssistSetting, settings: Settings) {
        if (!hasUsageStatsPermission()) {
            Log.d(TAG, "skip usage care: no usage stats permission")
            return
        }
        if (!canActToday(assist)) {
            Log.d(TAG, "skip usage care: daily limit")
            return
        }
        val foreground = UsageStatsQuery.getForegroundApp(this) ?: return
        val packages = assist.monitoredPackages.map { it.trim() }.filter { it.isNotEmpty() }
        if (packages.isNotEmpty() && foreground.packageName !in packages) return

        val thresholdMs = assist.effectiveThresholdMinutes() * 60_000L
        if (foreground.continuousMs < thresholdMs) return

        val now = System.currentTimeMillis()
        val cooldownMs = assist.effectiveCooldownMinutes() * 60_000L
        val last = prefs.getLong(PREFS_USAGE_PREFIX + foreground.packageName, 0L)
        if (now - last < cooldownMs) return

        prefs.edit().putLong(PREFS_USAGE_PREFIX + foreground.packageName, now).apply()
        Log.i(TAG, "usage care: ${foreground.packageName} ${foreground.continuousMinutes}m")

        val assistant = settings.getCurrentAssistant()
        val emotion = emotionResolver.resolveForAssistant(assistant.id)
        val hour = LocalTime.now(ZoneId.systemDefault()).hour
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = emotion,
            actionLevel = assistant.companionActionLevel,
            appName = foreground.appName,
            packageName = foreground.packageName,
            continuousMinutes = foreground.continuousMinutes,
            hourOfDay = hour,
            quietHourStart = assist.quietHourStart,
            quietHourEnd = assist.quietHourEnd,
            severeContinuousMinutes = assist.effectiveSevereContinuousMinutes(),
        )
        softActions.execute(action)
        markProactiveConsumed()
    }

    private suspend fun tickProactiveChat(assist: CompanionAssistSetting, settings: Settings) {
        if (!canActToday(assist)) return
        val now = System.currentTimeMillis()
        val cooldownMs = assist.effectiveProactiveCooldownMinutes() * 60_000L
        if (now - assist.lastProactiveAtEpochMs < cooldownMs) return

        val targetAssistant = settings.getCurrentAssistant()
            .takeIf { it.proactiveChatEnabled || assist.proactiveChatEnabled }
            ?: settings.assistants.firstOrNull { it.proactiveChatEnabled }
            ?: return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).toString()
        val hour = LocalTime.now(zone).hour

        val resolvedReason = when {
            assist.morningGreetingEnabled &&
                isWithinGreetingWindow(hour, assist.morningHour.coerceIn(0, 23)) &&
                assist.lastMorningDate != today -> ProactiveChatReason.MORNING

            assist.eveningGreetingEnabled &&
                isWithinGreetingWindow(hour, assist.eveningHour.coerceIn(0, 23)) &&
                assist.lastEveningDate != today -> ProactiveChatReason.EVENING

            else -> {
                val lastChat = conversationRepo.getRecentConversations(targetAssistant.id, limit = 1)
                    .firstOrNull()
                val lastAt = lastChat?.updateAt?.toEpochMilli() ?: 0L
                if (lastAt == 0L || now - lastAt >= assist.effectiveSilenceThresholdMs()) {
                    ProactiveChatReason.SILENCE
                } else {
                    null
                }
            }
        } ?: return

        Log.i(TAG, "proactive chat: $resolvedReason assistant=${targetAssistant.name}")
        val emotion = emotionResolver.resolveForAssistant(targetAssistant.id)
        val action = CompanionProactivePolicy.decideProactiveChat(
            reason = resolvedReason,
            emotion = emotion,
            actionLevel = targetAssistant.companionActionLevel,
            hourOfDay = hour,
            quietHourStart = assist.quietHourStart,
            quietHourEnd = assist.quietHourEnd,
        )
        softActions.execute(action)

        settingsStore.update { current ->
            val ca = bumpDayCount(current.companionAssist, today)
            current.copy(
                companionAssist = ca.copy(
                    lastProactiveAtEpochMs = now,
                    lastMorningDate = if (resolvedReason == ProactiveChatReason.MORNING) today else ca.lastMorningDate,
                    lastEveningDate = if (resolvedReason == ProactiveChatReason.EVENING) today else ca.lastEveningDate,
                )
            )
        }
    }

    /**
     * 情绪/关系/纪念日建议（[ProactiveTriggerManager]），与早安晚间沉默共用冷却与每日上限。
     */
    private suspend fun tickEmotionSuggestion(assist: CompanionAssistSetting, settings: Settings) {
        if (!canActToday(assist)) return
        val now = System.currentTimeMillis()
        val cooldownMs = assist.effectiveProactiveCooldownMinutes() * 60_000L
        if (now - assist.lastProactiveAtEpochMs < cooldownMs) return

        val targetAssistant = settings.getCurrentAssistant()
            .takeIf { it.enableCompanion && (it.proactiveChatEnabled || assist.proactiveChatEnabled) }
            ?: settings.assistants.firstOrNull {
                it.enableCompanion && it.proactiveChatEnabled
            }
            ?: return

        val ctx = emotionResolver.resolveContext(targetAssistant.id)
        val conversation = ctx.conversation ?: return
        val state = ctx.state ?: return

        val suggestion = proactiveTriggerManager.evaluateSuggestion(
            settings = settings,
            conversation = conversation,
            state = state,
        ) ?: return

        // 与 SILENCE 重复时跳过：普通沉默已由 tickProactiveChat 处理
        if (suggestion.type == "check_in" &&
            suggestion.reason == "user_inactive"
        ) {
            return
        }

        val hour = LocalTime.now(ZoneId.systemDefault()).hour
        Log.i(TAG, "emotion suggestion: ${suggestion.type} ${suggestion.reason}")
        val action = CompanionProactivePolicy.decideFromSuggestion(
            suggestion = suggestion,
            emotion = ctx.emotion,
            actionLevel = targetAssistant.companionActionLevel,
            hourOfDay = hour,
            quietHourStart = assist.quietHourStart,
            quietHourEnd = assist.quietHourEnd,
        )
        if (action is ProactiveAction.None) return
        softActions.execute(action)

        settingsStore.update { current ->
            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            val ca = bumpDayCount(current.companionAssist, today)
            current.copy(
                companionAssist = ca.copy(lastProactiveAtEpochMs = now)
            )
        }
    }

    private fun canActToday(assist: CompanionAssistSetting): Boolean {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val count = if (assist.proactiveDayKey == today) assist.proactiveDayCount else 0
        return count < assist.maxProactivePerDay.coerceAtLeast(1)
    }

    /** Exact hour or within catch-up window if the service was down at the scheduled hour. */
    private fun isWithinGreetingWindow(currentHour: Int, targetHour: Int): Boolean {
        if (currentHour == targetHour) return true
        val end = (targetHour + GREETING_CATCHUP_HOURS).coerceAtMost(23)
        return currentHour in (targetHour + 1)..end
    }

    private suspend fun markProactiveConsumed() {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        settingsStore.update { current ->
            current.copy(companionAssist = bumpDayCount(current.companionAssist, today))
        }
    }

    private fun bumpDayCount(assist: CompanionAssistSetting, today: String): CompanionAssistSetting {
        val count = if (assist.proactiveDayKey == today) assist.proactiveDayCount + 1 else 1
        return assist.copy(proactiveDayKey = today, proactiveDayCount = count)
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildMonitorNotification())
    }

    private fun buildMonitorNotification(): android.app.Notification {
        val settings = settingsStore.settingsFlow.value
        val assist = settings.companionAssist
        val parts = buildList {
            if (assist.monitorEnabled) add("伴侣找人")
            if (assist.proactiveChatEnabled || settings.assistants.any { it.proactiveChatEnabled }) {
                add("主动聊天")
            }
            if (settings.assistants.any { it.enableCompanion }) add("陪伴模式")
            if (assist.companionTestMode) add("快测")
        }
        val subtitle = if (parts.isEmpty()) {
            "伴侣服务运行中"
        } else {
            parts.joinToString(" · ") + " 已开启"
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, CompanionMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, COMPANION_MONITOR_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("Solace 伴侣")
            .setContentText(subtitle)
            .setOngoing(true)
            .setContentIntent(pending)
            .setSilent(true)
            .addAction(0, "停止", stopPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
