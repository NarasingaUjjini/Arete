package com.trailmap.gps.terrain

import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TerrainCell(
    val elevationMeters: Double,
    val slopeDegrees: Double,
    val aspectDegrees: Double,
    val hillshade: Double,
    val localReliefMeters: Double
)

object TerrainMath {
    const val NODATA = Float.NaN

    fun analyze(grid: DemGrid, lat: Double, lon: Double): TerrainCell? {
        val elevation = grid.interpolate(lat, lon) ?: return null
        val x = ((lon - grid.minLon) / grid.cellSizeX - 0.5)
        val y = ((grid.maxLat - lat) / grid.cellSizeY - 0.5)
        val col = x.toInt().coerceIn(1, grid.cols - 2)
        val row = y.toInt().coerceIn(1, grid.rows - 2)
        return analyzeCell(grid, row, col)?.copy(elevationMeters = elevation)
    }

    fun analyzeCell(grid: DemGrid, row: Int, col: Int): TerrainCell? {
        if (row < 1 || col < 1 || row >= grid.rows - 1 || col >= grid.cols - 1) return null
        val (cellX, cellY) = grid.cellSizeMeters()
        if (cellX <= 0.0 || cellY <= 0.0) return null
        val a = grid.elevationAt(row - 1, col - 1)
        val b = grid.elevationAt(row - 1, col)
        val c = grid.elevationAt(row - 1, col + 1)
        val d = grid.elevationAt(row, col - 1)
        val e = grid.elevationAt(row, col)
        val f = grid.elevationAt(row, col + 1)
        val g = grid.elevationAt(row + 1, col - 1)
        val h = grid.elevationAt(row + 1, col)
        val i = grid.elevationAt(row + 1, col + 1)
        if (listOf(a, b, c, d, e, f, g, h, i).any { it.isNaN() }) return null
        val dzdx = ((c + 2f * f + i) - (a + 2f * d + g)) / (8.0 * cellX)
        val dzdy = ((g + 2f * h + i) - (a + 2f * b + c)) / (8.0 * cellY)
        val slopeRad = atan(sqrt(dzdx * dzdx + dzdy * dzdy))
        val slopeDeg = Math.toDegrees(slopeRad)
        val aspectDeg = aspectDegrees(dzdx, dzdy)
        val shade = hillshade(slopeRad, aspectDeg, azimuthDegrees = 315.0, altitudeDegrees = 45.0)
        val values = floatArrayOf(a, b, c, d, e, f, g, h, i)
        val relief = (values.max() - values.min()).toDouble()
        return TerrainCell(e.toDouble(), slopeDeg, aspectDeg, shade, relief)
    }

    fun aspectDegrees(dzdx: Double, dzdy: Double): Double {
        if (dzdx == 0.0 && dzdy == 0.0) return 0.0
        var deg = Math.toDegrees(atan2(dzdx, -dzdy))
        if (deg < 0) deg += 360.0
        return deg
    }

    fun hillshade(
        slopeRad: Double,
        aspectDegrees: Double,
        azimuthDegrees: Double = 315.0,
        altitudeDegrees: Double = 45.0
    ): Double {
        val zenith = Math.toRadians(90.0 - altitudeDegrees)
        val azimuth = Math.toRadians(azimuthDegrees)
        val aspect = Math.toRadians(aspectDegrees)
        val value = cos(zenith) * cos(slopeRad) + sin(zenith) * sin(slopeRad) * cos(azimuth - aspect)
        return value.coerceIn(0.0, 1.0)
    }

    fun multidirectionalHillshade(slopeRad: Double, aspectDegrees: Double): Double {
        val azimuths = doubleArrayOf(225.0, 270.0, 315.0, 360.0)
        return azimuths.map { hillshade(slopeRad, aspectDegrees, it, 45.0) }.average()
    }

    fun aspectLabel(degrees: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = ((degrees + 22.5) / 45.0).toInt() % 8
        return dirs[idx]
    }

