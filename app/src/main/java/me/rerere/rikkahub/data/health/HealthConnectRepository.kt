package me.rerere.rikkahub.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    NOT_INSTALLED,
    UNAVAILABLE,
}

data class HealthDailySummary(
    val steps: Long? = null,
    val heartRateBpmAvg: Double? = null,
    val heartRateBpmLatest: Long? = null,
    val sleepMinutes: Long? = null,
    val sleepStart: Instant? = null,
    val sleepEnd: Instant? = null,
    val distanceMeters: Double? = null,
    val caloriesKcal: Double? = null,
    val fetchedAtEpochMs: Long = System.currentTimeMillis(),
    val missingPermissions: Set<String> = emptySet(),
) {
    val cacheKey: String
        get() = listOf(
            steps,
            heartRateBpmLatest,
            heartRateBpmAvg?.toInt(),
            sleepMinutes,
            distanceMeters?.toInt(),
            caloriesKcal?.toInt(),
        ).joinToString(":")
}

class HealthConnectRepository(
    private val context: Context,
) {
    private val mutex = Mutex()
    @Volatile
    private var cachedSummary: HealthDailySummary? = null
    @Volatile
    private var cachedAtMs: Long = 0L

    fun getAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED
            HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.NOT_INSTALLED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    fun getClientOrNull(): HealthConnectClient? {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) return null
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    fun providerInstallIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$PROVIDER_PACKAGE_NAME")
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openHealthConnectSettingsIntent(): Intent {
        return Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun getGrantedPermissions(): Set<String> {
        val client = getClientOrNull() ?: return emptySet()
        return runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrElse {
            Log.w(TAG, "getGrantedPermissions failed", it)
            emptySet()
        }
    }

    suspend fun hasRequiredPermissions(setting: HealthConnectSetting = HealthConnectSetting()): Boolean {
        val granted = getGrantedPermissions()
        return requiredPermissions(setting).all { it in granted }
    }

    fun requiredPermissions(setting: HealthConnectSetting = HealthConnectSetting()): Set<String> {
        return buildSet {
            if (setting.includeSteps) add(HealthPermission.getReadPermission(StepsRecord::class))
            if (setting.includeHeartRate) add(HealthPermission.getReadPermission(HeartRateRecord::class))
            if (setting.includeSleep) add(HealthPermission.getReadPermission(SleepSessionRecord::class))
            if (setting.includeActivityExtras) {
                add(HealthPermission.getReadPermission(DistanceRecord::class))
                add(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
            }
        }
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun readDailySummary(
        setting: HealthConnectSetting,
        forceRefresh: Boolean = false,
    ): HealthDailySummary? {
        if (!setting.enabled) return null
        val client = getClientOrNull() ?: return null

        if (!forceRefresh) {
            val cached = cachedSummary
            if (cached != null && System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS) {
                return cached
            }
        }

        return mutex.withLock {
            if (!forceRefresh) {
                val cached = cachedSummary
                if (cached != null && System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS) {
                    return@withLock cached
                }
            }

            val granted = runCatching {
                client.permissionController.getGrantedPermissions()
            }.getOrElse {
                Log.w(TAG, "permission check failed", it)
                return@withLock null
            }
            val required = requiredPermissions(setting)
            val missing = required - granted

            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            // Sleep window: yesterday 18:00 → now, to catch overnight sessions.
            val sleepWindowStart = LocalDate.now(zone)
                .minusDays(1)
                .atTime(18, 0)
                .atZone(zone)
                .toInstant()

            val steps = if (setting.includeSteps && HealthPermission.getReadPermission(StepsRecord::class) in granted) {
                runCatching {
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                        )
                    )[StepsRecord.COUNT_TOTAL]
                }.onFailure { Log.w(TAG, "read steps failed", it) }.getOrNull()
            } else null

            var hrAvg: Double? = null
            var hrLatest: Long? = null
            if (setting.includeHeartRate && HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
                runCatching {
                    val records = client.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                        )
                    ).records
                    val samples = records.flatMap { it.samples }
                    if (samples.isNotEmpty()) {
                        hrAvg = samples.map { it.beatsPerMinute.toDouble() }.average()
                        hrLatest = samples.maxByOrNull { it.time }?.beatsPerMinute
                    }
                }.onFailure { Log.w(TAG, "read heart rate failed", it) }
            }

            var sleepMinutes: Long? = null
            var sleepStart: Instant? = null
            var sleepEnd: Instant? = null
            if (setting.includeSleep && HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
                runCatching {
                    val sessions = client.readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, now),
                        )
                    ).records
                    if (sessions.isNotEmpty()) {
                        sleepMinutes = sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                        sleepStart = sessions.minOfOrNull { it.startTime }
                        sleepEnd = sessions.maxOfOrNull { it.endTime }
                    }
                }.onFailure { Log.w(TAG, "read sleep failed", it) }
            }

            var distanceMeters: Double? = null
            var caloriesKcal: Double? = null
            if (setting.includeActivityExtras) {
                if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
                    distanceMeters = runCatching {
                        client.aggregate(
                            AggregateRequest(
                                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                            )
                        )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                    }.onFailure { Log.w(TAG, "read distance failed", it) }.getOrNull()
                }
                if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) {
                    caloriesKcal = runCatching {
                        client.aggregate(
                            AggregateRequest(
                                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                            )
                        )[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                    }.onFailure { Log.w(TAG, "read calories failed", it) }.getOrNull()
                }
            }

            val summary = HealthDailySummary(
                steps = steps,
                heartRateBpmAvg = hrAvg,
                heartRateBpmLatest = hrLatest,
                sleepMinutes = sleepMinutes,
                sleepStart = sleepStart,
                sleepEnd = sleepEnd,
                distanceMeters = distanceMeters,
                caloriesKcal = caloriesKcal,
                missingPermissions = missing,
            )
            cachedSummary = summary
            cachedAtMs = System.currentTimeMillis()
            summary
        }
    }

    fun formatSummaryForPrompt(summary: HealthDailySummary?, setting: HealthConnectSetting): String {
        if (summary == null || !setting.enabled) return ""
        val zone = ZoneId.systemDefault()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val lines = buildList {
            add("Wearable / Health Connect context (read-only, not medical advice):")
            if (setting.includeSteps) {
                add("- Today steps: ${summary.steps?.toString() ?: "unavailable"}")
            }
            if (setting.includeHeartRate) {
                val latest = summary.heartRateBpmLatest?.let { "${it.toInt()} bpm" } ?: "unavailable"
                val avg = summary.heartRateBpmAvg?.let { String.format(Locale.US, "%.0f bpm avg", it) }
                add("- Heart rate: $latest${avg?.let { " ($it today)" } ?: ""}")
            }
            if (setting.includeSleep) {
                val duration = summary.sleepMinutes?.let { minutes ->
                    val h = minutes / 60
                    val m = minutes % 60
                    "${h}h ${m}m"
                } ?: "unavailable"
                val window = if (summary.sleepStart != null && summary.sleepEnd != null) {
                    val start = summary.sleepStart.atZone(zone).format(timeFmt)
                    val end = summary.sleepEnd.atZone(zone).format(timeFmt)
                    " ($start–$end)"
                } else ""
                add("- Recent sleep: $duration$window")
            }
            if (setting.includeActivityExtras) {
                summary.distanceMeters?.let {
                    add("- Distance today: ${String.format(Locale.US, "%.1f km", it / 1000.0)}")
                }
                summary.caloriesKcal?.let {
                    add("- Calories burned today: ${String.format(Locale.US, "%.0f kcal", it)}")
                }
            }
            add("- Use this only for gentle companion care (tone, check-ins). Never diagnose or give medical instructions.")
            if (summary.missingPermissions.isNotEmpty()) {
                add("- Some Health Connect permissions are still missing.")
            }
        }
        return lines.joinToString("\n")
    }

    fun formatSummaryForUi(summary: HealthDailySummary?): String {
        if (summary == null) return "暂无数据"
        val zone = ZoneId.systemDefault()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        return buildString {
            appendLine("今日步数：${summary.steps ?: "—"}")
            appendLine(
                "心率：${
                    summary.heartRateBpmLatest?.let { "${it} bpm" } ?: "—"
                }${
                    summary.heartRateBpmAvg?.let {
                        "（今日均 ${String.format(Locale.US, "%.0f", it)}）"
                    }.orEmpty()
                }"
            )
            val sleep = summary.sleepMinutes?.let { minutes ->
                val h = minutes / 60
                val m = minutes % 60
                val window = if (summary.sleepStart != null && summary.sleepEnd != null) {
                    val start = summary.sleepStart.atZone(zone).format(timeFmt)
                    val end = summary.sleepEnd.atZone(zone).format(timeFmt)
                    "（$start–$end）"
                } else ""
                "${h} 小时 ${m} 分$window"
            } ?: "—"
            appendLine("最近睡眠：$sleep")
            summary.distanceMeters?.let {
                appendLine("今日距离：${String.format(Locale.US, "%.1f km", it / 1000.0)}")
            }
            summary.caloriesKcal?.let {
                appendLine("今日消耗：${String.format(Locale.US, "%.0f kcal", it)}")
            }
            if (summary.missingPermissions.isNotEmpty()) {
                append("部分权限未授予")
            }
        }.trim()
    }

    fun clearCache() {
        cachedSummary = null
        cachedAtMs = 0L
    }

    companion object {
        private const val TAG = "HealthConnectRepo"
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
        private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)

        val ALL_READ_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        )
    }
}
