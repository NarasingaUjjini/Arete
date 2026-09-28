package com.trailmap.gps.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.trailmap.gps.data.MapLayer
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.util.NavigationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

data class OfflineDownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalTiles: Int = 0,
    val completedTiles: Int = 0,
    val error: String? = null
)

enum class TileSource(
    val id: String,
    val label: String,
    val maxZoom: Int
) {
    OPENTOPO("opentopo", "OpenTopoMap", 17),
    ESRI("esri", "Satellite", 19),
    OSM("osm", "OpenStreetMap", 19),
    HILLSHADE("hillshade", "Hillshade", 15),
    USGS_TOPO("usgs_topo", "USGS Topo", 16),
    USGS_SHADE("usgs_shade", "USGS Relief", 15),
    USGS_IMAGERY("usgs_imagery", "USGS Imagery", 16),
    USGS_HISTORICAL("usgs_historical", "USGS Historical", 16),
    USA_TOPO("usa_topo", "Classic USGS", 15);

    fun remoteUrl(z: Int, x: Int, y: Int): String = when (this) {
        OPENTOPO -> {
            val host = listOf("a", "b", "c")[(x + y) % 3]
            "https://$host.tile.opentopomap.org/$z/$x/$y.png"
        }
        ESRI ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        OSM -> "https://tile.openstreetmap.org/$z/$x/$y.png"
        HILLSHADE ->
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSShadedReliefOnly/MapServer/tile/$z/$y/$x"
        USGS_TOPO ->
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/$z/$y/$x"
        USGS_SHADE ->
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSShadedReliefOnly/MapServer/tile/$z/$y/$x"
        USGS_IMAGERY ->
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/$z/$y/$x"
        USGS_HISTORICAL ->
            "https://ngmdb.usgs.gov/arcgis/rest/services/topoview/ustOverlayAuto/MapServer/tile/$z/$y/$x"
        USA_TOPO ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/USA_Topo_Maps/MapServer/tile/$z/$y/$x"
    }

    companion object {
        fun fromRemoteUrl(url: String): ParsedTile? {
            val path = url.substringAfter("://")
            return when {
                path.contains("tile.opentopomap.org/") -> {
                    val parts = path.substringAfter("opentopomap.org/").split("/")
                    if (parts.size >= 3) {
                        ParsedTile(OPENTOPO, parts[0].toInt(), parts[1].toInt(), parts[2].substringBefore(".").toInt())
                    } else null
                }
                path.contains("World_Imagery/MapServer/tile/") -> {
                    val parts = path.substringAfter("MapServer/tile/").split("/")
                    if (parts.size >= 3) {
                        val z = parts[0].toInt()
                        val y = parts[1].toInt()
                        val x = parts[2].substringBefore("?").toInt()
                        ParsedTile(ESRI, z, x, y)
                    } else null
                }
                path.contains("tile.openstreetmap.org/") -> {
                    val parts = path.substringAfter("openstreetmap.org/").split("/")
                    if (parts.size >= 3) {
                        ParsedTile(OSM, parts[0].toInt(), parts[1].toInt(), parts[2].substringBefore(".").toInt())
                    } else null
                }
                path.contains("hillshading/") -> {
                    val parts = path.substringAfter("hillshading/").split("/")
                    if (parts.size >= 3) {
                        ParsedTile(HILLSHADE, parts[0].toInt(), parts[1].toInt(), parts[2].substringBefore(".").toInt())
                    } else null
                }
                path.contains("USGSTopo/MapServer/tile/") -> parseEsriTile(path, USGS_TOPO)
                path.contains("USGSShadedReliefOnly/MapServer/tile/") -> parseEsriTile(path, USGS_SHADE)
                path.contains("USGSImageryOnly/MapServer/tile/") -> parseEsriTile(path, USGS_IMAGERY)
                path.contains("topoview/ustOverlayAuto/MapServer/tile/") -> parseEsriTile(path, USGS_HISTORICAL)
                path.contains("USA_Topo_Maps/MapServer/tile/") -> parseEsriTile(path, USA_TOPO)
                else -> null
            }
        }

        private fun parseEsriTile(path: String, source: TileSource): ParsedTile? {
            val parts = path.substringAfter("MapServer/tile/").split("/")
            if (parts.size < 3) return null
            val z = parts[0].toInt()
            val y = parts[1].toInt()
            val x = parts[2].substringBefore("?").toInt()
            return ParsedTile(source, z, x, y)
        }
    }
}

