package com.trailmap.gps.terrain

import android.content.Context
import android.graphics.Bitmap
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.data.providers.DataFreshness
import com.trailmap.gps.geo.RouteGeometry
import com.trailmap.gps.util.AppLog
import com.trailmap.gps.util.NavigationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DemPackInfo(
    val id: String,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val cols: Int,
    val rows: Int,
    val fileName: String,
    val source: String,
    val downloadedAt: Long,
    val bytes: Long
) {
    fun bounds(): BoundingBox = BoundingBox(minLon, minLat, maxLon, maxLat)
    fun covers(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLon..maxLon
}

data class DemDownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val lastPack: DemPackInfo? = null
)

data class TerrainInspection(
    val lat: Double,
    val lon: Double,
    val demElevationMeters: Double? = null,
    val slopeDegrees: Double? = null,
    val aspectDegrees: Double? = null,
    val aspectLabel: String? = null,
    val hillshade: Double? = null,
    val localReliefMeters: Double? = null,
    val routeDistanceMeters: Double? = null,
    val elevationAboveUserMeters: Double? = null,
    val source: String = "USGS 3DEP",
    val offline: Boolean = false,
    val freshness: DataFreshness? = null
)

class DemRepository(context: Context) {
    private val appContext = context.applicationContext
    private val provider = Usgs3depProvider()
    private val demDir: File get() = File(appContext.filesDir, "dem").also { it.mkdirs() }
    private val indexFile: File get() = File(demDir, "index.json")

    private val _download = MutableStateFlow(DemDownloadState())
    val download: StateFlow<DemDownloadState> = _download.asStateFlow()

    private var cached: Pair<String, DemGrid>? = null

    val terrainProvider: Usgs3depProvider get() = provider

    fun packs(): List<DemPackInfo> = readIndex()

    fun hasCoverage(lat: Double, lon: Double): Boolean = findPack(lat, lon) != null

    fun hasCoverage(bounds: BoundingBox): Boolean = readIndex().any { pack ->
        pack.minLon <= bounds.minLon && pack.maxLon >= bounds.maxLon &&
            pack.minLat <= bounds.minLat && pack.maxLat >= bounds.maxLat
    }

    fun inspect(lat: Double, lon: Double, userElevation: Double? = null, routePoints: List<TrackPoint> = emptyList()): TerrainInspection? {
        val grid = gridCovering(lat, lon) ?: return null
        val cell = TerrainMath.analyze(grid, lat, lon)
        val demEle = cell?.elevationMeters ?: grid.interpolate(lat, lon)
        val along = if (routePoints.size >= 2) {
            RouteGeometry.project(routePoints, lat, lon)?.distanceAlongRouteMeters
        } else null
        return TerrainInspection(
            lat = lat,
            lon = lon,
            demElevationMeters = demEle,
            slopeDegrees = cell?.slopeDegrees,
            aspectDegrees = cell?.aspectDegrees,
            aspectLabel = cell?.aspectDegrees?.let { TerrainMath.aspectLabel(it) },
            hillshade = cell?.hillshade,
            localReliefMeters = cell?.localReliefMeters,
            routeDistanceMeters = along,
            elevationAboveUserMeters = if (demEle != null && userElevation != null) demEle - userElevation else null,
            source = grid.source,
            offline = true,
            freshness = provider.freshness(offline = true).copy(timestampMs = grid.downloadedAt)
        )
    }

    suspend fun inspectOrFetch(
        lat: Double,
        lon: Double,
        userElevation: Double? = null,
        routePoints: List<TrackPoint> = emptyList()
    ): TerrainInspection = withContext(Dispatchers.IO) {
        inspect(lat, lon, userElevation, routePoints)?.let { return@withContext it }
        val online = runCatching { provider.getElevation(lat, lon) }.getOrNull()
        TerrainInspection(
            lat = lat,
            lon = lon,
            demElevationMeters = online,
            source = if (online != null) "USGS 3DEP (online)" else "USGS 3DEP",
            offline = false,
            elevationAboveUserMeters = if (online != null && userElevation != null) online - userElevation else null,
            freshness = provider.freshness(offline = false)
        )
    }

    fun overlayBitmap(
        mode: TerrainOverlay,
        lat: Double,
        lon: Double,
        routePoints: List<TrackPoint> = emptyList()
    ): Pair<Bitmap, BoundingBox>? {
        if (mode == TerrainOverlay.NONE) return null
        val grid = gridCovering(lat, lon) ?: loadLatest() ?: return null
        return bitmapForGrid(grid, mode, routePoints)
    }

    fun overlayBitmapForBounds(mode: TerrainOverlay, bounds: BoundingBox): Pair<Bitmap, BoundingBox>? {
        if (mode == TerrainOverlay.NONE) return null
        val pack = readIndex().firstOrNull { it.covers((bounds.minLat + bounds.maxLat) / 2, (bounds.minLon + bounds.maxLon) / 2) }
            ?: readIndex().firstOrNull()
            ?: return null
        val grid = load(pack) ?: return null
        return bitmapForGrid(grid, mode)
    }

