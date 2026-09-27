package com.trailmap.gps.terrain

import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.geo.GeoMath
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DemGrid(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val cols: Int,
    val rows: Int,
    val elevations: FloatArray,
    val source: String = SOURCE_3DEP,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    val cellSizeX: Double get() = (maxLon - minLon) / cols
    val cellSizeY: Double get() = (maxLat - minLat) / rows
    val bounds: BoundingBox get() = BoundingBox(minLon, minLat, maxLon, maxLat)

    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLon..maxLon

    fun cellSizeMeters(): Pair<Double, Double> {
        val midLat = (minLat + maxLat) / 2.0
        val midLon = (minLon + maxLon) / 2.0
        val dx = GeoMath.haversineMeters(midLat, midLon, midLat, midLon + cellSizeX)
        val dy = GeoMath.haversineMeters(midLat, midLon, midLat + cellSizeY, midLon)
        return dx to dy
    }

    fun elevationAt(row: Int, col: Int): Float {
        if (row !in 0 until rows || col !in 0 until cols) return Float.NaN
        return elevations[row * cols + col]
    }

    fun interpolate(lat: Double, lon: Double): Double? {
        if (!contains(lat, lon) || cols < 2 || rows < 2) return null
        val x = ((lon - minLon) / cellSizeX - 0.5).coerceIn(0.0, (cols - 1).toDouble())
        val y = ((maxLat - lat) / cellSizeY - 0.5).coerceIn(0.0, (rows - 1).toDouble())
        val c0 = x.toInt().coerceIn(0, cols - 2)
        val r0 = y.toInt().coerceIn(0, rows - 2)
        val tx = (x - c0).toFloat()
        val ty = (y - r0).toFloat()
        val z00 = elevationAt(r0, c0)
        val z10 = elevationAt(r0, c0 + 1)
        val z01 = elevationAt(r0 + 1, c0)
        val z11 = elevationAt(r0 + 1, c0 + 1)
        if (z00.isNaN() || z10.isNaN() || z01.isNaN() || z11.isNaN()) return null
        val z0 = z00 * (1 - tx) + z10 * tx
        val z1 = z01 * (1 - tx) + z11 * tx
        return (z0 * (1 - ty) + z1 * ty).toDouble()
    }

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.write(MAGIC)
            out.writeInt(VERSION)
            out.writeDouble(minLon)
            out.writeDouble(minLat)
            out.writeDouble(maxLon)
            out.writeDouble(maxLat)
            out.writeInt(cols)
            out.writeInt(rows)
            out.writeUTF(source)
            out.writeLong(downloadedAt)
            val bytes = ByteBuffer.allocate(elevations.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            elevations.forEach { bytes.putFloat(it) }
            out.write(bytes.array())
        }
    }

    companion object {
        const val SOURCE_3DEP = "USGS 3DEP"
        private val MAGIC = "ARETDEM1".toByteArray(Charsets.US_ASCII)
        private const val VERSION = 1

        fun readFrom(file: File): DemGrid {
            DataInputStream(FileInputStream(file)).use { input ->
                val magic = ByteArray(8)
                input.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "Not an Arete DEM file" }
                val version = input.readInt()
                require(version == VERSION) { "Unsupported DEM version $version" }
                val minLon = input.readDouble()
                val minLat = input.readDouble()
                val maxLon = input.readDouble()
                val maxLat = input.readDouble()
                val cols = input.readInt()
                val rows = input.readInt()
                val source = input.readUTF()
                val downloadedAt = input.readLong()
                val raw = ByteArray(cols * rows * 4)
                input.readFully(raw)
                val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                val elevations = FloatArray(cols * rows) { buf.float }
                return DemGrid(minLon, minLat, maxLon, maxLat, cols, rows, elevations, source, downloadedAt)
            }
        }
    }
}
