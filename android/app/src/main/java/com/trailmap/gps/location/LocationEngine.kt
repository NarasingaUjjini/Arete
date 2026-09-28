package com.trailmap.gps.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.trailmap.gps.data.PowerProfile
import com.trailmap.gps.geo.GeoMath
import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class LocationQuality { EXCELLENT, GOOD, DEGRADED, STALE, NO_FIX, INVALID }

enum class LocationSession { BROWSING, NAVIGATION, RECORDING }

data class CurrentLocationState(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val horizontalAccuracy: Float = Float.NaN,
    val verticalAccuracy: Float = Float.NaN,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val timestamp: Long = 0L,
    val provider: String = "",
    val quality: LocationQuality = LocationQuality.NO_FIX,
    val usable: Boolean = false,
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val ageMs: Long = 0L,
    val lastGoodLatitude: Double? = null,
    val lastGoodLongitude: Double? = null,
    val lastGoodTimestamp: Long = 0L,
    val lastGoodAccuracy: Float = Float.NaN,
    val permissionGranted: Boolean = false,
    val gpsEnabled: Boolean = false,
    val averageCn0: Float = Float.NaN,
    val constellations: String = "",
    val firstFixMs: Long = 0L
) {
    fun toGpsUpdate(): GpsUpdate? {
        if (!usable) return null
        return GpsUpdate(
            latitude = latitude,
            longitude = longitude,
            elevation = altitude,
            accuracy = horizontalAccuracy,
            bearing = bearing,
            speed = speed,
            timestamp = timestamp,
            quality = quality,
            ageMs = ageMs,
            provider = provider
        )
    }
}

