package com.trailmap.gps.geo

import com.trailmap.gps.data.RouteStats
import com.trailmap.gps.data.TrackPoint
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min

object ElevationStats {
    const val DEFAULT_GAIN_THRESHOLD_M = 3.0

    fun enrich(points: List<TrackPoint>): List<TrackPoint> {
        if (points.isEmpty()) return emptyList()
        val cumDist = RouteGeometry.cumulativeDistances(points)
        var gain = 0.0
        var loss = 0.0
        val out = ArrayList<TrackPoint>(points.size)
        out.add(
            points[0].copy(
                cumulativeDistanceMeters = 0.0,
                cumulativeElevationGainMeters = 0.0,
                cumulativeElevationLossMeters = 0.0
            )
        )
        for (i in 1 until points.size) {
            val delta = points[i].elevation - points[i - 1].elevation
            if (delta > 0) gain += delta else loss += -delta
            out.add(
                points[i].copy(
                    cumulativeDistanceMeters = cumDist[i],
                    cumulativeElevationGainMeters = gain,
                    cumulativeElevationLossMeters = loss
                )
            )
        }
        return out
    }

    fun calculate(points: List<TrackPoint>, gainThresholdMeters: Double = DEFAULT_GAIN_THRESHOLD_M): RouteStats {
        if (points.size < 2) {
            val ele = points.firstOrNull()?.elevation ?: 0.0
            return RouteStats(
                distanceMeters = 0.0,
                elevationGainMeters = 0.0,
                elevationLossMeters = 0.0,
                maxElevationMeters = ele,
                minElevationMeters = ele,
                maxGradePercent = 0.0,
                averageGradePercent = 0.0,
                estimatedTimeSeconds = 0
            )
        }
        val filtered = smoothElevations(points)
        var distance = 0.0
        var gain = 0.0
        var loss = 0.0
        var pending = 0.0
        var maxEle = filtered[0].elevation
        var minEle = filtered[0].elevation
        var maxGrade = 0.0

        for (i in 1 until filtered.size) {
            val prev = filtered[i - 1]
            val curr = filtered[i]
            val step = GeoMath.haversineMeters(prev.lat, prev.lon, curr.lat, curr.lon)
            distance += step
            val delta = curr.elevation - prev.elevation
            pending += delta
            if (pending > gainThresholdMeters) {
                gain += pending
                pending = 0.0
            } else if (pending < -gainThresholdMeters) {
                loss += -pending
                pending = 0.0
            }
            maxEle = max(maxEle, curr.elevation)
            minEle = min(minEle, curr.elevation)
            if (step > 1.0) {
                val grade = abs(delta / step) * 100.0
                if (grade.isFinite()) maxGrade = max(maxGrade, grade)
            }
        }
        val net = filtered.last().elevation - filtered.first().elevation
        val avgGrade = if (distance > 1.0) (net / distance) * 100.0 else 0.0
        return RouteStats(
            distanceMeters = distance,
            elevationGainMeters = gain,
            elevationLossMeters = loss,
            maxElevationMeters = maxEle,
            minElevationMeters = minEle,
            maxGradePercent = maxGrade,
            averageGradePercent = avgGrade,
            estimatedTimeSeconds = 0
        )
    }

    fun remainingAscent(points: List<TrackPoint>, distanceAlongMeters: Double): Double {
        if (points.size < 2) return 0.0
        val enriched = if (points[0].cumulativeDistanceMeters == 0.0 && points.size > 1 &&
            points.last().cumulativeDistanceMeters > 0.0
        ) points else enrich(points)
        var ascent = 0.0
        for (i in 1 until enriched.size) {
            val prev = enriched[i - 1]
            val curr = enriched[i]
            if (curr.cumulativeDistanceMeters <= distanceAlongMeters) continue
            val start = max(prev.cumulativeDistanceMeters, distanceAlongMeters)
            val span = curr.cumulativeDistanceMeters - prev.cumulativeDistanceMeters
            if (span <= 0.0) continue
            val t0 = ((start - prev.cumulativeDistanceMeters) / span).coerceIn(0.0, 1.0)
            val ele0 = prev.elevation + (curr.elevation - prev.elevation) * t0
            val delta = curr.elevation - ele0
            if (delta > 0) ascent += delta
        }
        return ascent
    }

    fun slice(points: List<TrackPoint>, fromMeters: Double, toMeters: Double): RouteStats {
        if (points.size < 2) return calculate(points)
        val enriched = enrich(points)
        val lo = min(fromMeters, toMeters)
        val hi = max(fromMeters, toMeters)
        val span = enriched.filter { it.cumulativeDistanceMeters in lo..hi }
        val withEnds = if (span.size >= 2) span else enriched
        return calculate(withEnds)
    }

    fun pointAtDistance(points: List<TrackPoint>, distanceMeters: Double): TrackPoint? {
        if (points.isEmpty()) return null
        val enriched = if (points.last().cumulativeDistanceMeters > 0) points else enrich(points)
        if (enriched.size == 1) return enriched.first()
        val target = distanceMeters.coerceIn(0.0, enriched.last().cumulativeDistanceMeters)
        val after = enriched.indexOfFirst { it.cumulativeDistanceMeters >= target }.let { if (it < 0) enriched.lastIndex else it }
        if (after <= 0) return enriched.first()
        val b = enriched[after]
        val a = enriched[after - 1]
        val span = (b.cumulativeDistanceMeters - a.cumulativeDistanceMeters).coerceAtLeast(1e-6)
        val t = ((target - a.cumulativeDistanceMeters) / span).coerceIn(0.0, 1.0)
        return TrackPoint(
            lat = a.lat + (b.lat - a.lat) * t,
            lon = a.lon + (b.lon - a.lon) * t,
            elevation = a.elevation + (b.elevation - a.elevation) * t,
            cumulativeDistanceMeters = target,
            cumulativeElevationGainMeters = a.cumulativeElevationGainMeters +
                (b.cumulativeElevationGainMeters - a.cumulativeElevationGainMeters) * t,
            cumulativeElevationLossMeters = a.cumulativeElevationLossMeters +
                (b.cumulativeElevationLossMeters - a.cumulativeElevationLossMeters) * t
        )
    }

    fun gradePercent(riseMeters: Double, runMeters: Double): Double {
        if (runMeters <= 0.0) return 0.0
        return (riseMeters / runMeters) * 100.0
    }

    fun slopeDegrees(riseMeters: Double, runMeters: Double): Double {
        if (runMeters <= 0.0) return 0.0
        return Math.toDegrees(atan(riseMeters / runMeters))
    }

    fun smoothElevations(points: List<TrackPoint>, window: Int = 3): List<TrackPoint> {
        if (points.size < 3 || window < 2) return points
        val half = window / 2
        return points.mapIndexed { i, p ->
            val from = max(0, i - half)
            val to = min(points.lastIndex, i + half)
            var sum = 0.0
            var n = 0
            for (j in from..to) {
                if (points[j].elevation != 0.0 || points.any { it.elevation != 0.0 }) {
                    sum += points[j].elevation
                    n += 1
                }
            }
            p.copy(elevation = if (n == 0) p.elevation else sum / n)
        }
    }
}
