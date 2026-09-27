package com.trailmap.gps.ui.components

import com.trailmap.gps.conditions.Daylight
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.DataBarMetric
import com.trailmap.gps.data.DataBarProfile
import com.trailmap.gps.geo.Coordinates
import com.trailmap.gps.location.CurrentLocationState
import com.trailmap.gps.location.GpsUpdate
import com.trailmap.gps.ui.NavigationState
import com.trailmap.gps.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DataBarReading(val label: String, val value: String, val unit: String = "")

data class DataBarInputs(
    val location: GpsUpdate? = null,
    val locationState: CurrentLocationState? = null,
    val nav: NavigationState? = null,
    val traveledMeters: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val verticalMetersPerHour: Double? = null,
    val batteryPercent: Int? = null,
    val temperatureF: Int? = null,
    val wind: String? = null,
    val summitBearing: Double? = null
)

object DataBar {
    fun metrics(settings: AppSettings): Triple<DataBarMetric, DataBarMetric, DataBarMetric> {
        return if (settings.dataBarProfile == DataBarProfile.CUSTOM) {
            DataBarMetric.uniqueSlots(settings.dataBarSlot1, settings.dataBarSlot2, settings.dataBarSlot3)
        } else {
            DataBarMetric.slotsFor(settings.dataBarProfile)
        }
    }

    fun readings(settings: AppSettings, inputs: DataBarInputs): List<DataBarReading> {
        val (a, b, c) = metrics(settings)
        return listOf(a, b, c).map { read(it, settings, inputs) }
    }

    fun read(metric: DataBarMetric, settings: AppSettings, inputs: DataBarInputs): DataBarReading {
        val loc = inputs.location
        val nav = inputs.nav
        val lat = loc?.latitude
        val lon = loc?.longitude
        return when (metric) {
            DataBarMetric.COORDINATES -> DataBarReading(
                "POS",
                if (lat != null && lon != null) Coordinates.format(lat, lon, settings.coordinateFormat) else "—"
            )
            DataBarMetric.GPS_ACCURACY -> DataBarReading(
                "GPS",
                loc?.let { "±${it.accuracy.toInt()}" } ?: "—",
                "m"
            )
            DataBarMetric.ELEVATION -> DataBarReading(
                "ELEV",
                loc?.let { FormatUtils.formatElevation(it.elevation, settings.elevationUnit).substringBefore(" ") } ?: "—",
                if (settings.elevationUnit.name == "FEET") "ft" else "m"
            )
            DataBarMetric.SPEED -> DataBarReading(
                "SPD",
                loc?.let { FormatUtils.formatSpeed(it.speed, settings.distanceUnit).substringBefore(" ") } ?: "—",
                if (settings.distanceUnit.name == "MILES") "mph" else "km/h"
            )
            DataBarMetric.AVG_SPEED -> {
                val avg = if (inputs.elapsedSeconds > 0) (inputs.traveledMeters / inputs.elapsedSeconds).toFloat() else null
                DataBarReading("AVG", avg?.let { FormatUtils.formatSpeed(it, settings.distanceUnit).substringBefore(" ") } ?: "—")
            }
            DataBarMetric.DISTANCE_TRAVELED -> DataBarReading(
                "GONE",
                FormatUtils.formatDistance(inputs.traveledMeters, settings.distanceUnit)
            )
            DataBarMetric.DISTANCE_REMAINING -> DataBarReading(
                "LEFT",
                nav?.let { FormatUtils.formatDistance(it.remainingDistance, settings.distanceUnit) } ?: "—"
            )
            DataBarMetric.ASCENT -> DataBarReading(
                "ASC",
                nav?.let { FormatUtils.formatElevation(it.remainingAscent, settings.elevationUnit) } ?: "—"
            )
            DataBarMetric.DESCENT -> DataBarReading("DSC", "—")
            DataBarMetric.VERTICAL_SPEED -> DataBarReading(
                "VERT",
                inputs.verticalMetersPerHour?.let {
                    val signed = if (it >= 0) "+" else ""
                    "$signed${FormatUtils.formatElevation(kotlin.math.abs(it), settings.elevationUnit)}/hr"
                } ?: "—"
            )
            DataBarMetric.GRADE -> DataBarReading("GRD", "—")
            DataBarMetric.BEARING_WAYPOINT -> DataBarReading(
                "NEXT",
                nav?.let { FormatUtils.formatBearing(it.bearing) } ?: "—"
            )
            DataBarMetric.BEARING_SUMMIT -> DataBarReading(
                "SUM",
                inputs.summitBearing?.let { FormatUtils.formatBearing(it) } ?: "—"
            )
            DataBarMetric.PROGRESS -> DataBarReading(
                "PROG",
                nav?.let { "${(it.progress * 100).toInt()}%" } ?: "—"
            )
            DataBarMetric.MOVING_TIME, DataBarMetric.TOTAL_TIME -> DataBarReading(
                "TIME",
                FormatUtils.formatDuration(inputs.elapsedSeconds)
            )
            DataBarMetric.ETA -> DataBarReading("ETA", "est. —")
            DataBarMetric.SUNRISE -> {
                val sun = if (lat != null && lon != null) Daylight.calculate(lat, lon) else null
                DataBarReading("RISE", clock(sun?.sunriseMs))
            }
            DataBarMetric.SUNSET -> {
                val sun = if (lat != null && lon != null) Daylight.calculate(lat, lon) else null
                DataBarReading("SET", clock(sun?.sunsetMs))
            }
            DataBarMetric.DAYLIGHT -> {
                val sun = if (lat != null && lon != null) Daylight.calculate(lat, lon) else null
                val rem = sun?.remainingUntilSunset(System.currentTimeMillis())
                DataBarReading(
                    "LIGHT",
                    when {
                        rem == null -> "—"
                        rem <= 0 -> "dusk"
                        else -> FormatUtils.formatDuration(rem / 1000)
                    }
                )
            }
            DataBarMetric.TEMPERATURE -> DataBarReading(
                "TEMP",
                inputs.temperatureF?.let { "$it°F" } ?: "—"
            )
            DataBarMetric.WIND -> DataBarReading("WIND", inputs.wind?.ifBlank { "—" } ?: "—")
            DataBarMetric.BATTERY -> DataBarReading("BAT", inputs.batteryPercent?.let { "$it%" } ?: "—")
            DataBarMetric.FIX_AGE -> DataBarReading(
                "AGE",
                inputs.locationState?.takeIf { it.timestamp > 0 }?.let { "${it.ageMs / 1000}s" } ?: "—"
            )
        }
    }

    private fun clock(ms: Long?): String {
        if (ms == null) return "—"
        return SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
    }
}