data class ParsedTile(val source: TileSource, val z: Int, val x: Int, val y: Int)

data class TileJob(
    val sourceId: String,
    val z: Int,
    val x: Int,
    val y: Int,
    val done: Boolean = false,
    val bytes: Long = 0,
    val failed: Boolean = false
)

class OfflineTileManager(private val context: Context) {
    private val _state = MutableStateFlow(OfflineDownloadState())
    val state: StateFlow<OfflineDownloadState> = _state.asStateFlow()

    @Volatile private var cancelRequested = false

    private val tileDir: File
        get() = File(context.filesDir, "offline_tiles").also { it.mkdirs() }

    private val emptyTileFile: File
        get() = File(context.filesDir, "empty_tile.png")

    fun cancelDownload() {
        cancelRequested = true
        if (_state.value.isDownloading) {
            _state.value = _state.value.copy(isDownloading = false, error = "Download cancelled")
        }
    }

    fun installResourceTransform() {
        ensureEmptyTile()
        MapLibre.getInstance(context)
        FileSource.getInstance(context).setResourceTransform { _, url ->
            resolveUrl(url)
        }
    }

    fun resolveUrl(url: String): String {
        val parsed = TileSource.fromRemoteUrl(url) ?: return url
        val file = tileFile(parsed.source, parsed.z, parsed.x, parsed.y)
        if (file.exists() && file.length() > 0) {
            return file.toURI().toString()
        }
        if (!canUseNetwork()) {
            val fallback = when (parsed.source) {
                TileSource.OPENTOPO, TileSource.OSM, TileSource.USGS_HISTORICAL, TileSource.USA_TOPO ->
                    tileFile(TileSource.USGS_TOPO, parsed.z, parsed.x, parsed.y)
                TileSource.HILLSHADE -> tileFile(TileSource.USGS_SHADE, parsed.z, parsed.x, parsed.y)
                else -> null
            }
            if (fallback != null && fallback.exists() && fallback.length() > 0) {
                return fallback.toURI().toString()
            }
            return emptyTileFile.toURI().toString()
        }
        return url
    }

    fun sourcesFor(layer: MapLayer, hillshade: Boolean): List<TileSource> {
        val sources = mutableListOf<TileSource>()
        when (layer) {
            MapLayer.ARETE_TOPO -> {
                sources += TileSource.USGS_TOPO
                sources += TileSource.USGS_SHADE
            }
            MapLayer.USGS_TOPO -> sources += TileSource.USGS_TOPO
            MapLayer.IMAGERY -> sources += TileSource.USGS_IMAGERY
            MapLayer.HISTORICAL -> sources += TileSource.USGS_TOPO
            MapLayer.OPENTOPO, MapLayer.SATELLITE, MapLayer.OSM ->
                sources += TileSource.USGS_TOPO
        }
        if (hillshade && TileSource.USGS_SHADE !in sources) sources += TileSource.USGS_SHADE
        return sources
    }

    fun packSources(includeTopo: Boolean, includeHillshade: Boolean): List<TileSource> {
        val sources = mutableListOf<TileSource>()
        if (includeTopo) sources += TileSource.USGS_TOPO
        if (includeHillshade) sources += TileSource.USGS_SHADE
        return sources
    }

    fun sourceLabels(layer: MapLayer, hillshade: Boolean): String =
        sourcesFor(layer, hillshade).joinToString(" · ") { it.label }

    fun canUseNetwork(): Boolean = NetworkGate.allowNetwork() && isOnline()

