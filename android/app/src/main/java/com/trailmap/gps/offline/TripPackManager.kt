package com.trailmap.gps.offline

import android.content.Context
import com.trailmap.gps.data.PackCheckItem
import com.trailmap.gps.data.PackItemStatus
import com.trailmap.gps.data.RouteRepository
import com.trailmap.gps.data.TripPackAreaMode
import com.trailmap.gps.data.TripPackDao
import com.trailmap.gps.data.TripPackEntity
import com.trailmap.gps.data.TripPackStatus
import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.data.providers.ImageryResolution
import com.trailmap.gps.terrain.DemRepository
import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TripPackProgress(
    val isRunning: Boolean = false,
    val paused: Boolean = false,
    val phase: String = "",
    val progress: Float = 0f,
    val completedTiles: Int = 0,
    val totalTiles: Int = 0,
    val downloadedBytes: Long = 0,
    val error: String? = null,
    val packId: Long? = null
)

class TripPackManager(
    context: Context,
    private val dao: TripPackDao,
    private val tiles: OfflineTileManager,
    private val dem: DemRepository,
    private val routes: RouteRepository
) {
    private val appContext = context.applicationContext
    private val imagery = NaipImageryProvider()
    private val weather = NwsSnapshotProvider()
    private val packRoot: File get() = File(appContext.filesDir, "trip_packs").also { it.mkdirs() }

    private val _progress = MutableStateFlow(TripPackProgress())
    val progress: StateFlow<TripPackProgress> = _progress.asStateFlow()

    @Volatile private var pauseRequested = false
    @Volatile private var cancelRequested = false

    fun observeAll() = dao.observeAll()
    fun observeForRoute(routeId: Long) = dao.observeForRoute(routeId)
    suspend fun latestForRoute(routeId: Long) = dao.latestForRoute(routeId)
    suspend fun get(id: Long) = dao.getById(id)

    fun forcedOffline(): Boolean = !NetworkGate.allowNetwork()
    fun setForcedOffline(value: Boolean) = NetworkGate.setForcedOffline(value)

    fun packDir(id: Long): File = File(packRoot, id.toString()).also { it.mkdirs() }

    suspend fun prepare(
        routeId: Long,
        name: String,
        points: List<com.trailmap.gps.data.TrackPoint>,
        corridor: CorridorBuffer,
        areaMode: TripPackAreaMode,
        customBounds: BoundingBox?,
        includeTopo: Boolean,
        includeDem: Boolean,
        includeHillshade: Boolean,
        includeImagery: Boolean,
        includeConditions: Boolean,
        maxZoom: Int
    ): TripPackEntity {
        val bounds = if (areaMode == TripPackAreaMode.CUSTOM && customBounds != null) {
            customBounds
        } else {
            CorridorGeometry.bufferBounds(points, corridor.meters)
        }
        val sources = tiles.packSources(includeTopo, includeHillshade)
        val jobs = if (areaMode == TripPackAreaMode.CORRIDOR) {
            tiles.buildCorridorJobs(points, corridor.meters, MIN_ZOOM, maxZoom, sources)
        } else {
            tiles.buildBoundsJobs(
                doubleArrayOf(bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat),
                MIN_ZOOM, maxZoom, sources
            )
        }
        val tileEstimate = jobs.size * 18_000L
        val demEstimate = if (includeDem) 350_000L else 0L
        val imgEstimate = if (includeImagery) 400_000L else 0L
        val existing = dao.latestForRoute(routeId)
        val entity = (existing ?: TripPackEntity(routeId = routeId, name = name, minLon = 0.0, minLat = 0.0, maxLon = 0.0, maxLat = 0.0, corridorMeters = corridor.meters)).copy(
            name = name,
            minLon = bounds.minLon,
            minLat = bounds.minLat,
            maxLon = bounds.maxLon,
            maxLat = bounds.maxLat,
            corridorMeters = corridor.meters,
            areaMode = areaMode.name,
            includeTopo = includeTopo,
            includeDem = includeDem,
            includeHillshade = includeHillshade,
            includeImagery = includeImagery,
            includeConditions = includeConditions,
            estimatedBytes = tileEstimate + demEstimate + imgEstimate,
            maxZoom = maxZoom,
            updatedAt = System.currentTimeMillis(),
            error = null
        )
        val id = if (entity.id == 0L) dao.insert(entity) else {
            dao.update(entity)
            entity.id
        }
        val saved = dao.getById(id) ?: entity.copy(id = id)
        writeJobs(saved.id, jobs)
        return saved
    }

    fun estimateTiles(
        points: List<com.trailmap.gps.data.TrackPoint>,
        corridor: CorridorBuffer,
        areaMode: TripPackAreaMode,
        customBounds: BoundingBox?,
        includeTopo: Boolean,
        includeHillshade: Boolean,
        maxZoom: Int
    ): Int {
        val sources = tiles.packSources(includeTopo, includeHillshade)
        return if (areaMode == TripPackAreaMode.CORRIDOR) {
            tiles.buildCorridorJobs(points, corridor.meters, MIN_ZOOM, maxZoom, sources).size
        } else if (customBounds != null) {
            tiles.buildBoundsJobs(
                doubleArrayOf(customBounds.minLon, customBounds.minLat, customBounds.maxLon, customBounds.maxLat),
                MIN_ZOOM, maxZoom, sources
            ).size
        } else 0
    }

    fun pause() {
        pauseRequested = true
    }

    fun cancel() {
        cancelRequested = true
        pauseRequested = false
    }

    suspend fun start(packId: Long) {
        pauseRequested = false
        cancelRequested = false
        val pack = dao.getById(packId) ?: return
        if (!NetworkGate.allowNetwork()) {
            _progress.value = TripPackProgress(error = "Network disabled — turn off airplane test to download", packId = packId)
            return
        }
        dao.update(pack.copy(status = TripPackStatus.DOWNLOADING.name, updatedAt = System.currentTimeMillis(), error = null))
        _progress.value = TripPackProgress(isRunning = true, phase = "Starting", packId = packId)
        try {
            val route = routes.getById(pack.routeId)
            val points = route?.let { routes.parsePoints(it.pointsJson) }.orEmpty()
            var jobs = readJobs(packId).ifEmpty {
                val sources = tiles.packSources(pack.includeTopo, pack.includeHillshade)
                if (pack.area() == TripPackAreaMode.CORRIDOR && points.isNotEmpty()) {
                    tiles.buildCorridorJobs(points, pack.corridorMeters, MIN_ZOOM, pack.maxZoom, sources)
                } else {
                    tiles.buildBoundsJobs(
                        doubleArrayOf(pack.minLon, pack.minLat, pack.maxLon, pack.maxLat),
                        MIN_ZOOM, pack.maxZoom, sources
                    )
                }
            }.map { tiles.annotateJob(it) }

            _progress.value = _progress.value.copy(
                phase = "USGS Topo",
                totalTiles = jobs.size,
                completedTiles = jobs.count { it.done },
                downloadedBytes = tiles.measuredBytes(jobs)
            )
            jobs = tiles.downloadJobs(jobs, { next, index ->
                writeJobs(packId, next)
                _progress.value = _progress.value.copy(
                    phase = "USGS Topo",
                    progress = if (next.isEmpty()) 1f else index.toFloat() / next.size * 0.55f,
                    completedTiles = next.count { it.done },
                    totalTiles = next.size,
                    downloadedBytes = tiles.measuredBytes(next)
                )
            }, shouldStop = { pauseRequested || cancelRequested })
            writeJobs(packId, jobs)

            if (stopIfNeeded(packId, jobs)) return

            if (pack.includeDem) {
                _progress.value = _progress.value.copy(phase = "USGS 3DEP", progress = 0.58f)
                val bounds = BoundingBox(pack.minLon, pack.minLat, pack.maxLon, pack.maxLat)
                dem.downloadForBounds(bounds)
            }
            if (stopIfNeeded(packId, jobs)) return

            if (pack.includeImagery) {
                _progress.value = _progress.value.copy(phase = "USGS NAIP", progress = 0.78f)
                val dest = File(packDir(packId), "naip.jpg")
                if (!dest.exists() || dest.length() == 0L) {
                    imagery.download(
                        BoundingBox(pack.minLon, pack.minLat, pack.maxLon, pack.maxLat),
                        ImageryResolution.STANDARD,
                        dest
                    ).onFailure { AppLog.w("pack", "imagery failed", it) }
                }
            }
            if (stopIfNeeded(packId, jobs)) return

            if (pack.includeConditions) {
                _progress.value = _progress.value.copy(phase = "NWS snapshot", progress = 0.88f)
                val dest = File(packDir(packId), "nws.json")
                val midLat = (pack.minLat + pack.maxLat) / 2
                val midLon = (pack.minLon + pack.maxLon) / 2
                weather.download(midLat, midLon, dest)
            }

            _progress.value = _progress.value.copy(phase = "Verify", progress = 0.95f)
            val checks = verify(packId)
            val latest = dao.getById(packId) ?: return
            val bytes = measureBytes(latest, jobs)
            val status = statusFrom(checks, latest)
            dao.update(
                latest.copy(
                    status = status.name,
                    actualBytes = bytes,
                    verifiedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    manifestJson = checksToJson(checks),
                    error = checks.firstOrNull { it.status == PackItemStatus.FAILED && it.required }?.message
                )
            )
            routes.markOfflineDownloaded(pack.routeId, bytes)
            _progress.value = TripPackProgress(
                isRunning = false,
                progress = 1f,
                downloadedBytes = bytes,
                packId = packId,
                completedTiles = jobs.count { it.done },
                totalTiles = jobs.size
            )
        } catch (e: Exception) {
            AppLog.w("pack", "Trip pack failed", e)
            dao.getById(packId)?.let {
                dao.update(it.copy(status = TripPackStatus.FAILED.name, error = e.message, updatedAt = System.currentTimeMillis()))
            }
            _progress.value = TripPackProgress(error = e.message ?: "Download failed", packId = packId)
        }
    }

    suspend fun verify(packId: Long): List<PackCheckItem> = withContext(Dispatchers.IO) {
        val pack = dao.getById(packId) ?: return@withContext emptyList()
        val route = routes.getById(pack.routeId)
        val points = route?.let { routes.parsePoints(it.pointsJson) }.orEmpty()
        val waypoints = route?.let { routes.parseWaypoints(it.waypointsJson) }.orEmpty()
        val jobs = readJobs(packId).map { tiles.annotateJob(it) }
        val topoJobs = jobs.filter { it.sourceId == TileSource.USGS_TOPO.id }
        val shadeJobs = jobs.filter { it.sourceId == TileSource.USGS_SHADE.id }
        val topoOk = topoJobs.isEmpty() || topoJobs.count { it.done }.toDouble() / topoJobs.size >= 0.8
        val shadeOk = shadeJobs.isEmpty() || shadeJobs.count { it.done }.toDouble() / shadeJobs.size >= 0.7
        val midLat = (pack.minLat + pack.maxLat) / 2
        val midLon = (pack.minLon + pack.maxLon) / 2
        val demOk = !pack.includeDem || dem.hasCoverage(midLat, midLon)
        val imageryFile = File(packDir(packId), "naip.jpg")
        val weatherFile = File(packDir(packId), "nws.json")
        val snapshot = weather.read(weatherFile)
        val profileOk = points.size >= 2
        val checks = listOf(
            PackCheckItem("route", "Route", true, if (points.size >= 2) PackItemStatus.OK else PackItemStatus.MISSING),
            PackCheckItem("waypoints", "Waypoints", false, PackItemStatus.OK, message = "${waypoints.size} saved"),
            PackCheckItem("profile", "Elevation profile", true, if (profileOk) PackItemStatus.OK else PackItemStatus.MISSING),
            PackCheckItem(
                "topo", "USGS Topo", pack.includeTopo,
                when {
                    !pack.includeTopo -> PackItemStatus.SKIPPED
                    topoOk -> PackItemStatus.OK
                    topoJobs.any { it.done } -> PackItemStatus.FAILED
                    else -> PackItemStatus.MISSING
                },
                bytes = topoJobs.sumOf { it.bytes },
                message = "${topoJobs.count { it.done }}/${topoJobs.size} tiles"
            ),
            PackCheckItem(
                "dem", "USGS 3DEP", pack.includeDem,
                when {
                    !pack.includeDem -> PackItemStatus.SKIPPED
                    demOk -> PackItemStatus.OK
                    else -> PackItemStatus.MISSING
                }
            ),
            PackCheckItem(
                "3d", "3D terrain", pack.includeDem,
                when {
                    !pack.includeDem -> PackItemStatus.SKIPPED
                    demOk -> PackItemStatus.OK
                    else -> PackItemStatus.MISSING
                },
                message = "Derived from local DEM"
            ),
            PackCheckItem(
                "hillshade", "Hillshade", pack.includeHillshade,
                when {
                    !pack.includeHillshade -> PackItemStatus.SKIPPED
                    shadeOk || demOk -> PackItemStatus.OK
                    else -> PackItemStatus.MISSING
                },
                bytes = shadeJobs.sumOf { it.bytes }
            ),
            PackCheckItem(
                "imagery", "USGS NAIP", pack.includeImagery,
                when {
                    !pack.includeImagery -> PackItemStatus.SKIPPED
                    imageryFile.exists() && imageryFile.length() > 0 -> PackItemStatus.OK
                    else -> PackItemStatus.MISSING
                },
                bytes = if (imageryFile.exists()) imageryFile.length() else 0
            ),
            PackCheckItem(
                "weather", "NWS snapshot", pack.includeConditions,
                when {
                    !pack.includeConditions -> PackItemStatus.SKIPPED
                    snapshot == null -> PackItemStatus.MISSING
                    snapshot.isStale() -> PackItemStatus.STALE
                    else -> PackItemStatus.OK
                },
                message = snapshot?.let { "Issued ${it.issuedAt.ifBlank { "unknown" }}" }.orEmpty()
            ),
            PackCheckItem(
                "avalanche", "Avalanche source", false,
                PackItemStatus.ONLINE_ONLY,
                message = "Official forecast pages only — not packaged"
            )
        )
        dao.update(
            pack.copy(
                manifestJson = checksToJson(checks),
                verifiedAt = System.currentTimeMillis(),
                actualBytes = measureBytes(pack, jobs),
                status = statusFrom(checks, pack).name,
                updatedAt = System.currentTimeMillis()
            )
        )
        checks
    }

    suspend fun delete(packId: Long) {
        packDir(packId).deleteRecursively()
        dao.deleteById(packId)
    }

    fun storageBytes(): Long {
        var total = 0L
        packRoot.walkTopDown().forEach { if (it.isFile) total += it.length() }
        File(appContext.filesDir, "offline_tiles").walkTopDown().forEach { if (it.isFile) total += it.length() }
        File(appContext.filesDir, "dem").walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    fun parseManifest(json: String): List<PackCheckItem> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PackCheckItem(
                id = o.getString("id"),
                label = o.getString("label"),
                required = o.optBoolean("required"),
                status = PackItemStatus.valueOf(o.getString("status")),
                bytes = o.optLong("bytes"),
                message = o.optString("message")
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun stopIfNeeded(packId: Long, jobs: List<TileJob>): Boolean {
        val pack = dao.getById(packId) ?: return true
        if (cancelRequested) {
            dao.update(pack.copy(status = TripPackStatus.FAILED.name, error = "Cancelled", updatedAt = System.currentTimeMillis()))
            _progress.value = TripPackProgress(error = "Cancelled", packId = packId)
            return true
        }
        if (pauseRequested) {
            dao.update(pack.copy(status = TripPackStatus.PAUSED.name, actualBytes = tiles.measuredBytes(jobs), updatedAt = System.currentTimeMillis()))
            _progress.value = TripPackProgress(paused = true, packId = packId, downloadedBytes = tiles.measuredBytes(jobs), totalTiles = jobs.size, completedTiles = jobs.count { it.done })
            return true
        }
        return false
    }

    private fun statusFrom(checks: List<PackCheckItem>, pack: TripPackEntity): TripPackStatus {
        val required = checks.filter { it.required && it.status != PackItemStatus.SKIPPED }
        if (required.any { it.status == PackItemStatus.MISSING || it.status == PackItemStatus.FAILED }) {
            return if (checks.any { it.status == PackItemStatus.OK }) TripPackStatus.PARTIAL else TripPackStatus.FAILED
        }
        if (checks.any { it.status == PackItemStatus.STALE }) return TripPackStatus.STALE
        if (required.all { it.status == PackItemStatus.OK }) return TripPackStatus.READY
        return TripPackStatus.PARTIAL
    }

    private fun measureBytes(pack: TripPackEntity, jobs: List<TileJob>): Long {
        var bytes = tiles.measuredBytes(jobs)
        val dir = packDir(pack.id)
        File(dir, "naip.jpg").takeIf { it.exists() }?.let { bytes += it.length() }
        File(dir, "nws.json").takeIf { it.exists() }?.let { bytes += it.length() }
        return bytes
    }

    private fun writeJobs(packId: Long, jobs: List<TileJob>) {
        val arr = JSONArray()
        jobs.forEach { job ->
            arr.put(JSONObject().apply {
                put("sourceId", job.sourceId)
                put("z", job.z)
                put("x", job.x)
                put("y", job.y)
                put("done", job.done)
                put("bytes", job.bytes)
                put("failed", job.failed)
            })
        }
        File(packDir(packId), "jobs.json").writeText(arr.toString())
    }

    private fun readJobs(packId: Long): List<TileJob> {
        val file = File(packDir(packId), "jobs.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TileJob(
                    sourceId = o.getString("sourceId"),
                    z = o.getInt("z"),
                    x = o.getInt("x"),
                    y = o.getInt("y"),
                    done = o.optBoolean("done"),
                    bytes = o.optLong("bytes"),
                    failed = o.optBoolean("failed")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun checksToJson(checks: List<PackCheckItem>): String {
        val arr = JSONArray()
        checks.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("label", item.label)
                put("required", item.required)
                put("status", item.status.name)
                put("bytes", item.bytes)
                put("message", item.message)
            })
        }
        return arr.toString()
    }

    companion object {
        const val MIN_ZOOM = 10
    }
}
