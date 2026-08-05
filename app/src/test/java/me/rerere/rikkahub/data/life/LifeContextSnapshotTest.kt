package me.rerere.rikkahub.data.life

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * formatForPrompt 不依赖 Android；通过临时 subclass 不了，直接测 Snapshot 可注入条件 + 文案规则用 resolver 无 Context 部分。
 * 这里用轻量断言覆盖注入门槛。
 */
class LifeContextSnapshotTest {

    @Test
    fun injectable_requiresFourHoursAndNonLowConfidence() {
        val ok = LifeContextSnapshot(
            restStart = Instant.parse("2026-08-03T17:00:00Z"),
            wakeApprox = Instant.parse("2026-08-04T01:00:00Z"),
            durationMinutes = 8 * 60,
            source = RestSource.PHONE_INACTIVITY,
            confidence = RestConfidence.MEDIUM,
        )
        assertTrue(ok.isInjectable)

        val short = ok.copy(durationMinutes = 3 * 60)
        assertFalse(short.isInjectable)

        val low = ok.copy(confidence = RestConfidence.LOW)
        assertFalse(low.isInjectable)
    }
}
