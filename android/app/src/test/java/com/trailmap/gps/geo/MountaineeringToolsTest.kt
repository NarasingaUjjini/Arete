package com.trailmap.gps.geo

import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MountaineeringToolsTest {
    @Test
    fun classifiesSummitAndBailout() {
        assertEquals(WaypointKind.SUMMIT, WaypointKind.from("Rainier Summit", "peak"))
        assertEquals(WaypointKind.BAILOUT, WaypointKind.from("Fuhrer Finger escape", "Bailout"))
        assertEquals(WaypointKind.TRAILHEAD, WaypointKind.from("Paradise TH", ""))
    }

    @Test
    fun segmentsFollowCheckpointsAlongRoute() {
        val points = (0..10).map { i ->
            TrackPoint(47.0 + i * 0.001, -121.0, 1000.0 + i * 20.0, cumulativeDistanceMeters = i * 100.0)
        }
        val waypoints = listOf(
            Waypoint("Camp", 47.004, -121.0, 1080.0, "Camp"),
            Waypoint("Summit", 47.009, -121.0, 1180.0, "Summit")
        )
        val segs = RouteSegments.build(points, waypoints)
        assertTrue(segs.size >= 2)
        assertTrue(segs.any { it.toName.contains("Camp", true) })
        assertTrue(segs.any { it.toKind == WaypointKind.SUMMIT || it.toName.contains("Summit", true) })
    }

    @Test
    fun turnaroundUsesPaceAndReturnBy() {
        val now = 1_700_000_000_000L
        val returnBy = now + 4 * 3_600_000L
        val plan = TurnaroundPlanner.plan(
            remainingOutMeters = 2000.0,
            remainingBackMeters = 4000.0,
            paceMetersPerSecond = 1.0,
            lat = 47.0,
            lon = -121.0,
            returnByMs = returnBy,
            nowMs = now
        )
        assertEquals(4_000_000L, plan.estimatedBackMs)
        assertEquals(returnBy - 4_000_000L, plan.latestTurnaroundMs)
        assertTrue(plan.estimate)
    }
}
