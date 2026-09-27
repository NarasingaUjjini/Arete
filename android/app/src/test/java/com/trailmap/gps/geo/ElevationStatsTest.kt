package com.trailmap.gps.geo

import com.trailmap.gps.data.TrackPoint
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationStatsTest {
    @Test
    fun ignoresSubThresholdGpsJitter() {
        val points = listOf(
            TrackPoint(37.0, -119.0, 1000.0),
            TrackPoint(37.001, -119.0, 1001.0),
            TrackPoint(37.002, -119.0, 1000.0),
            TrackPoint(37.003, -119.0, 1001.0)
        )
        val stats = ElevationStats.calculate(points, gainThresholdMeters = 3.0)
        assertTrue(stats.elevationGainMeters < 3.0)
    }

    @Test
    fun countsRealClimb() {
        val points = listOf(
            TrackPoint(37.0, -119.0, 1000.0),
            TrackPoint(37.01, -119.0, 1200.0)
        )
        val stats = ElevationStats.calculate(points, gainThresholdMeters = 3.0)
        assertTrue(stats.elevationGainMeters > 100.0)
    }

    @Test
    fun pointAtDistanceIsAlongXAxis() {
        val points = ElevationStats.enrich(
            listOf(
                TrackPoint(37.0, -119.0, 1000.0),
                TrackPoint(37.01, -119.0, 1200.0)
            )
        )
        val mid = ElevationStats.pointAtDistance(points, points.last().cumulativeDistanceMeters / 2.0)!!
        assertTrue(mid.elevation in 1050.0..1150.0)
    }
}
