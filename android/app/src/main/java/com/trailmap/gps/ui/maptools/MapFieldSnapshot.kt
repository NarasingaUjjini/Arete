package com.trailmap.gps.ui.maptools

import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.DistanceUnit
import com.trailmap.gps.geo.Coordinates
import com.trailmap.gps.location.CurrentLocationState
import com.trailmap.gps.location.GpsUpdate
import com.trailmap.gps.map.MapScale
import com.trailmap.gps.map.MapScaleReading
import com.trailmap.gps.ui.NavigationState
import com.trailmap.gps.ui.components.DataBar
import com.trailmap.gps.ui.components.DataBarInputs
import com.trailmap.gps.ui.components.DataBarReading
import com.trailmap.gps.ui.components.qualityLabel
import com.trailmap.gps.util.FormatUtils

data class CompassSnapshot(
    val headingMagnetic: Double,
    val headingReady: Boolean,
    val course: Double,
    val bearing: Double,
    val summitBearing: Double?,
    val declination: Double,
    val magneticNorth: Boolean,
    val nextLabel: String
) {
    val headingShown: Double
        get() = if (headingReady) applyMagnetic(headingMagnetic) else applyNorth(course)
    val courseShown: Double get() = applyNorth(course)
    val bearingShown: Double get() = applyNorth(bearing)
    val summitShown: Double? get() = summitBearing?.let { applyNorth(it) }

    private fun applyNorth(degrees: Double): Double =
        if (magneticNorth) (degrees - declination + 360.0) % 360.0 else degrees

    private fun applyMagnetic(degrees: Double): Double =
        if (magneticNorth) degrees else (degrees + declination + 360.0) % 360.0
}

data class MapFieldSnapshot(
    val readings: List<DataBarReading>,
    val location: GpsUpdate?,
    val locationState: CurrentLocationState,
    val compass: CompassSnapshot,
    val coordinates: String,
    val elevationLabel: String,
    val accuracyLabel: String,
    val fixAgeLabel: String,
    val qualityLabel: String,
    val positionReason: String,
    val hasRoute: Boolean,
    val hasLocalDem: Boolean,
    val scale: MapScaleReading
) {
    companion object {
        fun from(
            settings: AppSettings,
            inputs: DataBarInputs,
            location: GpsUpdate?,
            locationState: CurrentLocationState,
            nav: NavigationState,
            summitBearing: Double?,
            declination: Double,
            magneticNorth: Boolean,
            hasRoute: Boolean,
            hasLocalDem: Boolean,
            mapLatitude: Double,
            mapZoom: Double,
            headingMagnetic: Double = 0.0,
            headingReady: Boolean = false
        ): MapFieldSnapshot {
            val loc = location ?: inputs.location
            val age = when {
                loc != null && !loc.observed && loc.verifiedAgeMs in 1 until Long.MAX_VALUE / 4 ->
                    "verified ${loc.verifiedAgeMs / 1000}s ago"
                locationState.timestamp > 0L -> {
                    val seconds = locationState.ageMs / 1000.0
                    if (seconds < 1.0) "now" else String.format("%.0fs", seconds)
                }
                else -> "—"
            }
            val accuracy = when {
                loc != null && loc.accuracy.isFinite() -> "±${loc.accuracy.toInt()} m"
                locationState.timestamp > 0L && locationState.horizontalAccuracy.isFinite() ->
                    "±${locationState.horizontalAccuracy.toInt()} m"
                else -> "no fix"
            }
            val coords = if (loc != null) {
                Coordinates.format(loc.latitude, loc.longitude, settings.coordinateFormat)
            } else {
                "—"
            }
            return MapFieldSnapshot(
                readings = DataBar.readings(settings, inputs.copy(location = loc, locationState = locationState, nav = nav)),
                location = loc,
                locationState = locationState,
                compass = CompassSnapshot(
                    headingMagnetic = headingMagnetic,
                    headingReady = headingReady,
                    course = nav.course.takeIf { it != 0.0 } ?: loc?.bearing?.toDouble() ?: 0.0,
                    bearing = nav.bearing,
                    summitBearing = summitBearing,
                    declination = declination,
                    magneticNorth = magneticNorth,
                    nextLabel = nav.nextLabel
                ),
                coordinates = coords,
                elevationLabel = loc?.let { FormatUtils.formatElevation(it.elevation, settings.elevationUnit) } ?: "—",
                accuracyLabel = accuracy,
                fixAgeLabel = age,
                qualityLabel = loc?.integrityLabel?.takeIf { it.isNotBlank() } ?: qualityLabel(locationState.quality),
                positionReason = loc?.positionReason.orEmpty(),
                hasRoute = hasRoute,
                hasLocalDem = hasLocalDem,
                scale = MapScale.reading(
                    latitude = loc?.latitude ?: mapLatitude,
                    zoom = mapZoom,
                    targetPixels = 72.0,
                    metric = settings.distanceUnit == DistanceUnit.KILOMETERS
                )
            )
        }
    }
}
