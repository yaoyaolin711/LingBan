package me.rerere.rikkahub.data.life

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 过夜休息窗纯算法（无 Android 依赖，便于 JVM 单测）。
 */
object RestWindowLogic {

    const val MIN_REST_MS = 4L * 60 * 60 * 1000
    const val SHORT_WAKE_MS = 3L * 60 * 1000

    private val WINDOW_START_TIME = LocalTime.of(18, 0)
    private val WINDOW_END_TIME = LocalTime.of(14, 0)
    private val REST_START_EARLIEST = LocalTime.of(20, 0)
    private val REST_START_LATEST = LocalTime.of(8, 0)

    data class BusyInterval(
        val startMs: Long,
        val endMs: Long,
    ) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    data class RestWindow(
        val restStartMs: Long,
        val wakeApproxMs: Long,
        val durationMs: Long,
    )

    fun overnightWindow(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val today = zdt.toLocalDate()
        val nominalEnd = today.atTime(WINDOW_END_TIME).atZone(zone).toInstant().toEpochMilli()
        val end = minOf(nowMs, nominalEnd)
        val restDay = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        val start = restDay.minusDays(1).atTime(WINDOW_START_TIME).atZone(zone).toInstant().toEpochMilli()
        return start to end.coerceAtLeast(start)
    }

    /**
     * [busy] 为前台区间；短于 [shortWakeMs] 的忙段会被忽略（不打断过夜空闲）。
     */
    fun estimateFromBusyIntervals(
        busy: List<BusyInterval>,
        windowStartMs: Long,
        windowEndMs: Long,
        nowMs: Long = windowEndMs,
        zone: ZoneId = ZoneId.systemDefault(),
        minRestMs: Long = MIN_REST_MS,
        shortWakeMs: Long = SHORT_WAKE_MS,
    ): RestWindow? {
        val endBound = minOf(nowMs, windowEndMs)
        if (endBound <= windowStartMs) return null

        val significant = busy
            .mapNotNull { clip(it, windowStartMs, endBound) }
            .filter { it.durationMs >= shortWakeMs }
            .sortedBy { it.startMs }
            .let { mergeOverlapping(it) }

        var best: RestWindow? = null
        for (i in 0..significant.size) {
            val gapStart = if (i == 0) windowStartMs else significant[i - 1].endMs
            val gapEnd = if (i == significant.size) endBound else significant[i].startMs
            val duration = gapEnd - gapStart
            if (duration < minRestMs) continue
            if (!isPlausibleRestStart(gapStart, zone)) continue
            val candidate = RestWindow(
                restStartMs = gapStart,
                wakeApproxMs = gapEnd,
                durationMs = duration,
            )
            if (best == null || candidate.restStartMs > best.restStartMs) {
                best = candidate
            }
        }
        return best
    }

    private fun isPlausibleRestStart(restStartMs: Long, zone: ZoneId): Boolean {
        val time = Instant.ofEpochMilli(restStartMs).atZone(zone).toLocalTime()
        return !time.isBefore(REST_START_EARLIEST) || time.isBefore(REST_START_LATEST)
    }

    private fun clip(interval: BusyInterval, startMs: Long, endMs: Long): BusyInterval? {
        val s = maxOf(interval.startMs, startMs)
        val e = minOf(interval.endMs, endMs)
        if (e <= s) return null
        return BusyInterval(s, e)
    }

    private fun mergeOverlapping(sorted: List<BusyInterval>): List<BusyInterval> {
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<BusyInterval>()
        var cur = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startMs <= cur.endMs) {
                cur = BusyInterval(cur.startMs, maxOf(cur.endMs, next.endMs))
            } else {
                out += cur
                cur = next
            }
        }
        out += cur
        return out
    }
}
