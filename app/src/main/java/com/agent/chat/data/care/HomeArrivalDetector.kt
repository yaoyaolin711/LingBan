package com.agent.chat.data.care

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class HomeArrivalDecision(
    val kind: String,
    val scenarioHint: String,
)

/**
 * 通过「当前位置是否在家附近」的状态变化，判断“从外面回家”。
 *
 * 实现方式：
 * - 保存 lastNearHome（上一次是否靠近家）
 * - 当前检测 nearHome=true 且 lastNearHome=false => arrival
 */
@Singleton
class HomeArrivalDetector @Inject constructor(
    private val homeLocationStore: HomeLocationStore,
    private val locationSnapshotProvider: LocationSnapshotProvider,
) {
    suspend fun detect(now: Long = System.currentTimeMillis()): HomeArrivalDecision? {
        val home = homeLocationStore.getHomeOrNull() ?: return null
        val snapshot = locationSnapshotProvider.getLastKnown() ?: return null

        if (snapshot.ageMinutes > HomeLocationStore.LOCATION_MAX_AGE_MINUTES) {
            return null
        }

        val near = distanceMeters(
            lat1 = snapshot.latitude,
            lng1 = snapshot.longitude,
            lat2 = home.latitude,
            lng2 = home.longitude,
        ) <= home.radiusMeters.toDouble()

        val lastNear = homeLocationStore.getLastNearHome()

        // 始终更新“是否在家附近”的状态（用于检测 outside->home）
        homeLocationStore.updateLastNearHome(near)

        if (lastNear) return null
        if (!near) return null

        val lastNudgeAt = homeLocationStore.getLastHomeNudgeAt()
        if (lastNudgeAt > 0L && now - lastNudgeAt < HomeLocationStore.HOME_NUDGE_COOLDOWN_MS) {
            return null
        }

        homeLocationStore.markHomeNudge(now)

        // proactive_nudge 模板会把 scenarioHint当作一句短提示
        return HomeArrivalDecision(
            kind = "home_arrived",
            scenarioHint = "用户刚回到家里：欢迎回家，先问一句累不累/晚饭吃了吗，像真人突然想起对方。",
        )
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        // Haversine
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

