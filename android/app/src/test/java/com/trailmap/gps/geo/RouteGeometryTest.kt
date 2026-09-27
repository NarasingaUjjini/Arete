package com.trailmap.gps.geo

import com.trailmap.gps.data.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {
    private val route = listOf(
        TrackPoint(37.0, -119.0),
        TrackPoint(37.0, -118.99),
        TrackPoint(37.01, -118.99)
    )

    @Test
    fun projectsOntoSegmentNotVertex() {
        val midLon = -118.995
        val projection = RouteGeometry.project(route, 37.002, midLon)!!
        assertEquals(0, projection.segmentIndex)
        assertTrue(projection.distanceToRouteMeters < 300.0)
        assertTrue(projection.progress > 0.0 && projection.progress < 1.0)
    }

    @Test
    fun remainingIncludesHopToRoute() {
        val projection = RouteGeometry.project(route, 37.02, -118.99)!!
        val remaining = RouteGeometry.remainingFromUser(projection)
        assertTrue(remaining > projection.remainingDistanceMeters)
    }

    @Test
    fun uncertaintySuppressesOffRoute() {
        val decision = RouteGeometry.offRouteState(
            previousOffRoute = false,
            distanceToRoute = 52.0,
            accuracyMeters = 67f,
            enterThresholdMeters = 45.0,
            exitThresholdMeters = 30.0
        )
        assertFalse(decision.isOffRoute)
        assertTrue(decision.gpsDegraded)
    }

    @Test
    fun hysteresisPreventsFlicker() {
        val enter = RouteGeometry.offRouteState(false, 50.0, 5f, 45.0, 30.0)
        assertTrue(enter.isOffRoute)
        val stillOut = RouteGeometry.offRouteState(true, 35.0, 5f, 45.0, 30.0)
        assertTrue(stillOut.isOffRoute)
        val back = RouteGeometry.offRouteState(true, 20.0, 5f, 45.0, 30.0)
        assertFalse(back.isOffRoute)
    }

    @Test
    fun rejectsNullIsland() {
        assertTrue(GeoMath.isNullIsland(0.0, 0.0))
        assertFalse(GeoMath.isValidLatitude(91.0))
    }

    @Test
    fun nextWaypointIsAlongRouteNotNearest() {
        val waypoints = listOf(
            com.trailmap.gps.data.Waypoint("Corner", 37.0, -118.99, type = "Pass"),
            com.trailmap.gps.data.Waypoint("End", 37.01, -118.99, type = "Summit")
        )
        val first = RouteGeometry.nextWaypointAhead(waypoints, route, 0.0)!!
        assertEquals("Corner", first.first.name)
        val pastCorner = RouteGeometry.project(route, 37.005, -118.99)!!.distanceAlongRouteMeters
        val second = RouteGeometry.nextWaypointAhead(waypoints, route, pastCorner)!!
        assertEquals("End", second.first.name)
    }
}
