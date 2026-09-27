package com.trailmap.gps.terrain

import org.junit.Assert.assertTrue
import org.junit.Test

class ContourGeneratorTest {
    @Test
    fun slopingGridProducesIndexAndIntermediateLines() {
        val cols = 8
        val rows = 8
        val elev = FloatArray(cols * rows) { i ->
            val col = i % cols
            (col * 40f)
        }
        val grid = DemGrid(-120.1, 47.0, -120.0, 47.1, cols, rows, elev)
        val lines = ContourGenerator.generate(grid, intervalMeters = 40.0)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.index })
        assertTrue(lines.all { it.points.size == 2 })
        val json = ContourGenerator.toGeoJson(grid)
        assertTrue(json.contains("FeatureCollection"))
        assertTrue(json.contains("\"ele\""))
    }

    @Test
    fun flatGridHasNoContours() {
        val elev = FloatArray(9) { 1000f }
        val grid = DemGrid(0.0, 0.0, 1.0, 1.0, 3, 3, elev)
        assertTrue(ContourGenerator.generate(grid).isEmpty())
    }
}
