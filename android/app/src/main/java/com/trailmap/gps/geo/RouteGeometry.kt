package com.trailmap.gps.geo

import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.Waypoint
import kotlin.math.cos
import kotlin.math.max

data class RouteProjection(
    val segmentIndex: Int,
    val nearestLat: Double,
    val nearestLon: Double,
    val distanceToRouteMeters: Double,
    val distanceAlongRouteMeters: Double,
    val remainingDistanceMeters: Double,
    val progress: Double,
    val totalDistanceMeters: Double
)

object RouteGeometry {
    fun cumulativeDistances(points: List<TrackPoint>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<Double>(points.size)
        var total = 0.0
        out.add(0.0)
        for (i in 1 until points.size) {
            total += GeoMath.haversineMeters(
                points[i - 1].lat, points[i - 1].lon,
                points[i].lat, points[i].lon
            )
            out.add(total)
        }
        return out
    }

    fun totalDistance(points: List<TrackPoint>): Double =
        cumulativeDistances(points).lastOrNull() ?: 0.0

    fun project(points: List<TrackPoint>, lat: Double, lon: Double): RouteProjection? {
        if (points.isEmpty()) return null
        if (points.size == 1) {
            val d = GeoMath.haversineMeters(lat, lon, points[0].lat, points[0].lon)
            return RouteProjection(0, points[0].lat, points[0].lon, d, 0.0, 0.0, 1.0, 0.0)
        }
        val cum = cumulativeDistances(points)
        val total = cum.last()
        var bestDist = Double.POSITIVE_INFINITY
        var bestSeg = 0
        var bestLat = points[0].lat
        var bestLon = points[0].lon
        var bestAlong = 0.0

        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val nearest = nearestOnSegment(lat, lon, a.lat, a.lon, b.lat, b.lon)
            if (nearest.distance < bestDist) {
                bestDist = nearest.distance
                bestSeg = i
                bestLat = nearest.lat
                bestLon = nearest.lon
                val segLen = GeoMath.haversineMeters(a.lat, a.lon, b.lat, b.lon)
                bestAlong = cum[i] + nearest.t * segLen
            }
        }
        val remaining = max(0.0, total - bestAlong)
        val progress = if (total > 0.0) (bestAlong / total).coerceIn(0.0, 1.0) else 0.0
        return RouteProjection(
            segmentIndex = bestSeg,
            nearestLat = bestLat,
            nearestLon = bestLon,
            distanceToRouteMeters = bestDist,
            distanceAlongRouteMeters = bestAlong,
            remainingDistanceMeters = remaining,
            progress = progress,
            totalDistanceMeters = total
        )
    }

    /**
     * Remaining route distance plus the hop from the user to the projected point.
     */
    fun remainingFromUser(projection: RouteProjection): Double =
        projection.distanceToRouteMeters + projection.remainingDistanceMeters

    fun nextWaypointAhead(
        waypoints: List<Waypoint>,
        points: List<TrackPoint>,
        distanceAlongRouteMeters: Double,
        reachedSlackMeters: Double = 15.0
    ): Pair<Waypoint, Double>? {
        if (waypoints.isEmpty() || points.isEmpty()) return null
        val ahead = waypoints.mapNotNull { waypoint ->
            val along = project(points, waypoint.lat, waypoint.lon)?.distanceAlongRouteMeters
                ?: return@mapNotNull null
            if (along > distanceAlongRouteMeters + reachedSlackMeters) waypoint to along else null
        }
        return ahead.minByOrNull { it.second }
    }

    data class NearestOnSegment(
        val lat: Double,
        val lon: Double,
        val t: Double,
        val distance: Double
    )

    fun nearestOnSegment(
        lat: Double,
        lon: Double,
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): NearestOnSegment {
        val midLat = Math.toRadians((lat1 + lat2) / 2.0)
        val x1 = 0.0
        val y1 = 0.0
        val x2 = (lon2 - lon1) * cos(midLat)
        val y2 = lat2 - lat1
        val x = (lon - lon1) * cos(midLat)
        val y = lat - lat1
        val len2 = x2 * x2 + y2 * y2
        val t = if (len2 <= 0.0) 0.0 else ((x * x2 + y * y2) / len2).coerceIn(0.0, 1.0)
        val nx = x1 + t * x2
        val ny = y1 + t * y2
        val nLat = lat1 + ny
        val nLon = lon1 + nx / cos(midLat).coerceAtLeast(1e-6)
        val dist = GeoMath.haversineMeters(lat, lon, nLat, nLon)
        return NearestOnSegment(nLat, nLon, t, dist)
    }

    fun offRouteState(
        previousOffRoute: Boolean,
        distanceToRoute: Double,
        accuracyMeters: Float,
        enterThresholdMeters: Double,
        exitThresholdMeters: Double
    ): OffRouteDecision {
        val accuracy = accuracyMeters.toDouble().coerceAtLeast(0.0)
        if (accuracy >= enterThresholdMeters) {
            return OffRouteDecision(
                isOffRoute = false,
                gpsDegraded = true,
                message = "GPS accuracy degraded"
            )
        }
        return if (previousOffRoute) {
            if (distanceToRoute <= exitThresholdMeters) {
                OffRouteDecision(isOffRoute = false, gpsDegraded = false, message = null)
            } else {
                OffRouteDecision(isOffRoute = true, gpsDegraded = false, message = null)
            }
        } else {
            if (distanceToRoute > enterThresholdMeters) {
                OffRouteDecision(isOffRoute = true, gpsDegraded = false, message = null)
            } else {
                OffRouteDecision(isOffRoute = false, gpsDegraded = false, message = null)
            }
        }
    }
}

data class OffRouteDecision(
    val isOffRoute: Boolean,
    val gpsDegraded: Boolean,
    val message: String?
)

enum class OffRouteCorridor(val enterMeters: Double, val exitMeters: Double) {
    NARROW(25.0, 16.0),
    NORMAL(45.0, 30.0),
    WIDE(80.0, 55.0)
}
