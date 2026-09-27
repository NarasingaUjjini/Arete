package com.trailmap.gps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayerTest {
    @Test
    fun oldTopoSettingMigratesToAreteTopo() {
        assertEquals(MapLayer.ARETE_TOPO, MapLayer.fromStored("TOPO"))
        assertEquals(MapLayer.ARETE_TOPO, MapLayer.fromStored(null))
        assertEquals(MapLayer.OSM, MapLayer.fromStored("OSM"))
    }

    @Test
    fun paidOrOsmStacksAreOnlineOnly() {
        assertTrue(MapLayer.OPENTOPO.onlineOnly)
        assertTrue(MapLayer.SATELLITE.onlineOnly)
        assertTrue(MapLayer.OSM.onlineOnly)
        assertFalse(MapLayer.ARETE_TOPO.onlineOnly)
    }
}
