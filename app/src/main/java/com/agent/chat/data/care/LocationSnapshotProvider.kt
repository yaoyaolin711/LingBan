package com.agent.chat.data.care

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val ageMinutes: Long,
)

@Singleton
class LocationSnapshotProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission")
    suspend fun getLastKnown(): LocationSnapshot? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        var best: Location? = null
        for (provider in providers) {
            if (!lm.isProviderEnabled(provider) && provider != LocationManager.PASSIVE_PROVIDER) continue
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) best = loc
        }

        val last = best ?: return@withContext null
        val ageMinutes = ((System.currentTimeMillis() - last.time) / 60_000L).coerceAtLeast(0)

        LocationSnapshot(
            latitude = last.latitude,
            longitude = last.longitude,
            accuracyMeters = (last.accuracy ?: 0f),
            ageMinutes = ageMinutes,
        )
    }

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}

