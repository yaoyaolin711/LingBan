package me.rerere.rikkahub.data.life

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import me.rerere.rikkahub.data.device.UsageStatsQuery
import java.time.Instant
import java.time.ZoneId

/**
 * 用「过夜最长前台空闲窗」估计休息起止（Android UsageStats 适配层）。
 * 纯算法见 [RestWindowLogic]。
 */
object RestWindowEstimator {

    const val MIN_REST_MS = RestWindowLogic.MIN_REST_MS
    const val SHORT_WAKE_MS = RestWindowLogic.SHORT_WAKE_MS

    typealias BusyInterval = RestWindowLogic.BusyInterval
    typealias RestWindow = RestWindowLogic.RestWindow

    fun estimate(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LifeContextSnapshot? {
        val (windowStart, windowEnd) = RestWindowLogic.overnightWindow(nowMs, zone)
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val launcherPackages = UsageStatsQuery.resolveLauncherPackages(context.packageManager)
        val excluded = launcherPackages + context.packageName
        val busy = collectBusyIntervals(usageStatsManager, windowStart, windowEnd, excluded)
        val window = RestWindowLogic.estimateFromBusyIntervals(
            busy = busy,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = nowMs,
            zone = zone,
        ) ?: return null
        return LifeContextSnapshot(
            restStart = Instant.ofEpochMilli(window.restStartMs),
            wakeApprox = Instant.ofEpochMilli(window.wakeApproxMs),
            durationMinutes = window.durationMs / 60_000,
            source = RestSource.PHONE_INACTIVITY,
            confidence = RestConfidence.MEDIUM,
            fetchedAtEpochMs = nowMs,
        )
    }

    fun overnightWindow(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()) =
        RestWindowLogic.overnightWindow(nowMs, zone)

    fun estimateFromBusyIntervals(
        busy: List<BusyInterval>,
        windowStartMs: Long,
        windowEndMs: Long,
        nowMs: Long = windowEndMs,
        zone: ZoneId = ZoneId.systemDefault(),
        minRestMs: Long = MIN_REST_MS,
        shortWakeMs: Long = SHORT_WAKE_MS,
    ) = RestWindowLogic.estimateFromBusyIntervals(
        busy = busy,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        nowMs = nowMs,
        zone = zone,
        minRestMs = minRestMs,
        shortWakeMs = shortWakeMs,
    )

    @Suppress("DEPRECATION", "NewApi")
    fun collectBusyIntervals(
        usageStatsManager: UsageStatsManager,
        startMs: Long,
        endMs: Long,
        excludedPackages: Set<String>,
    ): List<BusyInterval> {
        val lookback = 12L * 60 * 60 * 1000
        val events = usageStatsManager.queryEvents(startMs - lookback, endMs)
        val event = UsageEvents.Event()
        val result = ArrayList<BusyInterval>()
        var currentPkg: String? = null
        var currentStart = 0L

        fun settle(until: Long) {
            val pkg = currentPkg
            currentPkg = null
            if (pkg == null || pkg in excludedPackages) return
            val from = maxOf(currentStart, startMs)
            if (until > from) {
                result += BusyInterval(from, until)
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
        return result
    }
}
