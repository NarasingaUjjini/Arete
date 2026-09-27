package com.trailmap.gps.data

enum class DataBarProfile {
    HIKING,
    MOUNTAINEERING,
    NAVIGATION,
    RECORDING,
    CUSTOM;

    val label: String
        get() = when (this) {
            HIKING -> "Hiking"
            MOUNTAINEERING -> "Mountaineering"
            NAVIGATION -> "Navigation"
            RECORDING -> "Recording"
            CUSTOM -> "Custom"
        }
}

enum class DataBarMetric(val label: String) {
    COORDINATES("Coordinates"),
    GPS_ACCURACY("GPS accuracy"),
    ELEVATION("Current elevation"),
    SPEED("Speed"),
    AVG_SPEED("Average speed"),
    DISTANCE_TRAVELED("Distance traveled"),
    DISTANCE_REMAINING("Distance remaining"),
    ASCENT("Ascent remaining"),
    DESCENT("Descent"),
    VERTICAL_SPEED("Vertical speed"),
    GRADE("Grade"),
    BEARING_WAYPOINT("Bearing to waypoint"),
    BEARING_SUMMIT("Bearing to summit"),
    PROGRESS("Route progress"),
    MOVING_TIME("Moving time"),
    TOTAL_TIME("Total time"),
    ETA("ETA"),
    SUNRISE("Sunrise"),
    SUNSET("Sunset"),
    DAYLIGHT("Daylight remaining"),
    TEMPERATURE("Temperature"),
    WIND("Wind"),
    BATTERY("Battery"),
    FIX_AGE("Fix age");

    companion object {
        fun slotsFor(profile: DataBarProfile): Triple<DataBarMetric, DataBarMetric, DataBarMetric> = when (profile) {
            DataBarProfile.HIKING -> Triple(ELEVATION, SPEED, DISTANCE_TRAVELED)
            DataBarProfile.MOUNTAINEERING -> Triple(ELEVATION, ASCENT, DAYLIGHT)
            DataBarProfile.NAVIGATION -> Triple(DISTANCE_REMAINING, BEARING_WAYPOINT, GPS_ACCURACY)
            DataBarProfile.RECORDING -> Triple(DISTANCE_TRAVELED, SPEED, ELEVATION)
            DataBarProfile.CUSTOM -> Triple(ELEVATION, SPEED, GPS_ACCURACY)
        }

        fun uniqueSlots(a: DataBarMetric, b: DataBarMetric, c: DataBarMetric): Triple<DataBarMetric, DataBarMetric, DataBarMetric> {
            val used = linkedSetOf(a)
            val second = if (b in used) entries.first { it !in used } else b
            used += second
            val third = if (c in used) entries.first { it !in used } else c
            return Triple(a, second, third)
        }
    }
}

enum class AccentTheme(val hex: String, val label: String) {
    ALPINE("#FF6B00", "Alpine (amber)"),
    ICE("#38BDF8", "Ice (cyan)"),
    AMBER("#FF7A1A", "Amber pulse"),
    SLATE("#F2F4F7", "Slate");

    companion object {
        fun fromStored(raw: String?): AccentTheme =
            entries.firstOrNull { it.name == raw } ?: ALPINE
    }
}

enum class OverlayStrength(val opacity: Float, val label: String) {
    LOW(0.28f, "Low"),
    MEDIUM(0.48f, "Medium"),
    HIGH(0.66f, "High")
}

enum class RouteLibraryFilter(val label: String) {
    ALL("All"),
    OFFLINE("Offline"),
    IMPORTED("Imported"),
    DRAWN("Drawn"),
    RECORDED("Recorded")
}

enum class DrawTool {
    NONE,
    ROUTE,
    WAYPOINT
}
