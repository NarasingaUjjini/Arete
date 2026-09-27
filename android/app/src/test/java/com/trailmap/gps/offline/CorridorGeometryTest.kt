package com.trailmap.gps.offline

import com.trailmap.gps.data.TrackPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorridorGeometryTest {
    private val route = listOf(
        TrackPoint(37.0, -119.0),
        TrackPoint(37.0, -118.99),
        TrackPoint(37.01, -118.99)
    )

    @Test
    fun pointOnRouteIsInsideBuffer() {
        assertTrue(CorridorGeometry.withinCorridor(37.0, -118.995, route, 200.0))
    }

    @Test
    fun farPointIsOutsideBuffer() {
        assertFalse(CorridorGeometry.withinCorridor(37.2, -119.3, route, 400.0))
    }

    @Test
    fun bufferBoundsExpandRoute() {
        val box = CorridorGeometry.bufferBounds(route, 1609.0)
        assertTrue(box.minLat < 37.0)
        assertTrue(box.maxLat > 37.01)
        assertTrue(box.minLon < -119.0)
        assertTrue(box.maxLon > -118.99)
    }
}