    fun buildCorridorJobs(
        points: List<TrackPoint>,
        bufferMeters: Double,
        minZoom: Int,
        maxZoom: Int,
        sources: List<TileSource>
    ): List<TileJob> {
        if (points.isEmpty()) return emptyList()
        val box = CorridorGeometry.bufferBounds(points, bufferMeters)
        val bounds = doubleArrayOf(box.minLon, box.minLat, box.maxLon, box.maxLat)
        return enumerateTiles(bounds, minZoom, maxZoom)
            .filter { tile ->
                val (lat, lon) = CorridorGeometry.tileCenter(tile.z, tile.x, tile.y)
                CorridorGeometry.withinCorridor(lat, lon, points, bufferMeters)
            }
            .flatMap { tile ->
                sources.mapNotNull { source ->
                    if (tile.z > source.maxZoom) null
                    else annotateJob(TileJob(source.id, tile.z, tile.x, tile.y))
                }
            }
    }

    fun buildBoundsJobs(
        bounds: DoubleArray,
        minZoom: Int,
        maxZoom: Int,
        sources: List<TileSource>
    ): List<TileJob> = enumerateTiles(bounds, minZoom, maxZoom).flatMap { tile ->
        sources.mapNotNull { source ->
            if (tile.z > source.maxZoom) null
            else annotateJob(TileJob(source.id, tile.z, tile.x, tile.y))
        }
    }

    fun annotateJob(job: TileJob): TileJob {
        val source = TileSource.entries.find { it.id == job.sourceId } ?: return job
        val file = tileFile(source, job.z, job.x, job.y)
        return if (file.exists() && file.length() > 0) {
            job.copy(done = true, bytes = file.length(), failed = false)
        } else job
    }

    fun measuredBytes(jobs: List<TileJob>): Long = jobs.sumOf { it.bytes }

    suspend fun downloadJobs(
        jobs: List<TileJob>,
        onProgress: (List<TileJob>, Int) -> Unit,
        shouldStop: () -> Boolean
    ): List<TileJob> = withContext(Dispatchers.IO) {
        val out = jobs.toMutableList()
        out.indices.forEach { index ->
            if (shouldStop()) return@withContext out
            val job = out[index]
            if (job.done) {
                onProgress(out, index + 1)
                return@forEach
            }
            if (!canUseNetwork()) {
                out[index] = job.copy(failed = true)
                onProgress(out, index + 1)
                return@forEach
            }
            val source = TileSource.entries.find { it.id == job.sourceId }
            if (source == null) {
                out[index] = job.copy(failed = true)
            } else {
                try {
                    val bytes = downloadTile(source, job.z, job.x, job.y)
                    out[index] = if (bytes > 0) job.copy(done = true, bytes = bytes, failed = false)
                    else job.copy(failed = true)
                } catch (_: Exception) {
                    out[index] = job.copy(failed = true)
                }
            }
            onProgress(out, index + 1)
        }
        out
    }

    suspend fun downloadForBounds(
        bounds: DoubleArray,
        minZoom: Int = 10,
        maxZoom: Int = 15,
        layer: MapLayer = MapLayer.ARETE_TOPO,
        hillshade: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        if (bounds.size < 4 || bounds[2] <= bounds[0] || bounds[3] <= bounds[1]) {
            _state.value = OfflineDownloadState(error = "Invalid download area")
            return@withContext 0L
        }
        downloadTilesForBounds(bounds, minZoom, maxZoom, sourcesFor(layer, hillshade))
    }

    suspend fun downloadForRoute(
        points: List<TrackPoint>,
        minZoom: Int = 10,
        maxZoom: Int = 15,
        paddingFactor: Double = 0.15,
        layer: MapLayer = MapLayer.ARETE_TOPO,
        hillshade: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext 0L
        val bounds = NavigationUtils.boundsFromPoints(points, paddingFactor)
        downloadTilesForBounds(bounds, minZoom, maxZoom, sourcesFor(layer, hillshade))
    }