class LocationEngine(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _state = MutableStateFlow(CurrentLocationState())
    val state: StateFlow<CurrentLocationState> = _state.asStateFlow()

    private var lastAccepted: Location? = null
    private var lastGood: Location? = null
    private var satellitesVisible = 0
    private var satellitesUsed = 0
    private var averageCn0 = Float.NaN
    private var constellations = ""
    private var firstFixMs = 0L
    private var sessionStartedAt = 0L
    private var powerProfile = PowerProfile.BALANCED
    private var gnssCallback: GnssStatus.Callback? = null
    private val sessions = mutableSetOf<LocationSession>()
    private var appliedIntervalMs: Long? = null

    private val listener = LocationListener { location -> onLocation(location) }

    fun setPowerProfile(profile: PowerProfile) {
        if (powerProfile == profile) return
        powerProfile = profile
        if (sessions.isNotEmpty()) start(activeSession())
    }

    fun acquire(session: LocationSession) {
        if (sessions.isEmpty()) {
            sessionStartedAt = System.currentTimeMillis()
            firstFixMs = 0L
        }
        sessions += session
        start(activeSession())
    }

    fun release(session: LocationSession) {
        sessions -= session
        if (sessions.isEmpty()) stop() else start(activeSession())
    }

    fun setDesiredInterval(ms: Long?) {
        if (appliedIntervalMs == ms) return
        appliedIntervalMs = ms
        if (sessions.isNotEmpty()) start(activeSession())
    }

    private fun activeSession(): LocationSession = when {
        LocationSession.RECORDING in sessions -> LocationSession.RECORDING
        LocationSession.NAVIGATION in sessions -> LocationSession.NAVIGATION
        else -> LocationSession.BROWSING
    }

    @SuppressLint("MissingPermission")
    fun start(session: LocationSession) {
        try {
            locationManager.removeUpdates(listener)
        } catch (_: Exception) {
        }
        val interval = appliedIntervalMs ?: when (session) {
            LocationSession.BROWSING -> when (powerProfile) {
                PowerProfile.BATTERY -> 10_000L
                PowerProfile.BALANCED -> 4_000L
                PowerProfile.ACCURACY -> 2_000L
            }
            LocationSession.NAVIGATION, LocationSession.RECORDING -> 1_000L
        }
        val gpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        _state.value = _state.value.copy(
            gpsEnabled = gpsOn,
            permissionGranted = true
        )
        try {
            if (gpsOn) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    interval,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    interval,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            registerGnss()
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocation(it) }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(permissionGranted = false, quality = LocationQuality.NO_FIX)
            AppLog.w("gps", "Location permission missing", e)
        }
    }

    fun stop() {
        try {
            locationManager.removeUpdates(listener)
            gnssCallback?.let {
                if (Build.VERSION.SDK_INT >= 24) locationManager.unregisterGnssStatusCallback(it)
            }
        } catch (_: Exception) {
        }
    }

    fun tickAge(now: Long = System.currentTimeMillis()) {
        val current = _state.value
        if (current.timestamp <= 0L) return
        val age = now - current.timestamp
        val quality = when {
            !current.usable -> current.quality
            age > 15_000L -> LocationQuality.STALE
            else -> current.quality
        }
        _state.value = current.copy(ageMs = age, quality = quality)
    }

    @SuppressLint("MissingPermission")
    private fun registerGnss() {
        if (Build.VERSION.SDK_INT < 24) return
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                satellitesVisible = status.satelliteCount
                var used = 0
                var cn0Sum = 0.0
                var cn0Count = 0
                val names = linkedSetOf<String>()
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                    val cn0 = status.getCn0DbHz(i)
                    if (cn0 > 0f) {
                        cn0Sum += cn0
                        cn0Count++
                    }
                    names += constellationName(status.getConstellationType(i))
                }
                satellitesUsed = used
                averageCn0 = if (cn0Count > 0) (cn0Sum / cn0Count).toFloat() else Float.NaN
                constellations = names.joinToString(" ")
            }
        }
        gnssCallback = callback
        try {
            locationManager.registerGnssStatusCallback(callback, android.os.Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }
    }

    private fun onLocation(location: Location) {
        val now = System.currentTimeMillis()
        val verdict = validate(location, lastAccepted, now)
        val age = now - location.time.coerceAtMost(now)
        if (verdict == LocationQuality.INVALID) {
            _state.value = _state.value.copy(
                quality = LocationQuality.INVALID,
                usable = false,
                ageMs = age,
                satellitesVisible = satellitesVisible,
                satellitesUsed = satellitesUsed,
                gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            )
            return
        }
        lastAccepted = location
        if (verdict == LocationQuality.EXCELLENT || verdict == LocationQuality.GOOD) {
            lastGood = location
        }
        if (firstFixMs <= 0L && sessionStartedAt > 0L) {
            firstFixMs = (now - sessionStartedAt).coerceAtLeast(1L)
        }
        _state.value = CurrentLocationState(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            horizontalAccuracy = location.accuracy,
            verticalAccuracy = if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters
            } else Float.NaN,
            speed = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            timestamp = location.time,
            provider = location.provider ?: "",
            quality = if (age > 15_000L) LocationQuality.STALE else verdict,
            usable = verdict != LocationQuality.INVALID,
            satellitesVisible = satellitesVisible,
            satellitesUsed = satellitesUsed,
            ageMs = age,
            lastGoodLatitude = lastGood?.latitude,
            lastGoodLongitude = lastGood?.longitude,
            lastGoodTimestamp = lastGood?.time ?: 0L,
            lastGoodAccuracy = lastGood?.accuracy ?: Float.NaN,
            permissionGranted = true,
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER),
            averageCn0 = averageCn0,
            constellations = constellations,
            firstFixMs = firstFixMs
        )
    }

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLO"
        GnssStatus.CONSTELLATION_BEIDOU -> "BDS"
        GnssStatus.CONSTELLATION_GALILEO -> "GAL"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_IRNSS -> "NAVIC"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        else -> "OTH"
    }

    companion object {
        fun validate(location: Location, previous: Location?, now: Long): LocationQuality {
            val lat = location.latitude
            val lon = location.longitude
            if (!lat.isFinite() || !lon.isFinite()) return LocationQuality.INVALID
            if (!GeoMath.isValidLatitude(lat) || !GeoMath.isValidLongitude(lon)) return LocationQuality.INVALID
            if (GeoMath.isNullIsland(lat, lon)) return LocationQuality.INVALID
            if (location.hasAltitude()) {
                val alt = location.altitude
                if (!alt.isFinite() || alt < -500 || alt > 9000) return LocationQuality.INVALID
            }
            if (location.time > now + 60_000L) return LocationQuality.INVALID
            if (previous != null) {
                val dt = abs(location.time - previous.time) / 1000.0
                if (dt > 0.2) {
                    val dist = GeoMath.haversineMeters(
                        previous.latitude, previous.longitude, lat, lon
                    )
                    val implied = dist / dt
                    if (implied > 55.0) return LocationQuality.INVALID
                }
            }
            val acc = location.accuracy
            val age = now - location.time
            return when {
                age > 15_000L -> LocationQuality.STALE
                acc > 50f -> LocationQuality.DEGRADED
                acc > 15f -> LocationQuality.GOOD
                else -> LocationQuality.EXCELLENT
            }
        }
    }
}
