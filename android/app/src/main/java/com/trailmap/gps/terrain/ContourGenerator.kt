package com.trailmap.gps.terrain

import kotlin.math.ceil
import kotlin.math.floor

data class ContourLine(
    val elevationMeters: Double,
    val index: Boolean,
    val points: List<DoubleArray>
)

/**
 * Marching-squares contours from a local 3DEP grid.
 * Elevations are DEM meters — never labeled as GPS altitude.
 */
object ContourGenerator {
    const val DEFAULT_MAX_DIM = 96

    fun intervalFor(minZ: Double, maxZ: Double): Double {
        val range = (maxZ - minZ).coerceAtLeast(1.0)
        return when {
            range < 200 -> 10.0
            range < 600 -> 20.0
            range < 1500 -> 40.0
            else -> 80.0
        }
    }

    /** Denser DEM sampling at close zoom so local 3DEP contours stay sharp over overzoomed USGS raster. */
    fun maxDimForZoom(zoom: Double): Int = when {
        zoom >= 16.0 -> 256
        zoom >= 14.0 -> 160
        else -> DEFAULT_MAX_DIM
    }

    fun generate(grid: DemGrid, intervalMeters: Double = 0.0, maxDim: Int = DEFAULT_MAX_DIM): List<ContourLine> {
        if (grid.cols < 2 || grid.rows < 2) return emptyList()
        var minZ = Double.POSITIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        for (z in grid.elevations) {
            if (z.isNaN()) continue
            if (z < minZ) minZ = z.toDouble()
            if (z > maxZ) maxZ = z.toDouble()
        }
        if (!minZ.isFinite() || !maxZ.isFinite() || maxZ - minZ < 1.0) return emptyList()
        val interval = (if (intervalMeters > 0) intervalMeters else intervalFor(minZ, maxZ)).coerceAtLeast(5.0)
        val indexEvery = interval * 5
        val stride = maxOf(1, maxOf(grid.cols, grid.rows) / maxDim)
        val segmentCap = (5_000L * maxDim / DEFAULT_MAX_DIM).toInt().coerceAtLeast(5_000)
        val first = ceil(minZ / interval) * interval
        val last = floor(maxZ / interval) * interval
        val out = mutableListOf<ContourLine>()
        var level = first
        var segments = 0
        while (level <= last + 0.001 && segments < segmentCap) {
            val index = kotlin.math.abs(level / indexEvery - kotlin.math.round(level / indexEvery)) < 1e-6
            var r = 0
            while (r < grid.rows - 1) {
                var c = 0
                while (c < grid.cols - 1) {
                    val segs = cellSegments(grid, r, c, stride, level)
                    segs.forEach { pair ->
                        out += ContourLine(level, index, pair)
                        segments++
                    }
                    c += stride
                }
                r += stride
            }
            level += interval
        }
        return out
    }

    fun toGeoJson(grid: DemGrid, maxDim: Int = DEFAULT_MAX_DIM): String {
        val features = StringBuilder()
        generate(grid, maxDim = maxDim).forEach { line ->
            if (line.points.size < 2) return@forEach
            if (features.isNotEmpty()) features.append(',')
            val coords = line.points.joinToString(",") { "[${it[0]},${it[1]}]" }
            features.append(
                """{"type":"Feature","properties":{"ele":${line.elevationMeters},"index":${line.index}},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
            )
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    private fun cellSegments(
        grid: DemGrid,
        row: Int,
        col: Int,
        stride: Int,
        level: Double
    ): List<List<DoubleArray>> {
        val r1 = (row + stride).coerceAtMost(grid.rows - 1)
        val c1 = (col + stride).coerceAtMost(grid.cols - 1)
        if (r1 == row || c1 == col) return emptyList()
        val z00 = grid.elevationAt(row, col)
        val z10 = grid.elevationAt(row, c1)
        val z11 = grid.elevationAt(r1, c1)
        val z01 = grid.elevationAt(r1, col)
        if (z00.isNaN() || z10.isNaN() || z11.isNaN() || z01.isNaN()) return emptyList()
        val p00 = corner(grid, row, col)
        val p10 = corner(grid, row, c1)
        val p11 = corner(grid, r1, c1)
        val p01 = corner(grid, r1, col)
        val bits =
            (if (z00 >= level) 1 else 0) or
                (if (z10 >= level) 2 else 0) or
                (if (z11 >= level) 4 else 0) or
                (if (z01 >= level) 8 else 0)
        if (bits == 0 || bits == 15) return emptyList()
        fun edge(a: DoubleArray, za: Float, b: DoubleArray, zb: Float): DoubleArray {
            val t = ((level - za) / (zb - za)).toFloat().coerceIn(0f, 1f)
            return doubleArrayOf(
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t
            )
        }
        val top = edge(p00, z00, p10, z10)
        val right = edge(p10, z10, p11, z11)
        val bottom = edge(p11, z11, p01, z01)
        val left = edge(p01, z01, p00, z00)
        val pairs: List<Pair<DoubleArray, DoubleArray>> = when (bits) {
            1, 14 -> listOf(left to top)
            2, 13 -> listOf(top to right)
            3, 12 -> listOf(left to right)
            4, 11 -> listOf(right to bottom)
            6, 9 -> listOf(top to bottom)
            7, 8 -> listOf(left to bottom)
            5 -> listOf(left to top, right to bottom)
            10 -> listOf(top to right, left to bottom)
            else -> emptyList()
        }
        return pairs.map { listOf(it.first, it.second) }
    }

    private fun corner(grid: DemGrid, row: Int, col: Int): DoubleArray {
        val lon = grid.minLon + (col + 0.5) * grid.cellSizeX
        val lat = grid.maxLat - (row + 0.5) * grid.cellSizeY
        return doubleArrayOf(lon, lat)
    }
}
