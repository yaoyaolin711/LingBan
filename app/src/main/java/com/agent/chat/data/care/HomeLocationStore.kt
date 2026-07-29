package com.agent.chat.data.care

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeLocationSnapshot(
    val hasHome: Boolean = false,
    val radiusMeters: Int = HomeLocationStore.DEFAULT_RADIUS_METERS,
)

/**
 * 本地保存「家」中心点与到家触发状态。
 *
 * 注意：
 * - 不写入 Room / Persona / Relationship
 * - 仅用于后台主动问候触发
 */
@Singleton
class HomeLocationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<HomeLocationSnapshot> = _snapshot.asStateFlow()

    fun getHomeOrNull(): HomeLocation? = readHomeOrNull()

    fun setHome(latitude: Double, longitude: Double, radiusMeters: Int) {
        prefs.edit()
            .putBoolean(KEY_HAS_HOME, true)
            .putFloat(KEY_LAT, latitude.toFloat())
            .putFloat(KEY_LNG, longitude.toFloat())
            .putInt(KEY_RADIUS_M, radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS))
            .apply()
        _snapshot.value = HomeLocationSnapshot(
            hasHome = true,
            radiusMeters = prefs.getInt(KEY_RADIUS_M, DEFAULT_RADIUS_METERS),
        )
    }

    fun clearHome() {
        prefs.edit()
            .putBoolean(KEY_HAS_HOME, false)
            .apply()
        _snapshot.value = HomeLocationSnapshot(hasHome = false)
    }

    fun setHomeRadius(radiusMeters: Int) {
        if (!prefs.getBoolean(KEY_HAS_HOME, false)) return
        prefs.edit().putInt(KEY_RADIUS_M, radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)).apply()
        _snapshot.value = _snapshot.value.copy(radiusMeters = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS))
    }

    fun getLastNearHome(): Boolean = prefs.getBoolean(KEY_LAST_NEAR_HOME, false)

    fun updateLastNearHome(near: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LAST_NEAR_HOME, near)
            .putLong(KEY_LAST_NEAR_HOME_AT, System.currentTimeMillis())
            .apply()
    }

    fun getLastHomeNudgeAt(): Long = prefs.getLong(KEY_LAST_HOME_NUDGE_AT, 0L)

    fun markHomeNudge(now: Long) {
        prefs.edit()
            .putLong(KEY_LAST_HOME_NUDGE_AT, now)
            .apply()
    }

    private fun readSnapshot(): HomeLocationSnapshot {
        val hasHome = prefs.getBoolean(KEY_HAS_HOME, false)
        val radius = prefs.getInt(KEY_RADIUS_M, DEFAULT_RADIUS_METERS)
        return HomeLocationSnapshot(hasHome = hasHome, radiusMeters = radius)
    }

    private fun readHomeOrNull(): HomeLocation? {
        val hasHome = prefs.getBoolean(KEY_HAS_HOME, false)
        if (!hasHome) return null
        val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
        val lng = prefs.getFloat(KEY_LNG, 0f).toDouble()
        val radius = prefs.getInt(KEY_RADIUS_M, DEFAULT_RADIUS_METERS)
        return HomeLocation(latitude = lat, longitude = lng, radiusMeters = radius)
    }

    companion object {
        private const val PREFS_NAME = "home_location"
        private const val KEY_HAS_HOME = "has_home"
        private const val KEY_LAT = "home_lat"
        private const val KEY_LNG = "home_lng"
        private const val KEY_RADIUS_M = "home_radius_m"

        private const val KEY_LAST_NEAR_HOME = "last_near_home"
        private const val KEY_LAST_NEAR_HOME_AT = "last_near_home_at"
        private const val KEY_LAST_HOME_NUDGE_AT = "last_home_nudge_at"

        const val DEFAULT_RADIUS_METERS = 300
        const val MIN_RADIUS_METERS = 100
        const val MAX_RADIUS_METERS = 1500

        /** 到家触发冷却，避免频繁进出刷屏 */
        const val HOME_NUDGE_COOLDOWN_MS: Long = 6 * 60 * 60 * 1000L
        /** 定位缓存超过此年龄则不用于触发，避免 GPS 冒认 */
        const val LOCATION_MAX_AGE_MINUTES: Long = 45
    }
}

data class HomeLocation(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
)

