package me.rerere.rikkahub.data.life

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.device.ProactiveChatReason
import me.rerere.rikkahub.data.health.HealthConnectRepository
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 汇总过夜休息窗：Health Connect 睡眠优先，否则用屏幕空闲估计。
 */
class LifeContextResolver(
    private val context: Context,
    private val healthConnectRepository: HealthConnectRepository,
) {
    private val mutex = Mutex()
    @Volatile
    private var cached: LifeContextSnapshot? = null
    @Volatile
    private var cachedAtMs: Long = 0L
    @Volatile
    private var cachedEnabled: Boolean = false

    suspend fun readSnapshot(
        settings: Settings,
        forceRefresh: Boolean = false,
    ): LifeContextSnapshot {
        if (!settings.lifeContext.enabled) {
            cached = null
            cachedEnabled = false
            return LifeContextSnapshot.EMPTY
        }

        if (!forceRefresh) {
            val hit = cached
            if (hit != null &&
                cachedEnabled &&
                System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS
            ) {
                return hit
            }
        }

        return mutex.withLock {
            if (!forceRefresh) {
                val hit = cached
                if (hit != null &&
                    cachedEnabled &&
                    System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS
                ) {
                    return@withLock hit
                }
            }

            val snapshot = resolveFresh(settings)
            cached = snapshot
            cachedAtMs = System.currentTimeMillis()
            cachedEnabled = true
            snapshot
        }
    }

    /**
     * 供 Prompt 注入；无有效数据时返回空串（不注入）。
     */
    fun formatForPrompt(snapshot: LifeContextSnapshot?): String {
        if (snapshot == null || !snapshot.isInjectable) return ""
        val zone = ZoneId.systemDefault()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val rest = snapshot.restStart?.atZone(zone)?.format(timeFmt) ?: return ""
        val wake = snapshot.wakeApprox?.atZone(zone)?.format(timeFmt)
        val hours = snapshot.durationMinutes?.let { minutes ->
            String.format(Locale.US, "%.1f", minutes / 60.0)
        }
        val sourceLabel = when (snapshot.source) {
            RestSource.HEALTH_CONNECT -> "health_connect"
            RestSource.PHONE_INACTIVITY -> "phone_inactivity"
            null -> "unknown"
        }
        val confidenceLabel = snapshot.confidence?.name?.lowercase(Locale.US) ?: "medium"
        val restNote = when (snapshot.source) {
            RestSource.HEALTH_CONNECT -> "from Health Connect sleep session"
            RestSource.PHONE_INACTIVITY -> "last foreground phone use"
            null -> "estimated"
        }
        val wakeNote = when (snapshot.source) {
            RestSource.HEALTH_CONNECT -> "sleep session end"
            RestSource.PHONE_INACTIVITY -> "first sustained use"
            null -> "estimated"
        }
        return buildString {
            appendLine("User rest context (estimated, not medical advice):")
            appendLine("- Approx rest start: $rest ($restNote)")
            if (wake != null) appendLine("- Approx wake: $wake ($wakeNote)")
            if (hours != null) appendLine("- Window: ~${hours}h")
            appendLine("- Source: $sourceLabel, confidence: $confidenceLabel")
            append("- Speak gently; do not claim exact sleep; do not lecture.")
        }
    }

    /** 设置页预览 */
    fun formatForUi(snapshot: LifeContextSnapshot?): String {
        if (snapshot == null || snapshot.restStart == null) return "暂无足够数据"
        val zone = ZoneId.systemDefault()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val rest = snapshot.restStart.atZone(zone).format(timeFmt)
        val wake = snapshot.wakeApprox?.atZone(zone)?.format(timeFmt) ?: "—"
        val hours = snapshot.durationMinutes?.let { minutes ->
            val h = minutes / 60
            val m = minutes % 60
            "${h}h ${m}m"
        } ?: "—"
        val source = when (snapshot.source) {
            RestSource.HEALTH_CONNECT -> "Health Connect"
            RestSource.PHONE_INACTIVITY -> "屏幕使用估计"
            null -> "—"
        }
        return "约 $rest → $wake（$hours · $source）"
    }

    /** 主动找人时的作息情境（只作背景，禁止写成固定问候模板） */
    fun formatForProactiveHint(
        snapshot: LifeContextSnapshot?,
        reason: ProactiveChatReason? = null,
    ): String? {
        if (snapshot == null || !snapshot.isInjectable) return null
        val zone = ZoneId.systemDefault()
        val timeFmt = DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())
        val rest = snapshot.restStart?.atZone(zone)?.format(timeFmt) ?: return null
        val wake = snapshot.wakeApprox?.atZone(zone)?.format(timeFmt)
        val whenNote = when (reason) {
            ProactiveChatReason.MORNING -> "现在是早晨主动找人"
            ProactiveChatReason.EVENING -> "现在是晚间主动找人"
            else -> "你正主动找用户说话"
        }
        return buildString {
            append("生活情境（仅供你理解与接话，不要照念、不要数字健康腔、不要报精确分钟）：")
            append(whenNote)
            append("。用户昨晚大约 $rest 后手机较安静")
            if (wake != null) append("，大约 $wake 又开始用")
            append("。若自然，可轻轻带一句关心；不自然就完全别提。按你的人设说话。")
        }
    }

    private suspend fun resolveFresh(settings: Settings): LifeContextSnapshot {
        // 1) Health Connect 睡眠优先
        if (settings.healthConnect.enabled && settings.healthConnect.includeSleep) {
            val fromHc = runCatching {
                val summary = healthConnectRepository.readDailySummary(settings.healthConnect)
                if (summary?.sleepStart != null && summary.sleepEnd != null) {
                    val minutes = summary.sleepMinutes
                        ?: Duration.between(summary.sleepStart, summary.sleepEnd).toMinutes()
                    LifeContextSnapshot(
                        restStart = summary.sleepStart,
                        wakeApprox = summary.sleepEnd,
                        durationMinutes = minutes,
                        source = RestSource.HEALTH_CONNECT,
                        confidence = RestConfidence.HIGH,
                    )
                } else {
                    null
                }
            }.onFailure {
                Log.w(TAG, "HC sleep for life context failed", it)
            }.getOrNull()
            if (fromHc != null && fromHc.isInjectable) return fromHc
        }

        // 2) 屏幕空闲估计
        if (!context.hasUsageStatsPermission()) {
            return LifeContextSnapshot.EMPTY
        }
        return runCatching {
            RestWindowEstimator.estimate(context) ?: LifeContextSnapshot.EMPTY
        }.onFailure {
            Log.w(TAG, "phone rest estimate failed", it)
        }.getOrDefault(LifeContextSnapshot.EMPTY)
    }

    companion object {
        private const val TAG = "LifeContextResolver"
        private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
