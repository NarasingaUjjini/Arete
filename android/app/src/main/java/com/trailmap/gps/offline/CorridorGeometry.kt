package com.trailmap.gps.offline

import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.geo.GeoMath
import com.trailmap.gps.geo.RouteGeometry
import kotlin.math.cos

enum class CorridorBuffer(val meters: Double, val label: String) {
    QUARTER(402.0, "0.25 mi"),
    HALF(805.0, "0.5 mi"),
    ONE(1609.0, "1 mi"),
    TWO(3219.0, "2 mi"),
    FIVE(8047.0, "5 mi");

    companion object {
        fun nearest(meters: Double): CorridorBuffer =
            entries.minBy { kotlin.math.abs(it.meters - meters) }
    }
}

object CorridorGeometry {
    fun bufferBounds(points: List<TrackPoint>, bufferMeters: Double): BoundingBox {
        require(points.isNotEmpty())
        var minLat = points.minOf { it.lat }
        var maxLat = points.maxOf { it.lat }
        var minLon = points.minOf { it.lon }
        var maxLon = points.maxOf { it.lon }
        val midLat = (minLat + maxLat) / 2.0
        val dLat = metersToLatDegrees(bufferMeters)
        val dLon = metersToLonDegrees(bufferMeters, midLat)
        return BoundingBox(minLon - dLon, minLat - dLat, maxLon + dLon, maxLat + dLat)
    }

    fun metersToLatDegrees(meters: Double): Double = meters / 111_320.0

    fun metersToLonDegrees(meters: Double, latitude: Double): Double {
        val scale = cos(Math.toRadians(latitude)).coerceAtLeast(0.15)
        return meters / (111_320.0 * scale)
    }

    fun withinCorridor(lat: Double, lon: Double, points: List<TrackPoint>, bufferMeters: Double): Boolean {
        if (points.isEmpty()) return false
        if (points.size == 1) {
            return GeoMath.haversineMeters(lat, lon, points[0].lat, points[0].lon) <= bufferMeters
        }
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val d = RouteGeometry.nearestOnSegment(lat, lon, a.lat, a.lon, b.lat, b.lon).distance
            if (d < best) best = d
            if (best <= bufferMeters) return true
        }
        return best <= bufferMeters
    }

    fun tileCenter(z: Int, x: Int, y: Int): Pair<Double, Double> {
        val n = 1 shl z
        val lon = x / n.toDouble() * 360.0 - 180.0 + 180.0 / n
        val latRad = Math.atan(Math.sinh(Math.PI * (1 - 2.0 * (y + 0.5) / n)))
        return Math.toDegrees(latRad) to lon
    }
}