    private suspend fun downloadTilesForBounds(
        bounds: DoubleArray,
        minZoom: Int,
        maxZoom: Int,
        sources: List<TileSource>
    ): Long {
        val coords = enumerateTiles(bounds, minZoom, maxZoom)
        if (coords.isEmpty()) {
            _state.value = OfflineDownloadState(error = "No tiles in download area")
            return 0L
        }

        val jobs = coords.flatMap { tile ->
            sources.mapNotNull { source ->
                if (tile.z > source.maxZoom) null else tile to source
            }
        }
        if (jobs.isEmpty()) {
            _state.value = OfflineDownloadState(error = "No tiles in download area")
            return 0L
        }

        var downloadedBytes = 0L
        var completed = 0
        cancelRequested = false
        _state.value = OfflineDownloadState(isDownloading = true, totalTiles = jobs.size)

        jobs.forEach { (tile, source) ->
            if (cancelRequested) {
                _state.value = _state.value.copy(
                    isDownloading = false,
                    error = "Download cancelled",
                    downloadedBytes = downloadedBytes,
                    completedTiles = completed
                )
                return downloadedBytes
            }
            try {
                downloadedBytes += downloadTile(source, tile.z, tile.x, tile.y)
            } catch (_: Exception) {
                // Keep going — a few failed tiles should not abort the pack
            }
            completed += 1
            _state.value = _state.value.copy(
                progress = completed.toFloat() / jobs.size,
                downloadedBytes = downloadedBytes,
                completedTiles = completed
            )
        }

        _state.value = _state.value.copy(isDownloading = false, progress = 1f)
        return downloadedBytes
    }

    fun estimateTileCountForBounds(
        bounds: DoubleArray,
        minZoom: Int,
        maxZoom: Int,
        layer: MapLayer,
        hillshade: Boolean
    ): Int {
        if (bounds.size < 4) return 0
        val coords = enumerateTiles(bounds, minZoom, maxZoom)
        val sources = sourcesFor(layer, hillshade)
        return coords.sumOf { tile -> sources.count { tile.z <= it.maxZoom } }
    }

    fun tileFile(source: TileSource, z: Int, x: Int, y: Int): File =
        File(tileDir, "${source.id}/$z/$x/$y.png")

    private fun downloadTile(source: TileSource, z: Int, x: Int, y: Int): Long {
        val file = tileFile(source, z, x, y)
        if (file.exists() && file.length() > 0) return file.length()
        if (!canUseNetwork()) return 0L

        val connection = URL(source.remoteUrl(z, x, y)).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connect()
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            return 0L
        }

        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        connection.inputStream.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        return file.length()
    }

    private data class TileCoord(val z: Int, val x: Int, val y: Int)

    private fun enumerateTiles(bounds: DoubleArray, minZoom: Int, maxZoom: Int): List<TileCoord> {
        val minLon = bounds[0]
        val minLat = bounds[1]
        val maxLon = bounds[2]
        val maxLat = bounds[3]
        val tiles = linkedSetOf<TileCoord>()
        val skipped = mutableListOf<Int>()

        for (z in minZoom..maxZoom) {
            val sw = latLonToTile(minLat, minLon, z)
            val ne = latLonToTile(maxLat, maxLon, z)
            val minX = minOf(sw.first, ne.first)
            val maxX = maxOf(sw.first, ne.first)
            val minY = minOf(sw.second, ne.second)
            val maxY = maxOf(sw.second, ne.second)
            val tileCount = (maxX - minX + 1L) * (maxY - minY + 1L)
            if (tileCount > 5000) {
                skipped += z
                continue
            }

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    tiles.add(TileCoord(z, x, y))
                }
            }
        }
        if (skipped.isNotEmpty()) {
            _state.value = _state.value.copy(
                error = "Zoom ${skipped.joinToString(",")} skipped — area too large for those levels"
            )
        }
        return tiles.toList()
    }

    private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom)
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n.toInt() - 1)
        val latRad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val y = floor((1.0 - ln(tan(latRad) + 1 / cos(latRad)) / Math.PI) / 2.0 * n)
            .toInt().coerceIn(0, n.toInt() - 1)
        return x to y
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun ensureEmptyTile() {
        if (emptyTileFile.exists() && emptyTileFile.length() > 0) return
        emptyTileFile.outputStream().use { it.write(EMPTY_PNG) }
    }

    companion object {
        const val USER_AGENT = "Arete/0.1 (offline mountaineering navigation; personal project)"

        private val EMPTY_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, 0xB4.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
    }
}
