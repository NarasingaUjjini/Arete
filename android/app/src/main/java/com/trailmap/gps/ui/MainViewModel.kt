package com.trailmap.gps.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trailmap.gps.TrailMapApp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.ParsedRoute
import com.trailmap.gps.data.RouteEntity
import com.trailmap.gps.data.RouteRepository
import com.trailmap.gps.data.RouteSource
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.importing.RouteParser
import com.trailmap.gps.data.OffRouteCorridorSetting
import com.trailmap.gps.data.Waypoint
import com.trailmap.gps.geo.ElevationStats
import com.trailmap.gps.geo.GeoMath
import com.trailmap.gps.geo.OffRouteCorridor
import com.trailmap.gps.geo.RouteGeometry
import com.trailmap.gps.location.GnssAssistance
import com.trailmap.gps.location.GpsUpdate
import com.trailmap.gps.location.LocationSession
import com.trailmap.gps.location.RecordingPhase
import com.trailmap.gps.export.GpxExporter
import com.trailmap.gps.location.TrackingService
import com.trailmap.gps.offline.OfflineDownloadState
import com.trailmap.gps.terrain.DemGrid
import com.trailmap.gps.terrain.TerrainInspection
import com.trailmap.gps.terrain.TerrainOverlay
import com.trailmap.gps.terrain.VerticalSpeedTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TrailMapApp
    private val routeRepository = RouteRepository(app.database.routeDao())
    private val locationEngine = app.locationEngine
    private val headingEngine = app.headingEngine
    private val positionEngine = app.positionEngine
    private val offlineManager = app.offlineTileManager
    private val demRepository = app.demRepository
    private val tripPacks = app.tripPackManager
    private val conditionsRepository = app.conditionsRepository
    private val recInfoRepository = app.recInfoRepository
    private val gnssAssistance = GnssAssistance(application)
    private val verticalSpeed = VerticalSpeedTracker()

    val settings: StateFlow<AppSettings> = app.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val routes = routeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importPreview = MutableStateFlow<ParsedRoute?>(null)
    val importPreview: StateFlow<ParsedRoute?> = _importPreview.asStateFlow()

    private val _selectedRoute = MutableStateFlow<RouteEntity?>(null)
    val selectedRoute: StateFlow<RouteEntity?> = _selectedRoute.asStateFlow()

    private val _currentLocation = MutableStateFlow<GpsUpdate?>(null)
    val currentLocation: StateFlow<GpsUpdate?> = _currentLocation.asStateFlow()
    val locationState = locationEngine.state
    val deviceHeading = headingEngine.heading
    val position = positionEngine.snapshot

    val isRecording = TrackingService.isRecording
    val recordingPhase = TrackingService.phase
    val recordedPoints = TrackingService.trackPoints

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<TrackPoint>>(emptyList())
    val breadcrumbs: StateFlow<List<TrackPoint>> = _breadcrumbs.asStateFlow()
    private var lastBreadcrumb: TrackPoint? = null
    private var lastOffRoute = false

    val offlineState: StateFlow<OfflineDownloadState> = offlineManager.state
    val demDownload = demRepository.download
    val tripPackProgress = tripPacks.progress
    val allTripPacks = tripPacks.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val forcedOffline = com.trailmap.gps.offline.NetworkGate.forcedOffline
    private val _packChecks = MutableStateFlow<List<com.trailmap.gps.data.PackCheckItem>>(emptyList())
    val packChecks: StateFlow<List<com.trailmap.gps.data.PackCheckItem>> = _packChecks.asStateFlow()

    private val _conditions = MutableStateFlow<com.trailmap.gps.conditions.MountainConditions?>(null)
    val conditions: StateFlow<com.trailmap.gps.conditions.MountainConditions?> = _conditions.asStateFlow()
    private val _conditionsLoading = MutableStateFlow(false)
    val conditionsLoading: StateFlow<Boolean> = _conditionsLoading.asStateFlow()
    private val _recInfo = MutableStateFlow<com.trailmap.gps.conditions.RecInfoSnapshot?>(null)
    val recInfo: StateFlow<com.trailmap.gps.conditions.RecInfoSnapshot?> = _recInfo.asStateFlow()
    private val _recInfoLoading = MutableStateFlow(false)
    val recInfoLoading: StateFlow<Boolean> = _recInfoLoading.asStateFlow()

    private val _inspection = MutableStateFlow<TerrainInspection?>(null)
    val inspection: StateFlow<TerrainInspection?> = _inspection.asStateFlow()
    private var measureAnchor: TerrainInspection? = null
    private var areaDownloadJob: Job? = null

    private val _terrainOverlay = MutableStateFlow(TerrainOverlay.NONE)
    val terrainOverlay: StateFlow<TerrainOverlay> = _terrainOverlay.asStateFlow()

    private val _verticalMph = MutableStateFlow<Double?>(null)
    val verticalMetersPerHour: StateFlow<Double?> = _verticalMph.asStateFlow()

    private val _profileDistance = MutableStateFlow<Double?>(null)
    val profileDistance: StateFlow<Double?> = _profileDistance.asStateFlow()

    private val _sectionStart = MutableStateFlow<Double?>(null)
    val sectionStart: StateFlow<Double?> = _sectionStart.asStateFlow()

    private val _sectionEnd = MutableStateFlow<Double?>(null)
    val sectionEnd: StateFlow<Double?> = _sectionEnd.asStateFlow()

    private val _gnssRefreshing = MutableStateFlow(false)
    val gnssRefreshing: StateFlow<Boolean> = _gnssRefreshing.asStateFlow()

    private val _gnssMessage = MutableStateFlow<String?>(null)
    val gnssMessage: StateFlow<String?> = _gnssMessage.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()
    private val _returnByMinutes = MutableStateFlow(19 * 60 + 30)
    val returnByMinutes: StateFlow<Int> = _returnByMinutes.asStateFlow()
    private val _magneticNorth = MutableStateFlow(false)
    val magneticNorth: StateFlow<Boolean> = _magneticNorth.asStateFlow()
    private val _activeMapTool = MutableStateFlow<com.trailmap.gps.ui.maptools.MapTool?>(null)
    val activeMapTool: StateFlow<com.trailmap.gps.ui.maptools.MapTool?> = _activeMapTool.asStateFlow()

    private val _batterySaver = MutableStateFlow(false)
    val batterySaver: StateFlow<Boolean> = _batterySaver.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _drawPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val drawPoints: StateFlow<List<TrackPoint>> = _drawPoints.asStateFlow()

    private val _isDrawingRoute = MutableStateFlow(false)
    val isDrawingRoute: StateFlow<Boolean> = _isDrawingRoute.asStateFlow()
    private val _drawTool = MutableStateFlow(com.trailmap.gps.data.DrawTool.NONE)
    val drawTool: StateFlow<com.trailmap.gps.data.DrawTool> = _drawTool.asStateFlow()
    private val _drawRedo = MutableStateFlow<List<TrackPoint>>(emptyList())
    val drawRedo: StateFlow<List<TrackPoint>> = _drawRedo.asStateFlow()
    private val _drawPins = MutableStateFlow<List<TrackPoint>>(emptyList())
    val drawPins: StateFlow<List<TrackPoint>> = _drawPins.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _recenterTrigger = MutableStateFlow(0)
    val recenterTrigger: StateFlow<Int> = _recenterTrigger.asStateFlow()

    private val _fitRouteTrigger = MutableStateFlow(0)
    val fitRouteTrigger: StateFlow<Int> = _fitRouteTrigger.asStateFlow()

    private val _mapBearing = MutableStateFlow(0.0)
    val mapBearing: StateFlow<Double> = _mapBearing.asStateFlow()

    private val _showSaveDrawDialog = MutableStateFlow(false)
    val showSaveDrawDialog: StateFlow<Boolean> = _showSaveDrawDialog.asStateFlow()

    private val _orientationTrigger = MutableStateFlow(0)
    val orientationTrigger: StateFlow<Int> = _orientationTrigger.asStateFlow()

    val filteredRoutes = kotlinx.coroutines.flow.combine(routes, searchQuery, settings) { list, query, current ->
        list.filter { route ->
            val nameOk = query.isBlank() || route.name.contains(query, ignoreCase = true)
            val filterOk = when (current.libraryFilter) {
                com.trailmap.gps.data.RouteLibraryFilter.ALL -> true
                com.trailmap.gps.data.RouteLibraryFilter.OFFLINE -> route.offlineDownloaded
                com.trailmap.gps.data.RouteLibraryFilter.IMPORTED -> route.source == RouteSource.IMPORTED
                com.trailmap.gps.data.RouteLibraryFilter.DRAWN -> route.source == RouteSource.DRAWN
                com.trailmap.gps.data.RouteLibraryFilter.RECORDED -> route.source == RouteSource.RECORDED
            }
            nameOk && filterOk
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        locationEngine.acquire(LocationSession.BROWSING)
        positionEngine.terrainMeters = { lat, lon ->
            runCatching { app.demRepository.gridCovering(lat, lon)?.interpolate(lat, lon) }.getOrNull()
        }
        positionEngine.routeDistanceMeters = { lat, lon ->
            val route = _selectedRoute.value
            if (!_isNavigating.value || route == null) {
                null
            } else {
                com.trailmap.gps.geo.RouteGeometry.project(getRoutePoints(route), lat, lon)?.distanceToRouteMeters
            }
        }
        positionEngine.start()
        viewModelScope.launch {
            settings.collect { locationEngine.setPowerProfile(it.powerProfile) }
        }
        viewModelScope.launch {
            positionEngine.snapshot.collect { snap ->
                val update = snap.toGpsUpdate() ?: locationEngine.state.value.toGpsUpdate()
                _currentLocation.value = update
                if (update != null && update.recordable) {
                    verticalSpeed.add(update.timestamp, update.elevation)
                    _verticalMph.value = verticalSpeed.metersPerHour(update.timestamp)
                    if (_isNavigating.value) appendBreadcrumb(update)
                }
            }
        }
    }

    fun setHeadingWanted(wanted: Boolean) {
        if (wanted) headingEngine.acquire("ui") else headingEngine.release("ui")
    }

    override fun onCleared() {
        locationEngine.release(LocationSession.BROWSING)
        locationEngine.release(LocationSession.NAVIGATION)
        headingEngine.release("ui")
        positionEngine.stop()
        super.onCleared()
    }

    fun parseImport(inputStream: InputStream, fileName: String) {
        viewModelScope.launch {
            try {
                _importError.value = null
                _importPreview.value = RouteParser.parse(inputStream, fileName)
            } catch (e: Exception) {
                _importPreview.value = null
                _importError.value = e.message ?: "Could not import file"
            }
        }
    }

    fun clearImportError() {
        _importError.value = null
    }

    fun clearImportPreview() {
        _importPreview.value = null
    }

    suspend fun saveImport(source: RouteSource = RouteSource.IMPORTED): Long? {
        val preview = _importPreview.value ?: return null
        val id = routeRepository.save(preview, source)
        _importPreview.value = null
        return id
    }

    fun selectRoute(id: Long) {
        viewModelScope.launch {
            _selectedRoute.value = routeRepository.getById(id)
            fitRouteOnMap()
        }
    }

    fun clearSelectedRoute() {
        _selectedRoute.value = null
    }

    fun deleteRoute(id: Long) {
        viewModelScope.launch {
            routeRepository.delete(id)
            if (_selectedRoute.value?.id == id) _selectedRoute.value = null
        }
    }

    fun getRoutePoints(route: RouteEntity): List<TrackPoint> =
        routeRepository.parsePoints(route.pointsJson)

    fun getRouteWaypoints(route: RouteEntity): List<Waypoint> =
        routeRepository.parseWaypoints(route.waypointsJson)

    fun startNavigation() {
        _isNavigating.value = true
        lastOffRoute = false
        lastBreadcrumb = null
        _breadcrumbs.value = emptyList()
        locationEngine.acquire(LocationSession.NAVIGATION)
    }

    fun stopNavigation() {
        _isNavigating.value = false
        _batterySaver.value = false
        locationEngine.release(LocationSession.NAVIGATION)
    }

    fun clearBreadcrumb() {
        _breadcrumbs.value = emptyList()
        lastBreadcrumb = null
    }

    fun toggleBatterySaver() {
        _batterySaver.value = !_batterySaver.value
    }

    fun startRecording() = sendTracking(TrackingService.Actions.ACTION_START, foreground = true)
    fun pauseRecording() = sendTracking(TrackingService.Actions.ACTION_PAUSE)
    fun resumeRecording() = sendTracking(TrackingService.Actions.ACTION_RESUME, foreground = true)
    fun stopRecording() = sendTracking(TrackingService.Actions.ACTION_STOP)
    fun discardRecording() = sendTracking(TrackingService.Actions.ACTION_DISCARD)

    private fun sendTracking(action: String, foreground: Boolean = false) {
        val intent = Intent(getApplication(), TrackingService::class.java).apply {
            this.action = action
        }
        if (foreground) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    suspend fun saveRecordedTrack(name: String, notes: String = ""): Long? {
        val points = ElevationStats.enrich(recordedPoints.value)
        if (points.size < 2) return null
        val stats = ElevationStats.calculate(points)
        val parsed = ParsedRoute(
            name = name.ifBlank { "Recorded track" },
            points = points,
            distanceMeters = stats.distanceMeters,
            elevationGainMeters = stats.elevationGainMeters,
            elevationLossMeters = stats.elevationLossMeters,
            maxElevationMeters = stats.maxElevationMeters,
            estimatedTimeSeconds = 0
        )
        val id = routeRepository.save(parsed, RouteSource.RECORDED)
        TrackingService.resetTrack()
        return id
    }

    fun recordingElapsedSeconds(): Long = TrackingService.elapsedSeconds()

    fun downloadArea(bounds: DoubleArray, maxZoom: Int = 15, includeDem: Boolean = true, routeId: Long? = null) {
        downloadOffline(routeId ?: -1L, bounds, maxZoom, includeDem)
    }

    fun cancelAreaDownload() {
        areaDownloadJob?.cancel()
        areaDownloadJob = null
        offlineManager.cancelDownload()
        demRepository.cancelDownload()
    }

    fun downloadOffline(routeId: Long, bounds: DoubleArray, maxZoom: Int = 15, includeDem: Boolean = true) {
        areaDownloadJob?.cancel()
        areaDownloadJob = viewModelScope.launch {
            val current = settings.value
            val bytes = offlineManager.downloadForBounds(
                bounds = bounds,
                minZoom = 10,
                maxZoom = maxZoom,
                layer = current.mapLayer,
                hillshade = current.hillshadeEnabled
            )
            if (bytes > 0 && routeId > 0) {
                routeRepository.markOfflineDownloaded(routeId, bytes)
                _selectedRoute.value = routeRepository.getById(routeId)
            }
            if (includeDem && offlineManager.state.value.error != "Download cancelled") {
                demRepository.downloadForBounds(
                    com.trailmap.gps.data.providers.BoundingBox(bounds[0], bounds[1], bounds[2], bounds[3])
                )
            }
            if (offlineManager.state.value.error != "Download cancelled") {
                refreshGnssAssistance()
            }
        }
    }

    fun inspectTerrain(lat: Double, lon: Double) {
        val previous = measureAnchor
        _inspection.value = TerrainInspection(lat = lat, lon = lon, loading = true, source = "Loading…")
        viewModelScope.launch {
            val route = _selectedRoute.value
            val points = route?.let { getRoutePoints(it) }.orEmpty()
            val result = demRepository.inspectOrFetch(
                lat = lat,
                lon = lon,
                userElevation = _currentLocation.value?.elevation,
                routePoints = points
            )
            val prior = previous
            val fromEle = prior?.demElevationMeters
            val toEle = result.demElevationMeters
            val filled = if (
                prior != null &&
                fromEle != null &&
                toEle != null &&
                (kotlin.math.abs(prior.lat - lat) > 1e-7 || kotlin.math.abs(prior.lon - lon) > 1e-7)
            ) {
                val distance = GeoMath.haversineMeters(prior.lat, prior.lon, lat, lon)
                val rise = toEle - fromEle
                result.copy(
                    avgFromLat = prior.lat,
                    avgFromLon = prior.lon,
                    avgDistanceMeters = distance,
                    avgRiseMeters = rise,
                    avgGradePercent = ElevationStats.gradePercent(rise, distance),
                    avgSlopeDegrees = ElevationStats.slopeDegrees(rise, distance)
                )
            } else {
                result
            }
            _inspection.value = filled
            measureAnchor = filled
        }
    }

    fun clearInspection() {
        _inspection.value = null
        measureAnchor = null
    }

    fun setTerrainOverlay(mode: TerrainOverlay) {
        _terrainOverlay.value = mode
    }

    fun overlayBitmap(): Pair<android.graphics.Bitmap, com.trailmap.gps.data.providers.BoundingBox>? {
        val mode = _terrainOverlay.value
        val loc = _currentLocation.value
        val route = _selectedRoute.value
        val focusLat = loc?.latitude ?: route?.let { getRoutePoints(it).firstOrNull()?.lat }
        val focusLon = loc?.longitude ?: route?.let { getRoutePoints(it).firstOrNull()?.lon }
        val routePoints = route?.let { getRoutePoints(it) }.orEmpty()
        if (focusLat == null || focusLon == null) {
            val latest = demRepository.loadLatest() ?: return null
            return demRepository.overlayBitmap(
                mode,
                (latest.minLat + latest.maxLat) / 2,
                (latest.minLon + latest.maxLon) / 2,
                routePoints
            )
        }
        return demRepository.overlayBitmap(mode, focusLat, focusLon, routePoints)
    }

    fun hasLocalDem(lat: Double? = null, lon: Double? = null): Boolean {
        val y = lat ?: _currentLocation.value?.latitude ?: _selectedRoute.value?.let { getRoutePoints(it).firstOrNull()?.lat }
        val x = lon ?: _currentLocation.value?.longitude ?: _selectedRoute.value?.let { getRoutePoints(it).firstOrNull()?.lon }
        return if (y != null && x != null) demRepository.hasCoverage(y, x) else demRepository.packs().isNotEmpty()
    }

    fun activeDemGrid(): DemGrid? {
        val loc = _currentLocation.value
        val route = _selectedRoute.value
        val lat = loc?.latitude ?: route?.let { getRoutePoints(it).firstOrNull()?.lat }
        val lon = loc?.longitude ?: route?.let { getRoutePoints(it).firstOrNull()?.lon }
        return if (lat != null && lon != null) demRepository.gridCovering(lat, lon) else demRepository.loadLatest()
    }

    fun demProfile(points: List<TrackPoint>): List<TrackPoint> {
        val grid = activeDemGrid() ?: return emptyList()
        return points.mapNotNull { p ->
            val z = grid.interpolate(p.lat, p.lon) ?: return@mapNotNull null
            p.copy(elevation = z)
        }
    }

    fun downloadTerrainForSelected() {
        val route = _selectedRoute.value ?: return
        viewModelScope.launch {
            demRepository.downloadForRoute(getRoutePoints(route))
        }
    }

    fun packForRoute(routeId: Long): com.trailmap.gps.data.TripPackEntity? =
        allTripPacks.value.firstOrNull { it.routeId == routeId }

    fun storageBytes(): Long = tripPacks.storageBytes()

    fun estimatePackTiles(
        points: List<TrackPoint>,
        corridor: com.trailmap.gps.offline.CorridorBuffer,
        mode: com.trailmap.gps.data.TripPackAreaMode,
        includeTopo: Boolean,
        includeHillshade: Boolean,
        maxZoom: Int
    ): Int = tripPacks.estimateTiles(points, corridor, mode, null, includeTopo, includeHillshade, maxZoom)

    fun startTripPack(
        route: RouteEntity,
        corridor: com.trailmap.gps.offline.CorridorBuffer,
        mode: com.trailmap.gps.data.TripPackAreaMode,
        includeTopo: Boolean,
        includeDem: Boolean,
        includeHillshade: Boolean,
        includeImagery: Boolean,
        includeConditions: Boolean,
        maxZoom: Int
    ) {
        viewModelScope.launch {
            val points = getRoutePoints(route)
            val pack = tripPacks.prepare(
                routeId = route.id,
                name = route.name,
                points = points,
                corridor = corridor,
                areaMode = mode,
                customBounds = null,
                includeTopo = includeTopo,
                includeDem = includeDem,
                includeHillshade = includeHillshade,
                includeImagery = includeImagery,
                includeConditions = includeConditions,
                maxZoom = maxZoom
            )
            tripPacks.start(pack.id)
            _packChecks.value = tripPacks.parseManifest(tripPacks.get(pack.id)?.manifestJson ?: "[]")
        }
    }

    fun pauseTripPack() = tripPacks.pause()

    fun resumeTripPack(packId: Long) {
        viewModelScope.launch { tripPacks.start(packId) }
    }

    fun cancelTripPack() = tripPacks.cancel()

    fun verifyTripPack(packId: Long) {
        viewModelScope.launch {
            _packChecks.value = tripPacks.verify(packId)
        }
    }

    fun deleteTripPack(packId: Long) {
        viewModelScope.launch {
            tripPacks.delete(packId)
            _packChecks.value = emptyList()
        }
    }

    fun setForcedOffline(value: Boolean) = tripPacks.setForcedOffline(value)

    fun loadPackChecks(pack: com.trailmap.gps.data.TripPackEntity?) {
        _packChecks.value = pack?.let { tripPacks.parseManifest(it.manifestJson) }.orEmpty()
    }

    fun refreshRecInfo() {
        if (_recInfoLoading.value) return
        val loc = _currentLocation.value
        if (loc == null) {
            _recInfo.value = com.trailmap.gps.conditions.RecInfoSnapshot(
                error = "Need a GPS fix to search nearby."
            )
            return
        }
        viewModelScope.launch {
            _recInfoLoading.value = true
            _recInfo.value = recInfoRepository.load(loc.latitude, loc.longitude)
            _recInfoLoading.value = false
        }
    }

    fun refreshConditions() {
        if (_conditionsLoading.value) return
        viewModelScope.launch {
            _conditionsLoading.value = true
            val route = _selectedRoute.value
            _conditions.value = conditionsRepository.load(
                points = route?.let { getRoutePoints(it) }.orEmpty(),
                waypoints = route?.let { getRouteWaypoints(it) }.orEmpty(),
                userLat = _currentLocation.value?.latitude,
                userLon = _currentLocation.value?.longitude
            )
            _conditionsLoading.value = false
        }
    }

    fun setProfileDistance(distanceMeters: Double?) {
        _profileDistance.value = distanceMeters
    }

    fun markSectionStart() {
        _sectionStart.value = _profileDistance.value
    }

    fun markSectionEnd() {
        _sectionEnd.value = _profileDistance.value
    }

    fun clearSection() {
        _sectionStart.value = null
        _sectionEnd.value = null
    }

    fun estimateOfflineTiles(bounds: DoubleArray, maxZoom: Int): Int {
        val current = settings.value
        return offlineManager.estimateTileCountForBounds(
            bounds = bounds,
            minZoom = 10,
            maxZoom = maxZoom,
            layer = current.mapLayer,
            hillshade = current.hillshadeEnabled
        )
    }

    fun offlineLayerLabels(): String {
        val current = settings.value
        return offlineManager.sourceLabels(current.mapLayer, current.hillshadeEnabled)
    }

    fun refreshGnssAssistance() {
        if (_gnssRefreshing.value) return
        viewModelScope.launch {
            _gnssRefreshing.value = true
            _gnssMessage.value = null
            val result = gnssAssistance.refresh()
            if (result.success) {
                app.settingsRepository.setGnssAssistanceUpdatedAt(System.currentTimeMillis())
            }
            _gnssMessage.value = result.message
            _gnssRefreshing.value = false
        }
    }

    fun navigationState(route: RouteEntity, location: GpsUpdate?): NavigationState {
        val points = getRoutePoints(route)
        val waypoints = getRouteWaypoints(route)
        if (location == null || points.isEmpty()) {
            return NavigationState()
        }
        val projection = RouteGeometry.project(points, location.latitude, location.longitude)
            ?: return NavigationState()
        val corridor = when (settings.value.offRouteCorridor) {
            OffRouteCorridorSetting.NARROW -> OffRouteCorridor.NARROW
            OffRouteCorridorSetting.NORMAL -> OffRouteCorridor.NORMAL
            OffRouteCorridorSetting.WIDE -> OffRouteCorridor.WIDE
        }
        val off = RouteGeometry.offRouteState(
            previousOffRoute = lastOffRoute,
            distanceToRoute = projection.distanceToRouteMeters,
            accuracyMeters = location.accuracy,
            enterThresholdMeters = corridor.enterMeters,
            exitThresholdMeters = corridor.exitMeters
        )
        lastOffRoute = off.isOffRoute

        val next = nextCheckpoint(waypoints, points, projection.distanceAlongRouteMeters, location.latitude, location.longitude)
            ?: nextAlongRoute(points, projection.distanceAlongRouteMeters)
        val remainingAscent = ElevationStats.remainingAscent(points, projection.distanceAlongRouteMeters)
        return NavigationState(
            distanceToNext = next?.distanceMeters ?: RouteGeometry.remainingFromUser(projection),
            bearing = next?.bearing ?: 0.0,
            course = location.bearing.toDouble(),
            elevation = location.elevation,
            remainingDistance = RouteGeometry.remainingFromUser(projection),
            remainingAscent = remainingAscent,
            isOffRoute = off.isOffRoute,
            gpsDegraded = off.gpsDegraded,
            statusMessage = off.message,
            progress = projection.progress.toFloat(),
            nearestIndex = projection.segmentIndex,
            distanceAlongRoute = projection.distanceAlongRouteMeters,
            totalDistance = projection.totalDistanceMeters,
            nextLabel = next?.label ?: "ROUTE AHEAD",
            nextType = next?.type ?: ""
        )
    }

    private data class NextTarget(val label: String, val type: String, val distanceMeters: Double, val bearing: Double)

    private fun nextCheckpoint(
        waypoints: List<Waypoint>,
        points: List<TrackPoint>,
        along: Double,
        lat: Double,
        lon: Double
    ): NextTarget? {
        val target = RouteGeometry.nextWaypointAhead(waypoints, points, along) ?: return null
        val waypoint = target.first
        val alongToWaypoint = target.second
        return NextTarget(
            label = waypoint.name,
            type = waypoint.type,
            distanceMeters = (alongToWaypoint - along).coerceAtLeast(0.0),
            bearing = GeoMath.initialBearingDegrees(lat, lon, waypoint.lat, waypoint.lon)
        )
    }

    fun exportRoute(context: android.content.Context, route: RouteEntity) {
        val file = GpxExporter.write(context, route.name, getRoutePoints(route), getRouteWaypoints(route))
        GpxExporter.share(context, file)
    }

    private fun nextAlongRoute(points: List<TrackPoint>, along: Double): NextTarget {
        val last = points.last()
        val ahead = points.firstOrNull { it.cumulativeDistanceMeters > along } ?: last
        val from = points.lastOrNull { it.cumulativeDistanceMeters <= along } ?: points.first()
        return NextTarget(
            label = "ROUTE AHEAD",
            type = "",
            distanceMeters = (last.cumulativeDistanceMeters - along).coerceAtLeast(0.0),
            bearing = GeoMath.initialBearingDegrees(from.lat, from.lon, ahead.lat, ahead.lon)
        )
    }

    private fun appendBreadcrumb(update: GpsUpdate) {
        val last = lastBreadcrumb
        val next = TrackPoint(update.latitude, update.longitude, update.elevation, update.timestamp)
        if (last == null || GeoMath.haversineMeters(last.lat, last.lon, next.lat, next.lon) >= 8.0) {
            lastBreadcrumb = next
            _breadcrumbs.value = _breadcrumbs.value + next
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateDistanceUnit(unit: com.trailmap.gps.data.DistanceUnit) {
        viewModelScope.launch { app.settingsRepository.setDistanceUnit(unit) }
    }

    fun updateElevationUnit(unit: com.trailmap.gps.data.ElevationUnit) {
        viewModelScope.launch { app.settingsRepository.setElevationUnit(unit) }
    }

    fun updateCoordinateFormat(format: com.trailmap.gps.data.CoordinateFormat) {
        viewModelScope.launch { app.settingsRepository.setCoordinateFormat(format) }
    }

    fun updatePowerProfile(profile: com.trailmap.gps.data.PowerProfile) {
        viewModelScope.launch { app.settingsRepository.setPowerProfile(profile) }
    }

    fun updateMapLayer(layer: com.trailmap.gps.data.MapLayer) {
        viewModelScope.launch { app.settingsRepository.setMapLayer(layer) }
    }

    fun cycleReturnBy() {
        val options = listOf(16 * 60, 17 * 60 + 30, 19 * 60, 19 * 60 + 30, 21 * 60)
        val cur = _returnByMinutes.value
        val idx = options.indexOf(cur).let { if (it < 0) 0 else (it + 1) % options.size }
        _returnByMinutes.value = options[idx]
    }

    fun returnByLabel(): String {
        val m = _returnByMinutes.value
        val h24 = m / 60
        val min = m % 60
        val am = h24 < 12
        val h12 = when (val h = h24 % 12) { 0 -> 12; else -> h }
        return "%d:%02d %s".format(h12, min, if (am) "AM" else "PM")
    }

    fun returnByMs(nowMs: Long = System.currentTimeMillis()): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        cal.set(java.util.Calendar.HOUR_OF_DAY, _returnByMinutes.value / 60)
        cal.set(java.util.Calendar.MINUTE, _returnByMinutes.value % 60)
        cal.set(java.util.Calendar.SECOND, 0)
        if (cal.timeInMillis < nowMs) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun toggleMagneticNorth() {
        _magneticNorth.value = !_magneticNorth.value
    }

    fun routeSegments(route: RouteEntity) =
        com.trailmap.gps.geo.RouteSegments.build(getRoutePoints(route), getRouteWaypoints(route))

    fun summitWaypoint(route: RouteEntity) =
        com.trailmap.gps.geo.RouteSegments.summit(getRoutePoints(route), getRouteWaypoints(route))

    fun bailoutWaypoints(route: RouteEntity) =
        com.trailmap.gps.geo.RouteSegments.bailouts(getRouteWaypoints(route))

    fun summitBearing(route: RouteEntity, location: GpsUpdate?): Double? {
        val summit = summitWaypoint(route) ?: return null
        val lat = location?.latitude ?: return null
        val lon = location.longitude
        return GeoMath.initialBearingDegrees(lat, lon, summit.lat, summit.lon)
    }

    fun turnaroundPlan(route: RouteEntity, location: GpsUpdate?): com.trailmap.gps.geo.TurnaroundPlan? {
        val points = getRoutePoints(route)
        val loc = location ?: return null
        if (points.isEmpty()) return null
        val proj = RouteGeometry.project(points, loc.latitude, loc.longitude) ?: return null
        val pace = com.trailmap.gps.geo.TurnaroundPlanner.movingPace(_breadcrumbs.value)
        return com.trailmap.gps.geo.TurnaroundPlanner.plan(
            remainingOutMeters = RouteGeometry.remainingFromUser(proj),
            remainingBackMeters = proj.distanceAlongRouteMeters,
            paceMetersPerSecond = pace,
            lat = loc.latitude,
            lon = loc.longitude,
            returnByMs = returnByMs()
        )
    }

    fun backtrackMeters(route: RouteEntity): Double {
        val corridor = when (settings.value.offRouteCorridor) {
            OffRouteCorridorSetting.NARROW -> 25.0
            OffRouteCorridorSetting.NORMAL -> 45.0
            OffRouteCorridorSetting.WIDE -> 80.0
        }
        return com.trailmap.gps.geo.TurnaroundPlanner.backtrackMeters(
            _breadcrumbs.value,
            getRoutePoints(route),
            corridor
        )
    }

    fun remainingToTrailhead(route: RouteEntity, location: GpsUpdate?): Double {
        val points = getRoutePoints(route)
        val loc = location ?: return RouteGeometry.totalDistance(points)
        val proj = RouteGeometry.project(points, loc.latitude, loc.longitude) ?: return 0.0
        return proj.distanceAlongRouteMeters
    }

    fun contourGeoJson(maxDim: Int = com.trailmap.gps.terrain.ContourGenerator.DEFAULT_MAX_DIM): String? {
        if (!settings.value.contoursEnabled) return null
        val grid = activeDemGrid() ?: return null
        return com.trailmap.gps.terrain.ContourGenerator.toGeoJson(grid, maxDim)
    }

    fun updateContours(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setContours(enabled) }
    }

    fun updateHillshade(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setHillshade(enabled) }
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setKeepScreenOn(enabled) }
    }

    fun updateOffRouteCorridor(value: OffRouteCorridorSetting) {
        viewModelScope.launch { app.settingsRepository.setOffRouteCorridor(value) }
    }

    fun updateNorthUp(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setNorthUp(enabled) }
    }

    fun toggleNorthUp() {
        viewModelScope.launch {
            val current = settings.value
            app.settingsRepository.setNorthUp(!current.northUp)
            _orientationTrigger.value += 1
        }
    }

    fun alignNorth() {
        viewModelScope.launch {
            app.settingsRepository.setNorthUp(true)
            _orientationTrigger.value += 1
        }
    }

    fun recenter() {
        _recenterTrigger.value += 1
    }

    fun fitRouteOnMap() {
        _fitRouteTrigger.value += 1
    }

    fun setMapBearing(bearing: Double) {
        _mapBearing.value = bearing
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun toggleDrawRoute() {
        if (_isDrawingRoute.value) cancelDrawing() else startDrawing()
    }

    fun startDrawing() {
        _isDrawingRoute.value = true
        _drawTool.value = com.trailmap.gps.data.DrawTool.ROUTE
        _drawPoints.value = emptyList()
        _drawRedo.value = emptyList()
        _drawPins.value = emptyList()
    }

    fun cancelDrawing() {
        _isDrawingRoute.value = false
        _drawTool.value = com.trailmap.gps.data.DrawTool.NONE
        _drawPoints.value = emptyList()
        _drawRedo.value = emptyList()
        _drawPins.value = emptyList()
        _showSaveDrawDialog.value = false
    }

    fun setDrawTool(tool: com.trailmap.gps.data.DrawTool) {
        if (_isDrawingRoute.value) _drawTool.value = tool
    }

    fun addDrawPoint(lat: Double, lon: Double) {
        if (!_isDrawingRoute.value) return
        val elevation = _currentLocation.value?.elevation ?: 0.0
        val point = TrackPoint(lat, lon, elevation)
        if (_drawTool.value == com.trailmap.gps.data.DrawTool.WAYPOINT) {
            _drawPins.value = _drawPins.value + point
        } else {
            _drawPoints.value = _drawPoints.value + point
            _drawRedo.value = emptyList()
        }
    }

    fun undoDraw() {
        val current = _drawPoints.value
        if (current.isEmpty()) return
        _drawRedo.value = _drawRedo.value + current.last()
        _drawPoints.value = current.dropLast(1)
    }

    fun redoDraw() {
        val redo = _drawRedo.value
        if (redo.isEmpty()) return
        _drawPoints.value = _drawPoints.value + redo.last()
        _drawRedo.value = redo.dropLast(1)
    }

    fun requestSaveDrawnRoute() {
        if (_drawPoints.value.size >= 2) {
            _showSaveDrawDialog.value = true
        }
    }

    fun dismissSaveDrawDialog() {
        _showSaveDrawDialog.value = false
    }

    suspend fun saveDrawnRoute(name: String): Long? {
        val points = _drawPoints.value
        if (points.size < 2) return null
        val stats = RouteParser.calculateStats(points)
        val waypoints = _drawPins.value.mapIndexed { i, p ->
            Waypoint("Point ${i + 1}", p.lat, p.lon, p.elevation, "Custom")
        }
        val parsed = ParsedRoute(
            name = name.ifBlank { "Drawn Route" },
            points = points,
            waypoints = waypoints,
            distanceMeters = stats.distanceMeters,
            elevationGainMeters = stats.elevationGainMeters,
            elevationLossMeters = stats.elevationLossMeters,
            maxElevationMeters = stats.maxElevationMeters,
            estimatedTimeSeconds = stats.estimatedTimeSeconds
        )
        val id = routeRepository.save(parsed, RouteSource.DRAWN)
        cancelDrawing()
        return id
    }

    fun updateDataBarProfile(value: com.trailmap.gps.data.DataBarProfile) {
        viewModelScope.launch { app.settingsRepository.setDataBarProfile(value) }
    }

    fun updateDataBarSlot(index: Int, metric: com.trailmap.gps.data.DataBarMetric) {
        viewModelScope.launch {
            val current = settings.value
            val slots = mutableListOf(current.dataBarSlot1, current.dataBarSlot2, current.dataBarSlot3)
            slots[index] = metric
            app.settingsRepository.setDataBarSlots(slots[0], slots[1], slots[2])
        }
    }

    fun updateAccent(value: com.trailmap.gps.data.AccentTheme) {
        viewModelScope.launch { app.settingsRepository.setAccentTheme(value) }
    }

    fun updateOverlayStrength(value: com.trailmap.gps.data.OverlayStrength) {
        viewModelScope.launch { app.settingsRepository.setOverlayStrength(value) }
    }

    fun updateLargeNumbers(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setLargeNumbers(enabled) }
    }

    fun updateLibraryFilter(value: com.trailmap.gps.data.RouteLibraryFilter) {
        viewModelScope.launch { app.settingsRepository.setLibraryFilter(value) }
    }

    fun updateMapChrome(value: com.trailmap.gps.data.MapChromeLayout) {
        viewModelScope.launch { app.settingsRepository.setMapChrome(value) }
    }

    fun toggleMapTool(tool: com.trailmap.gps.ui.maptools.MapTool) {
        val next = com.trailmap.gps.ui.maptools.MapToolSession.next(_activeMapTool.value, tool)
        if (_activeMapTool.value == com.trailmap.gps.ui.maptools.MapTool.ROUTE && next != com.trailmap.gps.ui.maptools.MapTool.ROUTE) {
            cancelDrawing()
        }
        if (next == com.trailmap.gps.ui.maptools.MapTool.ROUTE && !_isDrawingRoute.value) {
            startDrawing()
        }
        _activeMapTool.value = next
    }

    fun dismissMapTool() {
        if (_activeMapTool.value == com.trailmap.gps.ui.maptools.MapTool.ROUTE) cancelDrawing()
        _activeMapTool.value = null
    }
}

data class NavigationState(
    val distanceToNext: Double = 0.0,
    val bearing: Double = 0.0,
    val course: Double = 0.0,
    val elevation: Double = 0.0,
    val remainingDistance: Double = 0.0,
    val remainingAscent: Double = 0.0,
    val isOffRoute: Boolean = false,
    val gpsDegraded: Boolean = false,
    val statusMessage: String? = null,
    val progress: Float = 0f,
    val nearestIndex: Int = 0,
    val distanceAlongRoute: Double = 0.0,
    val totalDistance: Double = 0.0,
    val nextLabel: String = "ROUTE AHEAD",
    val nextType: String = ""
)
