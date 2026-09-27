package com.trailmap.gps.geo

import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatesTest {
    @Test
    fun southernHemisphereUtmUsesSouth() {
        val text = Coordinates.utm(-33.8688, 151.2093)
        assertTrue(text.contains("S"))
        assertTrue(!text.startsWith("0"))
    }

    @Test
    fun northernHemisphereUtmUsesNorth() {
        val text = Coordinates.utm(37.7749, -122.4194)
        assertTrue(text.contains("N"))
    }

    @Test
    fun mgrsIsNonEmpty() {
        val text = Coordinates.mgrs(37.7749, -122.4194)
        assertTrue(text.length >= 10)
    }
}
