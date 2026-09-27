package com.trailmap.gps.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
    val provider: String = ""
)

class LocationTracker(private val context: Context) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
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
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                interval
            ).setMinUpdateIntervalMillis(interval).build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { trySend(it.toGpsUpdate()) }
                }
            }
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            awaitClose { fusedClient.removeLocationUpdates(callback) }
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastLocation(): GpsUpdate? {
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.toGpsUpdate()
                ?: fusedClient.lastLocation.result?.toGpsUpdate()
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
