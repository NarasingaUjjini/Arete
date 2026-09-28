package com.trailmap.gps.ui.maptools

import com.trailmap.gps.data.MapChromeLayout
import com.trailmap.gps.map.MapScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapToolSessionTest {
    @Test
    fun tapSelectsAndTapAgainDismisses() {
        val opened = MapToolSession.next(null, MapTool.COMPASS)
        assertEquals(MapTool.COMPASS, opened)
        assertNull(MapToolSession.next(opened, MapTool.COMPASS))
        assertEquals(MapTool.LOCATION, MapToolSession.next(opened, MapTool.LOCATION))
    }

    @Test
    fun recInfoIsASharedDestination() {
        assertEquals("NPS/Rec Info", MapDestination.REC_INFO.label)
        assertTrue(MapDestination.entries.contains(MapDestination.REC_INFO))
    }

    @Test
    fun allLayoutsShareTheSameTools() {
        assertEquals(6, MapTool.entries.size)
        MapChromeLayout.entries.forEach { _ ->
            assertEquals(MapTool.entries.toList(), MapTool.mapTools)
        }
    }

    @Test
    fun headingUsesMagneticWhenAskedAndTrueWhenNot() {
        val magnetic = CompassSnapshot(
            headingMagnetic = 10.0,
            headingReady = true,
            course = 40.0,
            bearing = 80.0,
            summitBearing = null,
            declination = 15.0,
            magneticNorth = true,
            nextLabel = "CAMP"
        )
        assertEquals(10.0, magnetic.headingShown, 0.001)
        assertEquals(25.0, magnetic.courseShown, 0.001)
        val trueNorth = magnetic.copy(magneticNorth = false)
        assertEquals(25.0, trueNorth.headingShown, 0.001)
        assertEquals(40.0, trueNorth.courseShown, 0.001)
    }

    @Test
    fun defaultChromeIsRail() {
        assertEquals(MapChromeLayout.RAIL, MapChromeLayout.fromStored(null))
        assertEquals(MapChromeLayout.EDGE, MapChromeLayout.fromStored("EDGE"))
    }

    @Test
    fun scaleReadingUsesRoundDistances() {
        val metric = MapScale.reading(37.0, 12.0, 72.0, metric = true)
        assertTrue(metric.barMeters >= 5.0)
        assertTrue(metric.label.contains("m") || metric.label.contains("km"))
        val imperial = MapScale.reading(37.0, 12.0, 72.0, metric = false)
        assertTrue(imperial.label.contains("ft") || imperial.label.contains("mi"))
    }
}
