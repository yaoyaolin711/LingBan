package me.rerere.rikkahub.data.device

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** 计算屏幕时间时向前回看的窗口(12h), 用于还原区间开始时刻已在前台的 App. */
private const val LOOKBACK_MS = 12L * 60 * 60 * 1000

data class ForegroundAppInfo(
    val packageName: String,
    val appName: String,
    /** 当前连续前台时长(毫秒), 从最近一次进入前台算起 */
    val continuousMs: Long,
    val continuousMinutes: Long = continuousMs / 60_000,
)

data class AppSessionInfo(
    val packageName: String,
    val appName: String,
    /** 今日累计前台时长 */
    val todayTotalMs: Long,
    /** 当前是否在前台 */
    val isForeground: Boolean,
    /** 若当前在前台, 本段连续时长; 否则 0 */
    val continuousMs: Long,
)

/**
 * UsageStats 查询公共层: 前台 App、连续会话、区间前台时长聚合.
 * ScreenTime 工具与设备关怀监测共用此实现.
 */
object UsageStatsQuery {

    fun getForegroundApp(context: Context, nowMs: Long = System.currentTimeMillis()): ForegroundAppInfo? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val launcherPackages = resolveLauncherPackages(pm)
        val lookbackStart = nowMs - LOOKBACK_MS
        val events = usageStatsManager.queryEvents(lookbackStart, nowMs)
        val event = UsageEvents.Event()

        var currentPkg: String? = null
        var currentStart = 0L

        @Suppress("DEPRECATION", "NewApi")
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    currentPkg = event.packageName
                    currentStart = event.timeStamp
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (event.packageName == currentPkg) {
                        currentPkg = null
                        currentStart = 0L
                    }
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    currentPkg = null
                    currentStart = 0L
                }
            }
        }

        val pkg = currentPkg ?: return null
        if (pkg in launcherPackages || pkg == context.packageName) return null
        val continuous = (nowMs - currentStart).coerceAtLeast(0L)
        return ForegroundAppInfo(
            packageName = pkg,
            appName = resolveAppName(pm, pkg),
            continuousMs = continuous,
        )
    }

    fun getAppSession(
        context: Context,
        packageName: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AppSessionInfo {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val launcherPackages = resolveLauncherPackages(pm)
        val foregroundMs = computeForegroundTime(usageStatsManager, startOfDay, nowMs, launcherPackages)
        val todayTotal = foregroundMs[packageName] ?: 0L
        val foreground = getForegroundApp(context, nowMs)
        val isFg = foreground?.packageName == packageName
        return AppSessionInfo(
            packageName = packageName,
            appName = resolveAppName(pm, packageName),
            todayTotalMs = todayTotal,
            isForeground = isFg,
            continuousMs = if (isFg) foreground.continuousMs else 0L,
        )
    }

    /**
     * 用"全局单一前台"模型计算 [startMs, endMs) 区间内每个 App 的前台时长(毫秒).
     */
    @Suppress(
        "DEPRECATION",
        "NewApi",
    )
    fun computeForegroundTime(
        usageStatsManager: UsageStatsManager,
        startMs: Long,
        endMs: Long,
        excludedPackages: Set<String>,
    ): Map<String, Long> {
        val foregroundMs = HashMap<String, Long>()
        val events = usageStatsManager.queryEvents(startMs - LOOKBACK_MS, endMs)
        val event = UsageEvents.Event()

        var currentPkg: String? = null
        var currentStart = 0L

        fun settle(until: Long) {
            val pkg = currentPkg
            currentPkg = null
            if (pkg == null || pkg in excludedPackages) return
            val from = maxOf(currentStart, startMs)
            val duration = until - from
            if (duration > 0) {
                foregroundMs[pkg] = (foregroundMs[pkg] ?: 0L) + duration
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (event.packageName != currentPkg) {
                        settle(event.timeStamp)
                        currentPkg = event.packageName
                        currentStart = event.timeStamp
                    }
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (event.packageName == currentPkg) {
                        settle(event.timeStamp)
                    }
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    settle(event.timeStamp)
                }
            }
        }
        settle(endMs)
        return foregroundMs
    }

    fun resolveLauncherPackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun resolveAppName(pm: PackageManager, packageName: String): String {
        return runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    fun parseUsageTime(raw: String, zone: ZoneId): ZonedDateTime {
        val text = raw.trim()
        text.toLongOrNull()?.let { return Instant.ofEpochMilli(it).atZone(zone) }
        runCatching { return OffsetDateTime.parse(text).atZoneSameInstant(zone) }
        runCatching { return Instant.parse(text).atZone(zone) }
        runCatching { return LocalDateTime.parse(text).atZone(zone) }
        runCatching { return LocalDate.parse(text).atStartOfDay(zone) }
        error("Invalid time format: '$raw'. Use ISO-8601 date/date-time or epoch milliseconds.")
    }
}
