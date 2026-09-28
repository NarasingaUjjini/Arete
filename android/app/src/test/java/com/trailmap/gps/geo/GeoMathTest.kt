package com.trailmap.gps.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun destinationNorthIsAboutTheAskedDistance() {
        val startLat = 37.0
        val startLon = -119.0
        val (lat, lon) = GeoMath.destination(startLat, startLon, 0.0, 100.0)
        val meters = GeoMath.haversineMeters(startLat, startLon, lat, lon)
        assertEquals(100.0, meters, 1.0)
        assertTrue(lat > startLat)
        assertEquals(startLon, lon, 0.00001)
    }
}
