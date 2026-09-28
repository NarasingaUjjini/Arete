package com.trailmap.gps.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.trailmap.gps.data.PowerProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class GpsUpdate(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val accuracy: Float,
    val bearing: Float,
    val speed: Float,
    val timestamp: Long,
    val quality: LocationQuality = LocationQuality.GOOD,
    val ageMs: Long = 0L,
    val provider: String = "",
    val integrityLabel: String = "",
    val positionReason: String = "",
    val observed: Boolean = true,
    val verifiedAgeMs: Long = 0L,
    val recordable: Boolean = true,
    /** Heading chevron when the fix is trusted. The location mark itself always draws. */
    val showPrecisePuck: Boolean = true
)

class LocationTracker(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun locationUpdates(profile: PowerProfile): Flow<GpsUpdate> = callbackFlow {
        val interval = when (profile) {
            PowerProfile.BATTERY -> 10_000L
            PowerProfile.BALANCED -> 3_000L
            PowerProfile.ACCURACY -> 1_000L
        }

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (gpsEnabled) {
            val listener = LocationListener { location -> trySend(location.toGpsUpdate()) }
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                trySend(it.toGpsUpdate())
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                interval,
                0f,
                listener,
                Looper.getMainLooper()
            )
            awaitClose { locationManager.removeUpdates(listener) }
        } else {
            awaitClose { }
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastLocation(): GpsUpdate? {
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.toGpsUpdate()
        } catch (_: Exception) {
            null
        }
    }

    private fun Location.toGpsUpdate() = GpsUpdate(
        latitude = latitude,
        longitude = longitude,
        elevation = altitude,
        accuracy = accuracy,
        bearing = bearing,
        speed = speed,
        timestamp = time
    )
}