    private fun bitmapForGrid(
        grid: DemGrid,
        mode: TerrainOverlay,
        routePoints: List<TrackPoint> = emptyList()
    ): Pair<Bitmap, BoundingBox> {
        val keepClear = if (routePoints.size >= 2) {
            TerrainMath.routeClearMask(grid, routePoints.map { it.lat to it.lon })
        } else {
            null
        }
        val pixels = TerrainMath.overlayPixels(grid, mode, keepClear)
        val bitmap = Bitmap.createBitmap(grid.cols, grid.rows, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, grid.cols, 0, 0, grid.cols, grid.rows)
        return bitmap to grid.bounds
    }

    suspend fun downloadForRoute(points: List<TrackPoint>, paddingFactor: Double = 0.12): DemPackInfo? {
        if (points.isEmpty()) return null
        val raw = NavigationUtils.boundsFromPoints(points, paddingFactor)
        val bounds = BoundingBox(raw[0], raw[1], raw[2], raw[3])
        return downloadForBounds(bounds)
    }

    suspend fun downloadForBounds(bounds: BoundingBox): DemPackInfo? = withContext(Dispatchers.IO) {
        _download.value = DemDownloadState(isDownloading = true, progress = 0f)
        try {
            val grid = provider.downloadGrid(bounds) { _download.value = _download.value.copy(progress = it) }
            val pack = persist(grid)
            _download.value = DemDownloadState(isDownloading = false, progress = 1f, lastPack = pack)
            pack
        } catch (e: Exception) {
            AppLog.w("terrain", "DEM download failed", e)
            _download.value = DemDownloadState(
                isDownloading = false,
                error = e.message ?: "Terrain download failed"
            )
            null
        }
    }

    fun loadLatest(): DemGrid? = readIndex().maxByOrNull { it.downloadedAt }?.let { load(it) }

    fun gridCovering(lat: Double, lon: Double): DemGrid? = findPack(lat, lon)?.let { load(it) }

    private fun findPack(lat: Double, lon: Double): DemPackInfo? =
        readIndex().filter { it.covers(lat, lon) }.minByOrNull { (it.maxLat - it.minLat) * (it.maxLon - it.minLon) }

    private fun load(pack: DemPackInfo): DemGrid? {
        cached?.let { if (it.first == pack.id) return it.second }
        return runCatching {
            val grid = DemGrid.readFrom(File(demDir, pack.fileName))
            cached = pack.id to grid
            grid
        }.onFailure { AppLog.w("terrain", "Failed to read DEM ${pack.fileName}", it) }.getOrNull()
    }

    private fun persist(grid: DemGrid): DemPackInfo {
        val id = "dem_${grid.minLon.format()}_${grid.minLat.format()}_${grid.cols}x${grid.rows}_${grid.downloadedAt}"
        val fileName = "$id.dem"
        val file = File(demDir, fileName)
        grid.writeTo(file)
        val pack = DemPackInfo(
            id = id,
            minLon = grid.minLon,
            minLat = grid.minLat,
            maxLon = grid.maxLon,
            maxLat = grid.maxLat,
            cols = grid.cols,
            rows = grid.rows,
            fileName = fileName,
            source = grid.source,
            downloadedAt = grid.downloadedAt,
            bytes = file.length()
        )
        val next = readIndex().filterNot { it.id == id } + pack
        writeIndex(next)
        cached = id to grid
        return pack
    }

    private fun readIndex(): List<DemPackInfo> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DemPackInfo(
                    id = o.getString("id"),
                    minLon = o.getDouble("minLon"),
                    minLat = o.getDouble("minLat"),
                    maxLon = o.getDouble("maxLon"),
                    maxLat = o.getDouble("maxLat"),
                    cols = o.getInt("cols"),
                    rows = o.getInt("rows"),
                    fileName = o.getString("fileName"),
                    source = o.optString("source", DemGrid.SOURCE_3DEP),
                    downloadedAt = o.optLong("downloadedAt"),
                    bytes = o.optLong("bytes")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(packs: List<DemPackInfo>) {
        val arr = JSONArray()
        packs.forEach { pack ->
            arr.put(JSONObject().apply {
                put("id", pack.id)
                put("minLon", pack.minLon)
                put("minLat", pack.minLat)
                put("maxLon", pack.maxLon)
                put("maxLat", pack.maxLat)
                put("cols", pack.cols)
                put("rows", pack.rows)
                put("fileName", pack.fileName)
                put("source", pack.source)
                put("downloadedAt", pack.downloadedAt)
                put("bytes", pack.bytes)
            })
        }
        indexFile.writeText(arr.toString())
    }

    private fun Double.format(): String = String.format("%.4f", this).replace('.', 'p')
}
