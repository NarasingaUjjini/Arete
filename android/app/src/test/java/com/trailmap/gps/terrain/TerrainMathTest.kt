package com.trailmap.gps.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TerrainMathTest {
    private fun rampGrid(): DemGrid {
        val cols = 9
        val rows = 9
        val elev = FloatArray(cols * rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                elev[row * cols + col] = 1000f + col * 10f
            }
        }
        return DemGrid(-119.01, 37.0, -119.0, 37.01, cols, rows, elev)
    }

    @Test
    fun interpolatesInteriorPoint() {
        val grid = rampGrid()
        val z = grid.interpolate(37.005, -119.005)
        assertNotNull(z)
        assertTrue(z!! in 1000.0..1090.0)
    }

    @Test
    fun eastFacingRampHasEasterlyAspect() {
        val grid = rampGrid()
        val cell = TerrainMath.analyzeCell(grid, 4, 4)
        assertNotNull(cell)
        val aspect = cell!!.aspectDegrees
        assertTrue("aspect $aspect", aspect in 60.0..120.0 || aspect in 240.0..300.0)
        assertTrue(cell.slopeDegrees > 0.5)
    }

    @Test
    fun aspectLabelQuadrants() {
        assertEquals("N", TerrainMath.aspectLabel(0.0))
        assertEquals("E", TerrainMath.aspectLabel(90.0))
        assertEquals("S", TerrainMath.aspectLabel(180.0))
        assertEquals("W", TerrainMath.aspectLabel(270.0))
    }

    @Test
    fun slopeAndAspectOverlaysLeaveGapsForTheBasemap() {
        val pixels = TerrainMath.overlayPixels(rampGrid(), TerrainOverlay.ASPECT)
        val transparent = pixels.count { it ushr 24 == 0 }
        assertTrue("expected hatch gaps, transparent=$transparent", transparent > pixels.size / 3)
    }

    fun routeCorridorClearsOverlayCells() {
        val grid = rampGrid()
        val mask = TerrainMath.routeClearMask(grid, listOf(37.005 to -119.005), radiusCells = 2)
        assertTrue(mask.any { it })
        val pixels = TerrainMath.overlayPixels(grid, TerrainOverlay.SLOPE, mask)
        val cleared = mask.indices.count { mask[it] && pixels[it] == 0 }
        assertTrue(cleared > 0)
    }

    fun hillshadeIsUnitInterval() {
        val shade = TerrainMath.hillshade(Math.toRadians(20.0), 90.0)
        assertTrue(shade in 0.0..1.0)
    }

    @Test
    fun demRoundTripPreservesValues() {
        val original = rampGrid()
        val file = File.createTempFile("arete", ".dem")
        original.writeTo(file)
        val loaded = DemGrid.readFrom(file)
        file.delete()
        assertEquals(original.cols, loaded.cols)
        assertEquals(original.rows, loaded.rows)
        assertEquals(original.elevations[10], loaded.elevations[10], 0.001f)
    }

    @Test
    fun floatTiffParserReadsUncompressedStrip() {
        val cols = 2
        val rows = 2
        val values = floatArrayOf(1000f, 1100f, 1200f, 1300f)
        val bytes = buildFloatTiff(cols, rows, values)
        val parsed = Usgs3depProvider.parseFloatTiff(bytes, cols, rows)
        assertEquals(1000f, parsed[0], 0.01f)
        assertEquals(1300f, parsed[3], 0.01f)
    }

    @Test
    fun verticalSpeedUsesWindowNotInstant() {
        val tracker = VerticalSpeedTracker(windowMs = 180_000, minSpanMs = 40_000)
        tracker.add(0L, 1000.0)
        tracker.add(1_000L, 1002.0)
        assertTrue(tracker.metersPerHour(1_000L) == null)
        tracker.add(60_000L, 1030.0)
        val rate = tracker.metersPerHour(60_000L)!!
        assertTrue(rate > 1000.0)
    }

    private fun buildFloatTiff(cols: Int, rows: Int, values: FloatArray): ByteArray {
        val header = 8
        val ifdCount = 8
        val ifdSize = 2 + ifdCount * 12 + 4
        val dataOffset = header + ifdSize
        val out = java.nio.ByteBuffer.allocate(dataOffset + values.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.put('I'.code.toByte())
        out.put('I'.code.toByte())
        out.putShort(42)
        out.putInt(header)
        out.putShort(ifdCount.toShort())
        fun entry(tag: Int, type: Int, count: Int, value: Int) {
            out.putShort(tag.toShort())
            out.putShort(type.toShort())
            out.putInt(count)
            out.putInt(value)
        }
        entry(256, 3, 1, cols)
        entry(257, 3, 1, rows)
        entry(258, 3, 1, 32)
        entry(259, 3, 1, 1)
        entry(273, 4, 1, dataOffset)
        entry(277, 3, 1, 1)
        entry(279, 4, 1, values.size * 4)
        entry(339, 3, 1, 3)
        out.putInt(0)
        values.forEach { out.putFloat(it) }
        return out.array()
    }
}
