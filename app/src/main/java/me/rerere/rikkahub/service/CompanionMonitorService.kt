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
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.device.CompanionAssistSetting
import me.rerere.rikkahub.data.device.CompanionIntervention
import me.rerere.rikkahub.data.device.ProactiveChatReason
import me.rerere.rikkahub.data.device.UsageStatsQuery
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import org.koin.android.ext.android.inject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CompanionMonitor"

/**
 * 前台服务: 使用关怀监测 + 人设主动聊天调度.
 */
class CompanionMonitorService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.COMPANION_MONITOR_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.COMPANION_MONITOR_STOP"
        const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            val intent = Intent(context, CompanionMonitorService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CompanionMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun syncWithSettings(context: Context, assist: CompanionAssistSetting) {
            if (assist.needsForegroundService) start(context) else stop(context)
        }
    }

    private val settingsStore: SettingsStore by inject()
    private val companionIntervention: CompanionIntervention by inject()
    private val conversationRepo: ConversationRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private val lastInterventionAt = ConcurrentHashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    settingsStore.update { current ->
                        current.copy(
                            companionAssist = current.companionAssist.copy(
                                monitorEnabled = false,
                                proactiveChatEnabled = false,
                            )
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
                    .map { it.companionAssist.needsForegroundService }
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
                val assist = settingsStore.settingsFlow.value.companionAssist
                if (!assist.needsForegroundService) break
                if (assist.monitorEnabled) {
                    runCatching {
                        tickUsageCare(assist.thresholdMinutes, assist.cooldownMinutes, assist.monitoredPackages)
                    }.onFailure { Log.w(TAG, "usage care tick failed", it) }
                }
                if (assist.proactiveChatEnabled) {
                    runCatching { tickProactiveChat(assist) }
                        .onFailure { Log.w(TAG, "proactive tick failed", it) }
                }
                delay((assist.pollIntervalSeconds.coerceIn(15, 300) * 1000L))
            }
        }
    }

    private suspend fun tickUsageCare(
        thresholdMinutes: Int,
        cooldownMinutes: Int,
        monitoredPackages: List<String>,
    ) {
        if (!hasUsageStatsPermission()) {
            Log.d(TAG, "skip usage care: no usage stats permission")
            return
        }
        val foreground = UsageStatsQuery.getForegroundApp(this) ?: return
        val packages = monitoredPackages.map { it.trim() }.filter { it.isNotEmpty() }
        if (packages.isNotEmpty() && foreground.packageName !in packages) return

        val thresholdMs = thresholdMinutes.coerceAtLeast(1) * 60_000L
        if (foreground.continuousMs < thresholdMs) return

        val now = System.currentTimeMillis()
        val cooldownMs = cooldownMinutes.coerceAtLeast(1) * 60_000L
        val last = lastInterventionAt[foreground.packageName] ?: 0L
        if (now - last < cooldownMs) return

        lastInterventionAt[foreground.packageName] = now
        Log.i(TAG, "usage care: ${foreground.packageName} ${foreground.continuousMinutes}m")
        val message = companionIntervention.generateCareMessage(
            appName = foreground.appName,
            packageName = foreground.packageName,
            continuousMinutes = foreground.continuousMinutes,
        )
        companionIntervention.openSolaceWithMessage(
            message = message,
            title = "使用关怀 · ${foreground.appName}",
            useFullScreenIntent = true,
        )
    }

    private suspend fun tickProactiveChat(assist: CompanionAssistSetting) {
        val now = System.currentTimeMillis()
        val cooldownMs = assist.proactiveCooldownMinutes.coerceAtLeast(30) * 60_000L
        if (now - assist.lastProactiveAtEpochMs < cooldownMs) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).toString()
        val hour = LocalTime.now(zone).hour

        val resolvedReason = when {
            assist.morningGreetingEnabled &&
                hour == assist.morningHour.coerceIn(0, 23) &&
                assist.lastMorningDate != today -> ProactiveChatReason.MORNING

            assist.eveningGreetingEnabled &&
                hour == assist.eveningHour.coerceIn(0, 23) &&
                assist.lastEveningDate != today -> ProactiveChatReason.EVENING

            else -> {
                val silenceMs = assist.silenceHours.coerceAtLeast(1) * 3_600_000L
                val assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id
                val lastChat = conversationRepo.getRecentConversations(assistantId, limit = 1)
                    .firstOrNull()
                val lastAt = lastChat?.updateAt?.toEpochMilli() ?: 0L
                // 从未聊过或沉默过久
                if (lastAt == 0L || now - lastAt >= silenceMs) {
                    ProactiveChatReason.SILENCE
                } else {
                    null
                }
            }
        } ?: return

        Log.i(TAG, "proactive chat: $resolvedReason")
        val message = companionIntervention.generateProactiveMessage(resolvedReason)
        val title = when (resolvedReason) {
            ProactiveChatReason.MORNING -> "早安"
            ProactiveChatReason.EVENING -> "晚间问候"
            ProactiveChatReason.SILENCE -> "想找你聊聊"
        }
        companionIntervention.openSolaceWithMessage(
            message = message,
            title = title,
            useFullScreenIntent = false,
        )
        settingsStore.update { current ->
            val ca = current.companionAssist
            current.copy(
                companionAssist = ca.copy(
                    lastProactiveAtEpochMs = now,
                    lastMorningDate = if (resolvedReason == ProactiveChatReason.MORNING) today else ca.lastMorningDate,
                    lastEveningDate = if (resolvedReason == ProactiveChatReason.EVENING) today else ca.lastEveningDate,
                )
            )
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildMonitorNotification())
    }

    private fun buildMonitorNotification(): android.app.Notification {
        val assist = settingsStore.settingsFlow.value.companionAssist
        val parts = buildList {
            if (assist.monitorEnabled) add("使用关怀")
            if (assist.proactiveChatEnabled) add("主动聊天")
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
