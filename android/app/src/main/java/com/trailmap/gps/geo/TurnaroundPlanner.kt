package com.trailmap.gps.geo

import com.trailmap.gps.conditions.Daylight
import com.trailmap.gps.data.TrackPoint

data class TurnaroundPlan(
    val nowMs: Long,
    val returnByMs: Long?,
    val sunsetMs: Long?,
    val remainingOutMeters: Double,
    val remainingBackMeters: Double,
    val paceMetersPerSecond: Double?,
    val estimatedOutMs: Long?,
    val estimatedBackMs: Long?,
    val latestTurnaroundMs: Long?,
    val estimate: Boolean = true
)

object TurnaroundPlanner {
    fun plan(
        remainingOutMeters: Double,
        remainingBackMeters: Double,
        paceMetersPerSecond: Double?,
        lat: Double,
        lon: Double,
        returnByMs: Long?,
        nowMs: Long = System.currentTimeMillis()
    ): TurnaroundPlan {
        val pace = paceMetersPerSecond?.takeIf { it in 0.2..3.5 }
        val outMs = pace?.let { (remainingOutMeters / it * 1000.0).toLong() }
        val backMs = pace?.let { (remainingBackMeters / it * 1000.0).toLong() }
        val latest = when {
            returnByMs != null && backMs != null -> returnByMs - backMs
            else -> null
        }
        val sun = Daylight.calculate(lat, lon, nowMs)
        return TurnaroundPlan(
            nowMs = nowMs,
            returnByMs = returnByMs,
            sunsetMs = sun.sunsetMs,
            remainingOutMeters = remainingOutMeters,
            remainingBackMeters = remainingBackMeters,
            paceMetersPerSecond = pace,
            estimatedOutMs = outMs,
            estimatedBackMs = backMs,
            latestTurnaroundMs = latest,
            estimate = true
        )
    }

    fun movingPace(breadcrumbs: List<TrackPoint>, nowMs: Long = System.currentTimeMillis()): Double? {
        val recent = breadcrumbs.takeLast(24)
        if (recent.size < 3) return null
        val first = recent.first()
        val last = recent.last()
        val start = first.time ?: return null
        val end = last.time ?: nowMs
        val dt = (end - start) / 1000.0
        if (dt < 45) return null
        var dist = 0.0
        for (i in 1 until recent.size) {
            dist += GeoMath.haversineMeters(
                recent[i - 1].lat, recent[i - 1].lon,
                recent[i].lat, recent[i].lon
            )
        }
        if (dist < 40) return null
        return dist / dt
    }

    fun backtrackMeters(breadcrumbs: List<TrackPoint>, routePoints: List<TrackPoint>, corridorMeters: Double): Double {
        if (breadcrumbs.size < 2 || routePoints.size < 2) return 0.0
        var lastOn = -1
        breadcrumbs.forEachIndexed { i, p ->
            val proj = RouteGeometry.project(routePoints, p.lat, p.lon)
            if (proj != null && proj.distanceToRouteMeters <= corridorMeters) lastOn = i
        }
        if (lastOn < 0 || lastOn >= breadcrumbs.lastIndex) return 0.0
        var dist = 0.0
        for (i in lastOn until breadcrumbs.lastIndex) {
            dist += GeoMath.haversineMeters(
                breadcrumbs[i].lat, breadcrumbs[i].lon,
                breadcrumbs[i + 1].lat, breadcrumbs[i + 1].lon
            )
        }
        return dist
    }
}
