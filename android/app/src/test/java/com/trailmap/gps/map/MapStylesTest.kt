package com.trailmap.gps.map

import com.trailmap.gps.data.MapLayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStylesTest {
    @Test
    fun historicalStyleKeepsUsgsBaseAndClassicSheets() {
        val json = MapStyles.styleJson(MapLayer.HISTORICAL)
        assertTrue(json.contains(MapStyles.USGS_TOPO_URL))
        assertTrue(json.contains(MapStyles.USA_TOPO_URL))
        assertFalse(json.contains("ustOverlayAuto/MapServer/tile"))
        assertTrue(json.contains("\"background-color\": \"#d6c9a8\""))
    }

    @Test
    fun usgsRasterLayersUseLinearResampling() {
        val layers = listOf(
            MapLayer.ARETE_TOPO,
            MapLayer.USGS_TOPO,
            MapLayer.IMAGERY,
            MapLayer.HISTORICAL
        )
        layers.forEach { layer ->
            val json = MapStyles.styleJson(layer)
            assertTrue(layer.name, json.contains("\"raster-resampling\": \"linear\""))
        }
    }
}
