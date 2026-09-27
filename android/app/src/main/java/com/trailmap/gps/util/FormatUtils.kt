package com.trailmap.gps.util

import com.trailmap.gps.data.DistanceUnit
import com.trailmap.gps.data.ElevationUnit
import com.trailmap.gps.data.TrackPoint
import kotlin.math.atan2
import kotlin.math.roundToInt

object FormatUtils {
    fun formatDistance(meters: Double, unit: DistanceUnit): String {
        return when (unit) {
            DistanceUnit.MILES -> String.format("%.1f mi", meters / 1609.344)
            DistanceUnit.KILOMETERS -> String.format("%.1f km", meters / 1000.0)
        }
    }

    fun formatElevation(meters: Double, unit: ElevationUnit): String {
        return when (unit) {
            ElevationUnit.FEET -> String.format("%,d ft", (meters * 3.28084).roundToInt())
            ElevationUnit.METERS -> String.format("%,d m", meters.roundToInt())
        }
    }

    fun formatSpeed(metersPerSecond: Float, unit: DistanceUnit): String {
        return when (unit) {
            DistanceUnit.MILES -> String.format("%.1f mph", metersPerSecond * 2.23694f)
            DistanceUnit.KILOMETERS -> String.format("%.1f km/h", metersPerSecond * 3.6f)
        }
    }

    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs <= 0L) return "Never"
        val elapsed = (nowMs - timestampMs).coerceAtLeast(0L)
        val minutes = elapsed / 60_000
        val hours = elapsed / 3_600_000
        val days = elapsed / 86_400_000
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "Yesterday"
            else -> "${days}d ago"
        }
    }

    fun isStale(timestampMs: Long, maxAgeMs: Long = 24 * 60 * 60 * 1000L): Boolean {
        if (timestampMs <= 0L) return true
        return System.currentTimeMillis() - timestampMs > maxAgeMs
    }

    fun formatBearing(degrees: Double): String = "${degrees.roundToInt()}°"

    fun formatCoordinates(lat: Double, lon: Double, format: com.trailmap.gps.data.CoordinateFormat): String =
        com.trailmap.gps.geo.Coordinates.format(lat, lon, format)

    fun formatCoordinates(lat: Double, lon: Double, decimal: Boolean): String {
        return formatCoordinates(
            lat,
            lon,
            if (decimal) com.trailmap.gps.data.CoordinateFormat.DECIMAL
            else com.trailmap.gps.data.CoordinateFormat.UTM
        )
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}

object NavigationUtils {
    fun distanceToPoint(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return com.trailmap.gps.importing.RouteParser.haversine(lat1, lon1, lat2, lon2)
    }

    fun bearingToPoint(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2Rad)
        val x = kotlin.math.cos(lat1Rad) * kotlin.math.sin(lat2Rad) -
            kotlin.math.sin(lat1Rad) * kotlin.math.cos(lat2Rad) * kotlin.math.cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun findNearestPointIndex(points: List<TrackPoint>, lat: Double, lon: Double): Int {
        var minDist = Double.MAX_VALUE
        var minIndex = 0
        points.forEachIndexed { index, point ->
            val dist = distanceToPoint(lat, lon, point.lat, point.lon)
            if (dist < minDist) {
                minDist = dist
                minIndex = index
            }
        }
        return minIndex
    }

    fun remainingDistance(points: List<TrackPoint>, fromIndex: Int): Double {
        if (fromIndex >= points.size - 1) return 0.0
        var total = 0.0
        for (i in fromIndex until points.size - 1) {
            total += distanceToPoint(
                points[i].lat, points[i].lon,
                points[i + 1].lat, points[i + 1].lon
            )
        }
        return total
    }

    fun boundsFromPoints(points: List<TrackPoint>, paddingFactor: Double = 0.15): DoubleArray {
        if (points.isEmpty()) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        var minLat = points.first().lat
        var maxLat = points.first().lat
        var minLon = points.first().lon
        var maxLon = points.first().lon
        points.forEach { p ->
            minLat = minOf(minLat, p.lat)
            maxLat = maxOf(maxLat, p.lat)
            minLon = minOf(minLon, p.lon)
            maxLon = maxOf(maxLon, p.lon)
        }
        val latSpan = (maxLat - minLat).coerceAtLeast(0.002)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.002)
        val padLat = latSpan * paddingFactor
        val padLon = lonSpan * paddingFactor
        return doubleArrayOf(minLon - padLon, minLat - padLat, maxLon + padLon, maxLat + padLat)
    }
}
