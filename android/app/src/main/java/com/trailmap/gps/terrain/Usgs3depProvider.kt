package com.trailmap.gps.terrain

import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.data.providers.Coverage
import com.trailmap.gps.data.providers.DataFreshness
import com.trailmap.gps.data.providers.TerrainProvider
import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Usgs3depProvider : TerrainProvider {
    override val id: String = "usgs-3dep"
    override val name: String = "USGS 3DEP"
    override val attribution: String =
        "Elevation from USGS 3D Elevation Program. Public domain."

    override suspend fun getElevation(lat: Double, lon: Double): Double? = withContext(Dispatchers.IO) {
        queryEpqs(lat, lon)
    }

    override suspend fun downloadDem(bounds: BoundingBox): Result<Unit> =
        runCatching { downloadGrid(bounds); Unit }

    fun freshness(offline: Boolean): DataFreshness = DataFreshness(
        provider = name,
        product = "3DEP Elevation",
        timestampMs = System.currentTimeMillis(),
        sourceUrl = EPQS,
        offline = offline,
        attribution = attribution
    )

    suspend fun downloadGrid(
        bounds: BoundingBox,
        onProgress: (Float) -> Unit = {}
    ): DemGrid = withContext(Dispatchers.IO) {
        val (cols, rows) = gridSize(bounds)
        onProgress(0.05f)
        val tiff = runCatching { downloadTiffGrid(bounds, cols, rows) }.getOrNull()
        if (tiff != null) {
            onProgress(1f)
            return@withContext tiff
        }
        AppLog.w("terrain", "3DEP TIFF export unavailable, sampling points")
        downloadSampledGrid(bounds, cols, rows, onProgress)
    }

    fun coverage(bounds: BoundingBox): Coverage {
        val inConus = bounds.minLat in 24.0..50.0 && bounds.minLon in -125.0..-66.0
        return Coverage(available = inConus, notes = if (inConus) "CONUS 3DEP" else "3DEP coverage is best in the U.S.")
    }

    private fun gridSize(bounds: BoundingBox): Pair<Int, Int> {
        val latSpan = (bounds.maxLat - bounds.minLat).coerceAtLeast(0.0001)
        val lonSpan = (bounds.maxLon - bounds.minLon).coerceAtLeast(0.0001)
        val aspect = lonSpan / latSpan
        val longSide = when {
            latSpan * lonSpan < 0.01 -> 72
            latSpan * lonSpan < 0.04 -> 56
            else -> 40
        }.coerceIn(24, 80)
        return if (aspect >= 1.0) {
            longSide to (longSide / aspect).toInt().coerceIn(16, 80)
        } else {
            (longSide * aspect).toInt().coerceIn(16, 80) to longSide
        }
    }

    private fun queryEpqs(lat: Double, lon: Double): Double? {
        val url = "$EPQS?x=$lon&y=$lat&wkid=4326&units=Meters"
        val body = httpGet(url) ?: return null
        val json = JSONObject(body)
        val raw = json.opt("value") ?: return null
        val text = raw.toString()
        if (text.equals("null", true) || text.isBlank()) return null
        return text.toDoubleOrNull()
    }

    private fun downloadTiffGrid(bounds: BoundingBox, cols: Int, rows: Int): DemGrid {
        val url = buildString {
            append(IMAGE_SERVER)
            append("/exportImage?bbox=")
            append("${bounds.minLon},${bounds.minLat},${bounds.maxLon},${bounds.maxLat}")
            append("&bboxSR=4326&imageSR=4326&size=$cols,$rows")
            append("&format=tiff&pixelType=F32&noData=-9999")
            append("&interpolation=RSP_BilinearInterpolation&f=image")
        }
        val bytes = httpGetBytes(url) ?: error("empty TIFF")
        val elevations = parseFloatTiff(bytes, cols, rows)
        return DemGrid(
            minLon = bounds.minLon,
            minLat = bounds.minLat,
            maxLon = bounds.maxLon,
            maxLat = bounds.maxLat,
            cols = cols,
            rows = rows,
            elevations = elevations,
            source = DemGrid.SOURCE_3DEP
        )
    }

    private fun downloadSampledGrid(
        bounds: BoundingBox,
        cols: Int,
        rows: Int,
        onProgress: (Float) -> Unit
    ): DemGrid {
        val elevations = FloatArray(cols * rows) { Float.NaN }
        val points = ArrayList<Triple<Int, Int, Pair<Double, Double>>>(cols * rows)
        for (row in 0 until rows) {
            val lat = bounds.maxLat - (row + 0.5) * ((bounds.maxLat - bounds.minLat) / rows)
            for (col in 0 until cols) {
                val lon = bounds.minLon + (col + 0.5) * ((bounds.maxLon - bounds.minLon) / cols)
                points += Triple(row, col, lat to lon)
            }
        }
        val chunks = points.chunked(CHUNK)
        chunks.forEachIndexed { index, chunk ->
            val samples = getSamples(chunk.map { it.third })
            chunk.forEachIndexed { i, (row, col, _) ->
                val value = samples.getOrNull(i)
                if (value != null) elevations[row * cols + col] = value
            }
            onProgress(0.1f + 0.9f * (index + 1) / chunks.size)
        }
        val valid = elevations.count { !it.isNaN() }
        if (valid < elevations.size / 4) error("3DEP returned too few samples ($valid / ${elevations.size})")
        return DemGrid(
            minLon = bounds.minLon,
            minLat = bounds.minLat,
            maxLon = bounds.maxLon,
            maxLat = bounds.maxLat,
            cols = cols,
            rows = rows,
            elevations = elevations,
            source = DemGrid.SOURCE_3DEP
        )
    }

    private fun getSamples(points: List<Pair<Double, Double>>): List<Float?> {
        val geometry = buildString {
            append("{\"points\":[")
            points.forEachIndexed { i, (lat, lon) ->
                if (i > 0) append(',')
                append("[$lon,$lat]")
            }
            append("],\"spatialReference\":{\"wkid\":4326}}")
        }
        val body = httpPostForm(
            "$IMAGE_SERVER/getSamples",
            mapOf(
                "geometry" to geometry,
                "geometryType" to "esriGeometryMultipoint",
                "returnFirstValueOnly" to "true",
                "interpolation" to "RSP_BilinearInterpolation",
                "f" to "pjson"
            )
        ) ?: return points.map { (lat, lon) -> queryEpqs(lat, lon)?.toFloat() }
        return try {
            val samples = JSONObject(body).optJSONArray("samples")
            if (samples == null) {
                points.map { (lat, lon) -> queryEpqs(lat, lon)?.toFloat() }
            } else {
                List(points.size) { i ->
                    val item = samples.optJSONObject(i)
                    item?.opt("value")?.toString()?.toFloatOrNull()
                }
            }
        } catch (_: Exception) {
            points.map { (lat, lon) -> queryEpqs(lat, lon)?.toFloat() }
        }
    }

    private fun httpGet(url: String): String? {
        val bytes = httpGetBytes(url) ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    private fun httpGetBytes(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            AppLog.w("terrain", "3DEP GET failed", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPostForm(url: String, fields: Map<String, String>): String? {
        val encoded = fields.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            connection.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLog.w("terrain", "3DEP POST failed", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "Arete/0.1 (offline mountaineering navigation; personal project)"
        const val EPQS = "https://epqs.nationalmap.gov/v1/json"
        const val IMAGE_SERVER = "https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer"
        private const val CHUNK = 80

        internal fun parseFloatTiff(bytes: ByteArray, expectedCols: Int, expectedRows: Int): FloatArray {
            if (bytes.size < 16) error("TIFF too small")
            val le = bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte()
            if (!le && !(bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte())) {
                error("Not a TIFF")
            }
            val buf = ByteBuffer.wrap(bytes).order(if (le) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            buf.position(4)
            var ifd = buf.int
            if (ifd !in 8 until bytes.size) error("Bad IFD")
            buf.position(ifd)
            val count = buf.short.toInt() and 0xFFFF
            var width = expectedCols
            var height = expectedRows
            var bits = 32
            var samples = 1
            var sampleFormat = 3
            var compression = 1
            var stripOffset = -1
            var stripCount = expectedCols * expectedRows * 4
            repeat(count) {
                val tag = buf.short.toInt() and 0xFFFF
                val type = buf.short.toInt() and 0xFFFF
                val n = buf.int
                val valuePos = buf.position()
                val value = if (type == 3 && n == 1) buf.short.toInt() and 0xFFFF else buf.int
                buf.position(valuePos + 4)
                when (tag) {
                    256 -> width = value
                    257 -> height = value
                    258 -> bits = value
                    259 -> compression = value
                    273 -> stripOffset = value
                    277 -> samples = value
                    279 -> stripCount = value
                    339 -> sampleFormat = value
                }
            }
            if (compression != 1 || bits != 32 || samples != 1 || sampleFormat != 3) {
                error("TIFF is not uncompressed Float32")
            }
            if (stripOffset < 0 || stripOffset + width * height * 4 > bytes.size) {
                error("TIFF strip missing")
            }
            val data = ByteBuffer.wrap(bytes, stripOffset, stripCount.coerceAtMost(bytes.size - stripOffset))
                .order(if (le) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            val out = FloatArray(width * height)
            for (i in out.indices) {
                val v = data.float
                out[i] = if (!v.isFinite() || v < -500f || v > 9000f) Float.NaN else v
            }
            if (width != expectedCols || height != expectedRows) {
                return resample(out, width, height, expectedCols, expectedRows)
            }
            return out
        }

        private fun resample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
            val out = FloatArray(dw * dh)
            for (row in 0 until dh) {
                val sy = row * sh / dh
                for (col in 0 until dw) {
                    val sx = col * sw / dw
                    out[row * dw + col] = src[sy * sw + sx]
                }
            }
            return out
        }
    }
}
