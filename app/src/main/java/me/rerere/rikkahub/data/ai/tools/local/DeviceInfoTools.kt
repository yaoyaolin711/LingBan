package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal fun buildGetDeviceInfoTool(context: Context): Tool = Tool(
    name = "get_device_info",
    description = """
        Get basic device information: brand, model, manufacturer, Android version, SDK,
        locale, timezone, screen resolution/density, uptime, and whether this is Solace's package.
        Does not require special runtime permissions.
    """.trimIndent().replace("\n", " "),
    parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
    execute = {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val payload = buildJsonObject {
            put("brand", Build.BRAND)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("product", Build.PRODUCT)
            put("android_version", Build.VERSION.RELEASE)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("locale", Locale.getDefault().toLanguageTag())
            put("timezone", TimeZone.getDefault().id)
            put("zone_id", ZoneId.systemDefault().id)
            put("screen_width_px", metrics.widthPixels)
            put("screen_height_px", metrics.heightPixels)
            put("density", metrics.density)
            put("density_dpi", metrics.densityDpi)
            put("uptime_ms", SystemClock.elapsedRealtime())
            put("package", context.packageName)
            put("now", Instant.now().toString())
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

internal fun buildGetBatteryTool(context: Context): Tool = Tool(
    name = "get_battery",
    description = """
        Get battery status: level percent, charging state, plugged type, health, temperature (°C),
        voltage (mV), and technology if available.
    """.trimIndent().replace("\n", " "),
    parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
    execute = {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "UNAVAILABLE")
                        put("message", "Battery status broadcast unavailable")
                    }.toString()
                )
            )
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val payload = buildJsonObject {
            put("level_percent", pct)
            put("charging", charging)
            put(
                "status",
                when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                    else -> "unknown"
                }
            )
            put(
                "plugged",
                when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                    BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                    else -> "none"
                }
            )
            put(
                "health",
                when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                    BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                    else -> "unknown"
                }
            )
            if (temp > 0) put("temperature_c", temp / 10.0)
            if (voltage > 0) put("voltage_mv", voltage)
            if (tech.isNotBlank()) put("technology", tech)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

internal fun buildGetLocationTool(context: Context): Tool = Tool(
    name = "get_location",
    description = """
        Get the device's current or last-known location (latitude, longitude, accuracy, provider, time).
        Requires location permission. If not granted, returns NO_PERMISSION and the app should ask
        the user to enable location access for Solace.
    """.trimIndent().replace("\n", " "),
    parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
    needsApproval = { true },
    execute = {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "NO_PERMISSION")
                        put(
                            "message",
                            "Location permission is not granted. Please allow location access for Solace and retry."
                        )
                    }.toString()
                )
            )
        }

        val location = withTimeoutOrNull(8_000L) {
            requestFreshLocation(context)
        } ?: getLastKnownLocation(context)

        if (location == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "UNAVAILABLE")
                        put(
                            "message",
                            "Location unavailable. Ensure GPS/network location is enabled and try outdoors or near Wi-Fi."
                        )
                    }.toString()
                )
            )
        }
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("accuracy_m", location.accuracy.toDouble())
                    put("provider", location.provider ?: "")
                    put("time", Instant.ofEpochMilli(location.time).toString())
                    if (location.hasAltitude()) put("altitude_m", location.altitude)
                    if (location.hasSpeed()) put("speed_mps", location.speed.toDouble())
                    if (location.hasBearing()) put("bearing_deg", location.bearing.toDouble())
                }.toString()
            )
        )
    },
)

@SuppressLint("MissingPermission")
private fun getLastKnownLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    return providers.mapNotNull { provider ->
        runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
}

@SuppressLint("MissingPermission")
private suspend fun requestFreshLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> return getLastKnownLocation(context)
    }
    return suspendCancellableCoroutine { cont ->
        val done = AtomicBoolean(false)
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                if (done.compareAndSet(false, true)) {
                    runCatching { lm.removeUpdates(this) }
                    cont.resume(location)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        cont.invokeOnCancellation {
            runCatching { lm.removeUpdates(listener) }
        }
        runCatching {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
        }.onFailure {
            if (done.compareAndSet(false, true)) {
                cont.resume(getLastKnownLocation(context))
            }
        }
    }
}

internal fun buildDeviceInfoTools(context: Context): List<Tool> = listOf(
    buildGetDeviceInfoTool(context),
    buildGetBatteryTool(context),
    buildGetLocationTool(context),
)
