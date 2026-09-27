package com.trailmap.gps.geo

import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.Waypoint
import kotlin.math.abs

data class RouteSegment(
    val fromName: String,
    val toName: String,
    val fromAlongMeters: Double,
    val toAlongMeters: Double,
    val distanceMeters: Double,
    val gainMeters: Double,
    val lossMeters: Double,
    val maxGradePercent: Double,
    val toKind: WaypointKind
)

object RouteSegments {
    fun build(points: List<TrackPoint>, waypoints: List<Waypoint>): List<RouteSegment> {
        if (points.size < 2) return emptyList()
        val points = withCumulative(points)
        val ordered = orderedCheckpoints(points, waypoints)
        if (ordered.size < 2) {
            val start = points.first()
            val end = points.last()
            val stats = ElevationStats.calculate(points)
            return listOf(
                RouteSegment(
                    fromName = "Start",
                    toName = "End",
                    fromAlongMeters = 0.0,
                    toAlongMeters = stats.distanceMeters,
                    distanceMeters = stats.distanceMeters,
                    gainMeters = stats.elevationGainMeters,
                    lossMeters = stats.elevationLossMeters,
                    maxGradePercent = stats.maxGradePercent,
                    toKind = WaypointKind.CUSTOM
                )
            )
        }
        val out = mutableListOf<RouteSegment>()
        for (i in 0 until ordered.lastIndex) {
            val a = ordered[i]
            val b = ordered[i + 1]
            val slice = points.filter {
                it.cumulativeDistanceMeters >= a.second - 0.5 &&
                    it.cumulativeDistanceMeters <= b.second + 0.5
            }.ifEmpty { points }
            val stats = ElevationStats.calculate(slice)
            out += RouteSegment(
                fromName = a.first.name.ifBlank { a.first.kind().label },
                toName = b.first.name.ifBlank { b.first.kind().label },
                fromAlongMeters = a.second,
                toAlongMeters = b.second,
                distanceMeters = (b.second - a.second).coerceAtLeast(0.0),
                gainMeters = stats.elevationGainMeters,
                lossMeters = stats.elevationLossMeters,
                maxGradePercent = stats.maxGradePercent,
                toKind = b.first.kind()
            )
        }
        return out
    }

    private fun withCumulative(points: List<TrackPoint>): List<TrackPoint> {
        val cum = RouteGeometry.cumulativeDistances(points)
        return points.mapIndexed { i, p -> p.copy(cumulativeDistanceMeters = cum[i]) }
    }

    fun orderedCheckpoints(
        points: List<TrackPoint>,
        waypoints: List<Waypoint>
    ): List<Pair<Waypoint, Double>> {
        val start = Waypoint("Trailhead", points.first().lat, points.first().lon, points.first().elevation, "Trailhead")
        val endName = waypoints.firstOrNull { it.kind() == WaypointKind.SUMMIT }?.name ?: "End"
        val end = Waypoint(endName, points.last().lat, points.last().lon, points.last().elevation, "Custom")
        val along = waypoints.mapNotNull { wp ->
            val proj = RouteGeometry.project(points, wp.lat, wp.lon) ?: return@mapNotNull null
            if (proj.distanceToRouteMeters > 400) return@mapNotNull null
            wp to proj.distanceAlongRouteMeters
        }.sortedBy { it.second }
        val withEnds = mutableListOf(start to 0.0)
        along.forEach { candidate ->
            if (withEnds.none { abs(it.second - candidate.second) < 40 }) {
                withEnds += candidate
            }
        }
        val total = RouteGeometry.totalDistance(points)
        if (withEnds.none { abs(it.second - total) < 40 }) {
            withEnds += end to total
        }
        return withEnds.sortedBy { it.second }
    }

    fun summit(points: List<TrackPoint>, waypoints: List<Waypoint>): Waypoint? {
        waypoints.firstOrNull { it.kind() == WaypointKind.SUMMIT }?.let { return it }
        val peak = points.maxByOrNull { it.elevation } ?: return null
        if (peak.elevation <= 0.0) return null
        return Waypoint("Highest point", peak.lat, peak.lon, peak.elevation, "Summit")
    }

    fun bailouts(waypoints: List<Waypoint>): List<Waypoint> =
        waypoints.filter { it.kind() == WaypointKind.BAILOUT || it.kind() == WaypointKind.ALTERNATE }

    fun turnaround(waypoints: List<Waypoint>): Waypoint? =
        waypoints.firstOrNull { it.kind() == WaypointKind.TURNAROUND }
}
