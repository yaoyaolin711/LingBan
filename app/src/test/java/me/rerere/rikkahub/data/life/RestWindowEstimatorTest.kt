package me.rerere.rikkahub.data.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class RestWindowEstimatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun ms(date: LocalDate, hour: Int, minute: Int = 0): Long {
        return LocalDateTime.of(date, LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    @Test
    fun normalOvernight_detectsRestWindow() {
        val today = LocalDate.of(2026, 8, 4)
        val yesterday = today.minusDays(1)
        val now = ms(today, 10, 0)
        val (windowStart, windowEnd) = RestWindowLogic.overnightWindow(now, zone)

        val busy = listOf(
            RestWindowLogic.BusyInterval(ms(yesterday, 21, 0), ms(today, 1, 12)),
            RestWindowLogic.BusyInterval(ms(today, 8, 5), ms(today, 9, 30)),
        )

        val window = RestWindowLogic.estimateFromBusyIntervals(
            busy = busy,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = now,
            zone = zone,
        )
        assertNotNull(window)
        assertEquals(ms(today, 1, 12), window!!.restStartMs)
        assertEquals(ms(today, 8, 5), window.wakeApproxMs)
        assertTrue(window.durationMs >= RestWindowLogic.MIN_REST_MS)
    }

    @Test
    fun shortMidnightWake_doesNotSplitRest() {
        val today = LocalDate.of(2026, 8, 4)
        val yesterday = today.minusDays(1)
        val now = ms(today, 10, 0)
        val (windowStart, windowEnd) = RestWindowLogic.overnightWindow(now, zone)

        val busy = listOf(
            RestWindowLogic.BusyInterval(ms(yesterday, 22, 0), ms(today, 0, 40)),
            RestWindowLogic.BusyInterval(ms(today, 3, 0), ms(today, 3, 2)),
            RestWindowLogic.BusyInterval(ms(today, 7, 30), ms(today, 8, 0)),
        )

        val window = RestWindowLogic.estimateFromBusyIntervals(
            busy = busy,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = now,
            zone = zone,
        )
        assertNotNull(window)
        assertEquals(ms(today, 0, 40), window!!.restStartMs)
        assertEquals(ms(today, 7, 30), window.wakeApproxMs)
    }

    @Test
    fun shortGap_returnsNull() {
        val today = LocalDate.of(2026, 8, 4)
        val yesterday = today.minusDays(1)
        val now = ms(today, 10, 0)
        val (windowStart, windowEnd) = RestWindowLogic.overnightWindow(now, zone)

        // 整夜频繁使用，相邻空闲都 < 4h
        val busy = listOf(
            RestWindowLogic.BusyInterval(ms(yesterday, 21, 0), ms(yesterday, 23, 0)),
            RestWindowLogic.BusyInterval(ms(today, 0, 30), ms(today, 2, 0)),
            RestWindowLogic.BusyInterval(ms(today, 3, 30), ms(today, 5, 0)),
            RestWindowLogic.BusyInterval(ms(today, 6, 30), ms(today, 9, 30)),
        )

        val window = RestWindowLogic.estimateFromBusyIntervals(
            busy = busy,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = now,
            zone = zone,
        )
        assertNull(window)
    }

    @Test
    fun afternoonIdle_notTreatedAsSleep() {
        val today = LocalDate.of(2026, 8, 4)
        val now = ms(today, 13, 0)
        val (windowStart, windowEnd) = RestWindowLogic.overnightWindow(now, zone)

        val busy = listOf(
            RestWindowLogic.BusyInterval(
                ms(today.minusDays(1), 12, 0),
                ms(today.minusDays(1), 15, 0),
            ),
            RestWindowLogic.BusyInterval(ms(today, 10, 0), ms(today, 11, 0)),
        )

        val window = RestWindowLogic.estimateFromBusyIntervals(
            busy = busy,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = now,
            zone = zone,
        )
        assertNull(window)
    }

    @Test
    fun stillInForeground_noCompletedRest() {
        val today = LocalDate.of(2026, 8, 4)
        val yesterday = today.minusDays(1)
        val now = ms(today, 2, 0)
        val (windowStart, windowEnd) = RestWindowLogic.overnightWindow(now, zone)

        val busy = listOf(
            RestWindowLogic.BusyInterval(ms(yesterday, 20, 0), now),
        )

        val window = RestWindowLogic.estimateFromBusyIntervals(
            busy = busy,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = now,
            zone = zone,
        )
        assertNull(window)
    }
}
