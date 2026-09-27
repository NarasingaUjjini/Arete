package com.trailmap.gps.conditions

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

data class DaylightTimes(
    val sunriseMs: Long?,
    val sunsetMs: Long?,
    val civilDawnMs: Long?,
    val civilDuskMs: Long?,
    val latitude: Double,
    val longitude: Double
) {
    fun remainingUntilSunset(nowMs: Long): Long? = sunsetMs?.let { it - nowMs }
}

/**
 * NOAA-style sunrise/sunset computed locally. No network.
 * Zenith 90.833° = official sunrise/sunset; 96° = civil twilight.
 */
object Daylight {
    private const val OFFICIAL_ZENITH = 90.833
    private const val CIVIL_ZENITH = 96.0

    fun calculate(
        lat: Double,
        lon: Double,
        atMs: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault()
    ): DaylightTimes {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = atMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return DaylightTimes(
            sunriseMs = eventMs(lat, lon, year, month, day, OFFICIAL_ZENITH, rising = true, zone),
            sunsetMs = eventMs(lat, lon, year, month, day, OFFICIAL_ZENITH, rising = false, zone),
            civilDawnMs = eventMs(lat, lon, year, month, day, CIVIL_ZENITH, rising = true, zone),
            civilDuskMs = eventMs(lat, lon, year, month, day, CIVIL_ZENITH, rising = false, zone),
            latitude = lat,
            longitude = lon
        )
    }

    private fun eventMs(
        lat: Double,
        lon: Double,
        year: Int,
        month: Int,
        day: Int,
        zenith: Double,
        rising: Boolean,
        zone: TimeZone
    ): Long? {
        val hoursUtc = solarEventHoursUtc(lat, lon, year, month, day, zenith, rising) ?: return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis + (hoursUtc * 3_600_000.0).roundToLong()
    }

    internal fun solarEventHoursUtc(
        lat: Double,
        lon: Double,
        year: Int,
        month: Int,
        day: Int,
        zenith: Double,
        rising: Boolean
    ): Double? {
        val n1 = Math.floor(275.0 * month / 9.0)
        val n2 = Math.floor((month + 9.0) / 12.0)
        val n3 = 1 + Math.floor((year - 4 * Math.floor(year / 4.0) + 2) / 3.0)
        val n = n1 - n2 * n3 + day - 30
        val lngHour = lon / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634
        l = (l + 360) % 360
        var ra = Math.toDegrees(kotlin.math.atan(0.91764 * tan(Math.toRadians(l))))
        ra = (ra + 360) % 360
        val lQuad = Math.floor(l / 90.0) * 90.0
        val raQuad = Math.floor(ra / 90.0) * 90.0
        ra = (ra + (lQuad - raQuad)) / 15.0
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(Math.toRadians(zenith)) - sinDec * sin(Math.toRadians(lat))) /
            (cosDec * cos(Math.toRadians(lat)))
        if (cosH > 1.0 || cosH < -1.0) return null
        var h = Math.toDegrees(acos(cosH))
        h = if (rising) 360.0 - h else h
        h /= 15.0
        val raw = h + ra - 0.06571 * t - 6.622
        var ut = raw - lngHour
        ut %= 24.0
        if (ut < 0) ut += 24.0
        return ut
    }
}
