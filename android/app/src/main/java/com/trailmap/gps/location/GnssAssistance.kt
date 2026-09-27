package com.trailmap.gps.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class GnssAssistanceResult(
    val success: Boolean,
    val message: String
)

class GnssAssistance(private val context: Context) {
    @SuppressLint("MissingPermission")
    suspend fun refresh(): GnssAssistanceResult = withContext(Dispatchers.Main) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return@withContext GnssAssistanceResult(
                success = false,
                message = "Turn on location / GPS, then try again"
            )
        }

        val extras = Bundle()
        val injectedXtra = runCatching {
            manager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", extras)
        }.getOrDefault(false)
        val injectedTime = runCatching {
            manager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", extras)
        }.getOrDefault(false)

        val fix = withTimeoutOrNull(20_000) { awaitGpsFix(manager) }
        return@withContext when {
            fix != null -> GnssAssistanceResult(
                success = true,
                message = "GPS assistance saved"
            )
            injectedXtra || injectedTime -> GnssAssistanceResult(
                success = true,
                message = "Assistance data requested — keep GPS on until you get a lock"
            )
            else -> GnssAssistanceResult(
                success = false,
                message = "Could not refresh GPS data. Go outside with a clear sky and try again."
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitGpsFix(manager: LocationManager): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }

                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }
            }
            try {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { manager.removeUpdates(listener) }
        }
}