    fun slopeArgb(slopeDegrees: Double): Int {
        val t = (slopeDegrees / 45.0).coerceIn(0.0, 1.0)
        val r: Int
        val g: Int
        val b: Int
        when {
            t < 0.33 -> {
                val u = (t / 0.33).toFloat()
                r = (80 + 175 * u).toInt()
                g = (180 - 20 * u).toInt()
                b = (70 - 40 * u).toInt()
            }
            t < 0.66 -> {
                val u = ((t - 0.33) / 0.33).toFloat()
                r = 255
                g = (160 - 80 * u).toInt()
                b = 30
            }
            else -> {
                val u = ((t - 0.66) / 0.34).toFloat()
                r = 255
                g = (80 - 60 * u).toInt()
                b = (30 + 20 * u).toInt()
            }
        }
        val alpha = when {
            slopeDegrees < 12.0 -> 16
            slopeDegrees < 22.0 -> 34
            slopeDegrees < 32.0 -> 52
            else -> 72
        }
        return argb(alpha, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    fun aspectArgb(aspectDegrees: Double): Int {
        val hue = (aspectDegrees / 360.0)
        val (r, g, b) = hsv(hue.toFloat(), 0.42f, 0.82f)
        return argb(40, r, g, b)
    }

    fun hillshadeArgb(shade: Double, alpha: Int = 70): Int {
        val v = (shade * 255.0).toInt().coerceIn(0, 255)
        return argb(alpha, v, v, v)
    }

    fun overlayPixels(
        grid: DemGrid,
        mode: TerrainOverlay,
        keepClear: BooleanArray? = null
    ): IntArray {
        val out = IntArray(grid.cols * grid.rows)
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                val idx = row * grid.cols + col
                if (keepClear != null && idx < keepClear.size && keepClear[idx]) {
                    out[idx] = 0
                    continue
                }
                if (shouldHatch(mode, row, col)) {
                    out[idx] = 0
                    continue
                }
                val cell = analyzeCell(grid, row.coerceIn(1, grid.rows - 2), col.coerceIn(1, grid.cols - 2))
                out[idx] = if (cell == null) 0 else when (mode) {
                    TerrainOverlay.NONE -> 0
                    TerrainOverlay.SLOPE -> slopeArgb(cell.slopeDegrees)
                    TerrainOverlay.ASPECT -> aspectArgb(cell.aspectDegrees)
                    TerrainOverlay.HILLSHADE -> hillshadeArgb(
                        multidirectionalHillshade(
                            Math.toRadians(cell.slopeDegrees),
                            cell.aspectDegrees
                        )
                    )
                }
            }
        }
        return out
    }

    fun routeClearMask(
        grid: DemGrid,
        route: List<Pair<Double, Double>>,
        radiusCells: Int = 2
    ): BooleanArray {
        val mask = BooleanArray(grid.cols * grid.rows)
        if (route.isEmpty() || grid.cols <= 0 || grid.rows <= 0) return mask
        val samples = ArrayList<Pair<Double, Double>>(route.size * 4)
        for (i in route.indices) {
            samples += route[i]
            if (i == route.lastIndex) continue
            val a = route[i]
            val b = route[i + 1]
            repeat(7) { step ->
                val t = (step + 1) / 8.0
                samples += (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
            }
        }
        val radius = radiusCells.coerceAtLeast(1)
        for ((lat, lon) in samples) {
            val col = ((lon - grid.minLon) / grid.cellSizeX).toInt()
            val row = ((grid.maxLat - lat) / grid.cellSizeY).toInt()
            for (dr in -radius..radius) {
                for (dc in -radius..radius) {
                    val r = row + dr
                    val c = col + dc
                    if (r in 0 until grid.rows && c in 0 until grid.cols) {
                        mask[r * grid.cols + c] = true
                    }
                }
            }
        }
        return mask
    }

    private fun shouldHatch(mode: TerrainOverlay, row: Int, col: Int): Boolean {
        if (mode != TerrainOverlay.SLOPE && mode != TerrainOverlay.ASPECT) return false
        return (row + col) % 2 != 0
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun hsv(h: Float, s: Float, v: Float): IntArray {
        val i = (h * 6).toInt()
        val f = h * 6 - i
        val p = (v * (1 - s) * 255).toInt()
        val q = (v * (1 - f * s) * 255).toInt()
        val t = (v * (1 - (1 - f) * s) * 255).toInt()
        val vv = (v * 255).toInt()
        return when (i % 6) {
            0 -> intArrayOf(vv, t, p)
            1 -> intArrayOf(q, vv, p)
            2 -> intArrayOf(p, vv, t)
            3 -> intArrayOf(p, q, vv)
            4 -> intArrayOf(t, p, vv)
            else -> intArrayOf(vv, p, q)
        }
    }
}

enum class TerrainOverlay { NONE, SLOPE, ASPECT, HILLSHADE }
