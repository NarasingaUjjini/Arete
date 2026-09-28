package com.trailmap.gps.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import android.view.WindowManager
import com.trailmap.gps.data.RouteEntity
import com.trailmap.gps.ui.screens.CompassScreen
import com.trailmap.gps.ui.screens.ConditionsScreen
import com.trailmap.gps.ui.screens.RecInfoScreen
import com.trailmap.gps.ui.screens.EmergencyScreen
import com.trailmap.gps.ui.screens.GpsDiagnosticsScreen
import com.trailmap.gps.ui.screens.ObjectiveScreen
import com.trailmap.gps.ui.screens.Terrain3DScreen
import com.trailmap.gps.ui.screens.ImportPickerScreen
import com.trailmap.gps.ui.screens.ImportPreviewScreen
import com.trailmap.gps.ui.screens.LayerPickerSheet
import com.trailmap.gps.ui.screens.MapHomeScreen
import com.trailmap.gps.ui.screens.NavigationScreen
import com.trailmap.gps.ui.screens.TripPackScreen
import com.trailmap.gps.ui.screens.RecordingScreen
import com.trailmap.gps.ui.screens.RouteDetailScreen
import com.trailmap.gps.ui.screens.RoutesListScreen
import com.trailmap.gps.ui.screens.OfflineDownloadScreen
import com.trailmap.gps.ui.screens.SettingsScreen
import com.trailmap.gps.ui.screens.BottomTab
import com.trailmap.gps.location.TrackingService
import com.trailmap.gps.ui.theme.Black
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AppScreen {
    data object Map : AppScreen()
    data object ImportPicker : AppScreen()
    data object Routes : AppScreen()
    data object Settings : AppScreen()
    data object Recording : AppScreen()
    data class ImportPreview(val fileName: String) : AppScreen()
    data class Navigation(val routeId: Long) : AppScreen()
    data class OfflineDownload(val routeId: Long) : AppScreen()
    data object MapAreaDownload : AppScreen()
    data class RouteDetail(val routeId: Long) : AppScreen()
    data object GpsDiagnostics : AppScreen()
    data object Terrain3d : AppScreen()
    data object Conditions : AppScreen()
    data object RecInfo : AppScreen()
    data object Emergency : AppScreen()
    data object Compass : AppScreen()
    data object Objective : AppScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailMapAppContent(
    viewModel: MainViewModel = viewModel(),
    onBrowseFiles: () -> Unit,
    pendingImportStream: () -> Pair<java.io.InputStream, String>?
) {
    val settings by viewModel.settings.collectAsState()
    val routes by viewModel.filteredRoutes.collectAsState()
    val importPreview by viewModel.importPreview.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingPhase by viewModel.recordingPhase.collectAsState()
    val recordedPoints by viewModel.recordedPoints.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val isNavigating by viewModel.isNavigating.collectAsState()
    val batterySaver by viewModel.batterySaver.collectAsState()
    val offlineState by viewModel.offlineState.collectAsState()
    val gnssRefreshing by viewModel.gnssRefreshing.collectAsState()
    val gnssMessage by viewModel.gnssMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val drawPoints by viewModel.drawPoints.collectAsState()
    val isDrawingRoute by viewModel.isDrawingRoute.collectAsState()
    val drawTool by viewModel.drawTool.collectAsState()
    val drawRedo by viewModel.drawRedo.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val recenterTrigger by viewModel.recenterTrigger.collectAsState()
    val fitRouteTrigger by viewModel.fitRouteTrigger.collectAsState()
    val mapBearing by viewModel.mapBearing.collectAsState()
    val showSaveDrawDialog by viewModel.showSaveDrawDialog.collectAsState()
    val inspection by viewModel.inspection.collectAsState()
    val terrainOverlayMode by viewModel.terrainOverlay.collectAsState()
    val demDownload by viewModel.demDownload.collectAsState()
    val verticalMph by viewModel.verticalMetersPerHour.collectAsState()
    val profileDistance by viewModel.profileDistance.collectAsState()
    val sectionStart by viewModel.sectionStart.collectAsState()
    val sectionEnd by viewModel.sectionEnd.collectAsState()
    val allTripPacks by viewModel.allTripPacks.collectAsState()
    val tripPackProgress by viewModel.tripPackProgress.collectAsState()
    val packChecks by viewModel.packChecks.collectAsState()
    val forcedOffline by viewModel.forcedOffline.collectAsState()
    val conditions by viewModel.conditions.collectAsState()
    val conditionsLoading by viewModel.conditionsLoading.collectAsState()
    val recInfo by viewModel.recInfo.collectAsState()
    val recInfoLoading by viewModel.recInfoLoading.collectAsState()
    val magneticNorth by viewModel.magneticNorth.collectAsState()
    val deviceHeading by viewModel.deviceHeading.collectAsState()
    val position by viewModel.position.collectAsState()
    val activeMapTool by viewModel.activeMapTool.collectAsState()
    val returnByMinutes by viewModel.returnByMinutes.collectAsState()
    val overlayPair = remember(terrainOverlayMode, demDownload.lastPack?.id, selectedRoute?.id) {
        viewModel.overlayBitmap()
    }
    val contourProvider = remember(settings.contoursEnabled, demDownload.lastPack?.id, selectedRoute?.id) {
        { maxDim: Int -> viewModel.contourGeoJson(maxDim) }
    }
    val contourGeoJson = remember(contourProvider) {
        contourProvider(com.trailmap.gps.terrain.ContourGenerator.DEFAULT_MAX_DIM)
    }

    val orientationTrigger by viewModel.orientationTrigger.collectAsState()

    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Map) }
    var diagnosticsReturn by remember { mutableStateOf<AppScreen>(AppScreen.Map) }
    var downloadReturn by remember { mutableStateOf<AppScreen>(AppScreen.Map) }
    var showLayerPicker by remember { mutableStateOf(false) }
    var navRoute by remember { mutableStateOf<RouteEntity?>(null) }
    var navStartTime by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pendingImportStream) {
        pendingImportStream()?.let { (stream, name) ->
            viewModel.parseImport(stream, name)
            currentScreen = AppScreen.ImportPreview(name)
        }
    }

    val view = LocalView.current
    LaunchedEffect(activeMapTool, currentScreen, settings.northUp) {
        val compassOpen = currentScreen is AppScreen.Compass ||
            activeMapTool == com.trailmap.gps.ui.maptools.MapTool.COMPASS
        viewModel.setHeadingWanted(compassOpen || !settings.northUp)
    }
    LaunchedEffect(isNavigating, isRecording, settings.keepScreenOn) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            if (settings.keepScreenOn && (isNavigating || isRecording)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(isNavigating, navStartTime) {
        if (isNavigating && navStartTime > 0) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - navStartTime) / 1000
                delay(1000)
            }
        }
    }

    val displayRoutePoints = remember(selectedRoute, navRoute, importPreview) {
        when {
            navRoute != null -> viewModel.getRoutePoints(navRoute!!)
            selectedRoute != null -> viewModel.getRoutePoints(selectedRoute!!)
            importPreview != null -> importPreview!!.points
            else -> emptyList()
        }
    }

    com.trailmap.gps.ui.theme.TrailMapTheme(accentHex = settings.accentHex) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val screen = currentScreen) {
            AppScreen.Map -> {
                MapHomeScreen(
                    settings = settings,
                    currentLocation = currentLocation,
                    routePoints = displayRoutePoints,
                    drawPoints = drawPoints,
                    trackPoints = if (isRecording) recordedPoints else breadcrumbs,
                    isRecording = isRecording,
                    isDrawingRoute = isDrawingRoute,
                    isFullscreen = isFullscreen,
                    mapBearing = mapBearing,
                    recenterTrigger = recenterTrigger,
                    fitRouteTrigger = fitRouteTrigger,
                    orientationTrigger = orientationTrigger,
                    onImportClick = { currentScreen = AppScreen.ImportPicker },
                    onOpenConditions = { currentScreen = AppScreen.Conditions },
                    onOpenRecInfo = {
                        viewModel.refreshRecInfo()
                        currentScreen = AppScreen.RecInfo
                    },
                    onDownloadArea = {
                        downloadReturn = AppScreen.Map
                        currentScreen = AppScreen.MapAreaDownload
                    },
                    terrainOverlayRequested = terrainOverlayMode != com.trailmap.gps.terrain.TerrainOverlay.NONE,
                    hasLocalTerrainOverlay = overlayPair != null,
                    onTabSelected = { tab ->
                        when (tab) {
                            BottomTab.ROUTES -> currentScreen = AppScreen.Routes
                            BottomTab.RECORD -> currentScreen = AppScreen.Recording
                            BottomTab.LAYERS -> showLayerPicker = true
                            BottomTab.SETTINGS -> currentScreen = AppScreen.Settings
                        }
                    },
                    onRecenter = viewModel::recenter,
                    onToggleNorthUp = viewModel::toggleNorthUp,
                    onAlignNorth = viewModel::alignNorth,
                    onToggleFullscreen = viewModel::toggleFullscreen,
                    onToggleDrawRoute = viewModel::toggleDrawRoute,
                    onSaveDrawnRoute = viewModel::requestSaveDrawnRoute,
                    contourGeoJson = contourGeoJson,
                    contourProvider = contourProvider,
                    dataBarInputs = com.trailmap.gps.ui.components.DataBarInputs(
                        location = currentLocation,
                        locationState = locationState,
                        traveledMeters = if (isRecording) {
                            com.trailmap.gps.geo.RouteGeometry.totalDistance(recordedPoints)
                        } else 0.0,
                        verticalMetersPerHour = verticalMph,
                        temperatureF = conditions?.weather?.firstOrNull()?.periods?.firstOrNull()?.temperatureF,
                        wind = conditions?.weather?.firstOrNull()?.periods?.firstOrNull()?.wind
                    ),
                    drawTool = drawTool,
                    canUndo = drawPoints.isNotEmpty(),
                    canRedo = drawRedo.isNotEmpty(),
                    onUndoDraw = viewModel::undoDraw,
                    onRedoDraw = viewModel::redoDraw,
                    onCancelDraw = viewModel::cancelDrawing,
                    onFinishDraw = viewModel::requestSaveDrawnRoute,
                    onDrawToolChange = viewModel::setDrawTool,
                    routeColorHex = settings.accentHex,
                    overlayOpacity = settings.overlayStrength.opacity,
                    onMapClick = viewModel::addDrawPoint,
                    onBearingChanged = viewModel::setMapBearing,
                    locationState = locationState,
                    onGpsClick = {
                        diagnosticsReturn = AppScreen.Map
                        currentScreen = AppScreen.GpsDiagnostics
                    },
                    inspection = inspection,
                    inspectPoint = inspection?.let { it.lat to it.lon },
                    terrainOverlay = overlayPair?.first,
                    terrainOverlayBounds = overlayPair?.second,
                    onInspect = viewModel::inspectTerrain,
                    onClearInspection = viewModel::clearInspection,
                    activeTool = activeMapTool,
                    onToggleTool = viewModel::toggleMapTool,
                    onDismissTool = viewModel::dismissMapTool,
                    navState = selectedRoute?.let { viewModel.navigationState(it, currentLocation) }
                        ?: com.trailmap.gps.ui.NavigationState(),
                    summitBearing = selectedRoute?.let { route ->
                        val summit = viewModel.summitWaypoint(route)
                        val loc = currentLocation
                        if (summit != null && loc != null) {
                            com.trailmap.gps.geo.GeoMath.initialBearingDegrees(
                                loc.latitude, loc.longitude, summit.lat, summit.lon
                            )
                        } else null
                    },
                    magneticNorth = magneticNorth,
                    deviceHeadingMagnetic = deviceHeading.magneticDegrees,
                    deviceHeadingReady = deviceHeading.ready,
                    onToggleMagneticNorth = viewModel::toggleMagneticNorth,
                    onMapChromeChange = viewModel::updateMapChrome,
                    terrainOverlayMode = terrainOverlayMode,
                    onTerrainOverlayChange = viewModel::setTerrainOverlay,
                    hasLocalDem = viewModel.hasLocalDem()
                )
            }
            AppScreen.ImportPicker -> {
                ImportPickerScreen(
                    onBrowseFiles = onBrowseFiles,
                    onBack = { currentScreen = AppScreen.Map }
                )
            }
            is AppScreen.ImportPreview -> {
                importPreview?.let { preview ->
                    ImportPreviewScreen(
                        preview = preview,
                        settings = settings,
                        onBack = {
                            viewModel.clearImportPreview()
                            currentScreen = AppScreen.Map
                        },
                        onSave = {
                            scope.launch {
                                val id = viewModel.saveImport()
                                id?.let {
                                    viewModel.selectRoute(it)
                                    viewModel.fitRouteOnMap()
                                }
                                currentScreen = AppScreen.Map
                            }
                        },
                        onSaveAndDownload = {
                            scope.launch {
                                val id = viewModel.saveImport()
                                id?.let {
                                    viewModel.selectRoute(it)
                                    currentScreen = AppScreen.OfflineDownload(it)
                                } ?: run { currentScreen = AppScreen.Map }
                            }
                        }
                    )
                } ?: run { currentScreen = AppScreen.Map }
            }
            AppScreen.Routes -> {
                RoutesListScreen(
                    routes = routes,
                    settings = settings,
                    searchQuery = searchQuery,
                    onSearchChange = viewModel::setSearchQuery,
                    onRouteClick = { id ->
                        viewModel.selectRoute(id)
                        currentScreen = AppScreen.RouteDetail(id)
                    },
                    onImportClick = { currentScreen = AppScreen.ImportPicker },
                    onBack = { currentScreen = AppScreen.Map },
                    packStatusByRoute = allTripPacks.associate { it.routeId to it.packStatus() },
                    demReadyIds = allTripPacks.filter { it.includeDem }.map { it.routeId }.toSet(),
                    onFilterChange = viewModel::updateLibraryFilter
                )
            }
            AppScreen.Settings -> {
                SettingsScreen(
                    settings = settings,
                    gnssRefreshing = gnssRefreshing,
                    gnssMessage = gnssMessage,
                    onBack = { currentScreen = AppScreen.Map },
                    onDistanceUnitChange = viewModel::updateDistanceUnit,
                    onElevationUnitChange = viewModel::updateElevationUnit,
                    onCoordinateFormatChange = viewModel::updateCoordinateFormat,
                    onPowerProfileChange = viewModel::updatePowerProfile,
                    onKeepScreenOnChange = viewModel::updateKeepScreenOn,
                    onOffRouteCorridorChange = viewModel::updateOffRouteCorridor,
                    onDownloadMapArea = {
                        downloadReturn = AppScreen.Settings
                        currentScreen = AppScreen.MapAreaDownload
                    },
                    onOpenGpsDiagnostics = {
                        diagnosticsReturn = AppScreen.Settings
                        currentScreen = AppScreen.GpsDiagnostics
                    },
                    onOpenConditions = {
                        viewModel.refreshConditions()
                        currentScreen = AppScreen.Conditions
                    },
                    onDataBarProfileChange = viewModel::updateDataBarProfile,
                    onDataBarSlotChange = viewModel::updateDataBarSlot,
                    onAccentChange = viewModel::updateAccent,
                    onOverlayStrengthChange = viewModel::updateOverlayStrength,
                    onLargeNumbersChange = viewModel::updateLargeNumbers,
                    onMapChromeChange = viewModel::updateMapChrome,
                    locationSummary = buildString {
                        append("GPS ")
                        append(locationState.quality.name.lowercase())
                        if (locationState.usable) {
                            append(" · ±${locationState.horizontalAccuracy.toInt()}m")
                            if (locationState.ageMs > 0) append(" · ${locationState.ageMs / 1000}s ago")
                            if (locationState.satellitesUsed > 0) {
                                append(" · ${locationState.satellitesUsed}/${locationState.satellitesVisible} sats")
                            }
                        } else {
                            append(" · no usable fix")
                        }
                    }
                )
            }
            AppScreen.Recording -> {
                RecordingScreen(
                    phase = recordingPhase,
                    points = recordedPoints,
                    location = currentLocation,
                    settings = settings,
                    elapsedProvider = viewModel::recordingElapsedSeconds,
                    onStart = viewModel::startRecording,
                    onPause = viewModel::pauseRecording,
                    onResume = viewModel::resumeRecording,
                    onStop = viewModel::stopRecording,
                    onSave = { name ->
                        scope.launch {
                            viewModel.saveRecordedTrack(name)
                            currentScreen = AppScreen.Map
                        }
                    },
                    onDiscard = {
                        viewModel.discardRecording()
                        currentScreen = AppScreen.Map
                    },
                    onBack = { currentScreen = AppScreen.Map },
                    contourGeoJson = contourGeoJson
                )
            }
            is AppScreen.Navigation -> {
                navRoute?.let { route ->
                    val points = viewModel.getRoutePoints(route)
                    val navState = viewModel.navigationState(route, currentLocation)
                    NavigationScreen(
                        route = route,
                        routePoints = points,
                        settings = settings,
                        location = currentLocation,
                        locationState = locationState,
                        navState = navState,
                        breadcrumbs = breadcrumbs,
                        batterySaver = batterySaver,
                        elapsedSeconds = elapsedSeconds,
                        onExit = {
                            viewModel.stopNavigation()
                            navRoute = null
                            currentScreen = AppScreen.Map
                        },
                        onToggleBatterySaver = viewModel::toggleBatterySaver,
                        onGpsClick = {
                            diagnosticsReturn = screen
                            currentScreen = AppScreen.GpsDiagnostics
                        },
                        verticalMetersPerHour = verticalMph,
                        contourGeoJson = contourGeoJson,
                        onOpenEmergency = { currentScreen = AppScreen.Emergency },
                        onOpenCompass = { currentScreen = AppScreen.Compass },
                        turnaroundLabel = viewModel.turnaroundPlan(route, currentLocation)?.latestTurnaroundMs?.let {
                            "TURNAROUND EST ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(it))}"
                        }
                    )
                }
            }
            AppScreen.MapAreaDownload -> {
                OfflineDownloadScreen(
                    route = selectedRoute,
                    points = selectedRoute?.let { viewModel.getRoutePoints(it) }.orEmpty(),
                    settings = settings,
                    downloadState = offlineState,
                    layerLabels = viewModel.offlineLayerLabels(),
                    gnssRefreshing = gnssRefreshing,
                    gnssMessage = gnssMessage,
                    onBack = { currentScreen = downloadReturn },
                    onDownload = { bounds, zoom, includeDem ->
                        viewModel.downloadArea(bounds, zoom, includeDem, selectedRoute?.id)
                    },
                    onCancelDownload = viewModel::cancelAreaDownload,
                    estimateTiles = { bounds, zoom -> viewModel.estimateOfflineTiles(bounds, zoom) },
                    demProgress = demDownload.progress,
                    demError = demDownload.error,
                    demDownloading = demDownload.isDownloading,
                    currentLocation = currentLocation
                )
            }
            is AppScreen.OfflineDownload -> {
                selectedRoute?.let { route ->
                    val pack = allTripPacks.firstOrNull { it.routeId == route.id }
                    LaunchedEffect(pack?.id, pack?.manifestJson) {
                        viewModel.loadPackChecks(pack)
                    }
                    TripPackScreen(
                        route = route,
                        points = viewModel.getRoutePoints(route),
                        settings = settings,
                        pack = pack,
                        checks = packChecks,
                        progress = tripPackProgress,
                        storageBytes = viewModel.storageBytes(),
                        forcedOffline = forcedOffline,
                        estimateTiles = { corridor, mode, topo, shade, zoom ->
                            viewModel.estimatePackTiles(viewModel.getRoutePoints(route), corridor, mode, topo, shade, zoom)
                        },
                        onBack = { currentScreen = AppScreen.RouteDetail(route.id) },
                        onPrepareAndStart = { corridor, mode, topo, dem, shade, imagery, weather, zoom ->
                            viewModel.startTripPack(route, corridor, mode, topo, dem, shade, imagery, weather, zoom)
                        },
                        onPause = viewModel::pauseTripPack,
                        onResume = { pack?.id?.let { viewModel.resumeTripPack(it) } },
                        onCancel = viewModel::cancelTripPack,
                        onVerify = { pack?.id?.let { viewModel.verifyTripPack(it) } },
                        onDelete = { pack?.id?.let { viewModel.deleteTripPack(it) } },
                        onToggleAirplaneTest = viewModel::setForcedOffline
                    )
                }
            }
            is AppScreen.RouteDetail -> {
                selectedRoute?.let { route ->
                    RouteDetailScreen(
                        route = route,
                        points = viewModel.getRoutePoints(route),
                        waypoints = viewModel.getRouteWaypoints(route),
                        settings = settings,
                        onBack = { currentScreen = AppScreen.Routes },
                        onNavigate = {
                            navRoute = route
                            viewModel.startNavigation()
                            navStartTime = System.currentTimeMillis()
                            currentScreen = AppScreen.Navigation(route.id)
                        },
                        onDownload = {
                            currentScreen = AppScreen.OfflineDownload(route.id)
                        },
                        onExport = { viewModel.exportRoute(view.context, route) },
                        onDelete = { pendingDeleteId = route.id },
                        profileDistanceMeters = profileDistance,
                        sectionStartMeters = sectionStart,
                        sectionEndMeters = sectionEnd,
                        demPoints = viewModel.demProfile(viewModel.getRoutePoints(route)),
                        hasLocalDem = viewModel.hasLocalDem(),
                        demDownloading = demDownload.isDownloading,
                        onScrubProfile = { dist, _ -> viewModel.setProfileDistance(dist) },
                        onMarkSectionStart = viewModel::markSectionStart,
                        onMarkSectionEnd = viewModel::markSectionEnd,
                        onClearSection = viewModel::clearSection,
                        onDownloadTerrain = viewModel::downloadTerrainForSelected,
                        onOpen3d = { currentScreen = AppScreen.Terrain3d },
                        onOpenConditions = {
                            viewModel.refreshConditions()
                            currentScreen = AppScreen.Conditions
                        },
                        onOpenObjective = { currentScreen = AppScreen.Objective },
                        contourGeoJson = contourGeoJson,
                        packStatus = allTripPacks.firstOrNull { it.routeId == route.id }?.packStatus(),
                        packBytes = allTripPacks.firstOrNull { it.routeId == route.id }?.actualBytes ?: 0
                    )
                } ?: run { currentScreen = AppScreen.Routes }
            }
            AppScreen.GpsDiagnostics -> {
                GpsDiagnosticsScreen(
                    state = locationState,
                    settings = settings,
                    onBack = { currentScreen = diagnosticsReturn },
                    positionSummary = if (position.hasPosition) {
                        "${position.integrity.name} · ±${position.horizontalUncertaintyM.toInt()} m · ${position.reason}"
                    } else {
                        position.reason
                    }
                )
            }
            AppScreen.Terrain3d -> {
                val grid = viewModel.activeDemGrid()
                LaunchedEffect(grid) {
                    if (grid == null) {
                        currentScreen = selectedRoute?.let { AppScreen.RouteDetail(it.id) } ?: AppScreen.Map
                    }
                }
                if (grid != null) {
                    Terrain3DScreen(
                        grid = grid,
                        routePoints = selectedRoute?.let { viewModel.getRoutePoints(it) }.orEmpty(),
                        waypoints = selectedRoute?.let { viewModel.getRouteWaypoints(it) }.orEmpty(),
                        onBack = { currentScreen = selectedRoute?.let { AppScreen.RouteDetail(it.id) } ?: AppScreen.Map }
                    )
                }
            }
            AppScreen.Conditions -> {
                ConditionsScreen(
                    conditions = conditions,
                    loading = conditionsLoading,
                    onBack = {
                        currentScreen = selectedRoute?.let { AppScreen.RouteDetail(it.id) } ?: AppScreen.Settings
                    },
                    onRefresh = viewModel::refreshConditions
                )
            }
            AppScreen.RecInfo -> {
                RecInfoScreen(
                    snapshot = recInfo,
                    loading = recInfoLoading,
                    hasFix = currentLocation != null,
                    onBack = { currentScreen = AppScreen.Map },
                    onRefresh = viewModel::refreshRecInfo
                )
            }
            AppScreen.Emergency -> {
                val route = selectedRoute ?: navRoute
                EmergencyScreen(
                    location = currentLocation,
                    locationState = locationState,
                    settings = settings,
                    backtrackMeters = route?.let { viewModel.backtrackMeters(it) } ?: 0.0,
                    remainingToTrailheadMeters = route?.let { viewModel.remainingToTrailhead(it, currentLocation) } ?: 0.0,
                    position = position,
                    onBack = {
                        currentScreen = if (isNavigating && navRoute != null) {
                            AppScreen.Navigation(navRoute!!.id)
                        } else {
                            selectedRoute?.let { AppScreen.RouteDetail(it.id) } ?: AppScreen.Map
                        }
                    }
                )
            }
            AppScreen.Compass -> {
                val route = selectedRoute ?: navRoute
                val navState = route?.let { viewModel.navigationState(it, currentLocation) } ?: com.trailmap.gps.ui.NavigationState()
                CompassScreen(
                    location = currentLocation,
                    navState = navState,
                    summitBearing = route?.let { viewModel.summitBearing(it, currentLocation) },
                    magneticNorth = magneticNorth,
                    headingMagnetic = deviceHeading.magneticDegrees,
                    headingReady = deviceHeading.ready,
                    onToggleNorth = viewModel::toggleMagneticNorth,
                    onBack = {
                        currentScreen = if (isNavigating && navRoute != null) {
                            AppScreen.Navigation(navRoute!!.id)
                        } else {
                            selectedRoute?.let { AppScreen.Objective } ?: AppScreen.Map
                        }
                    }
                )
            }
            AppScreen.Objective -> {
                selectedRoute?.let { route ->
                    ObjectiveScreen(
                        routeName = route.name,
                        settings = settings,
                        segments = viewModel.routeSegments(route),
                        checkpoints = viewModel.getRouteWaypoints(route),
                        summit = viewModel.summitWaypoint(route),
                        bailouts = viewModel.bailoutWaypoints(route),
                        turnaround = viewModel.turnaroundPlan(route, currentLocation),
                        returnByLabel = remember(returnByMinutes) { viewModel.returnByLabel() },
                        onCycleReturnBy = viewModel::cycleReturnBy,
                        onOpenCompass = { currentScreen = AppScreen.Compass },
                        onOpenEmergency = { currentScreen = AppScreen.Emergency },
                        onBack = { currentScreen = AppScreen.RouteDetail(route.id) }
                    )
                } ?: run { currentScreen = AppScreen.Map }
            }
        }

        if (showLayerPicker) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showLayerPicker = false },
                sheetState = sheetState,
                containerColor = Black
            ) {
                LayerPickerSheet(
                    settings = settings,
                    onLayerChange = { layer ->
                        viewModel.updateMapLayer(layer)
                    },
                    onHillshadeChange = viewModel::updateHillshade,
                    onContoursChange = viewModel::updateContours,
                    terrainOverlay = terrainOverlayMode,
                    onTerrainOverlayChange = viewModel::setTerrainOverlay,
                    onDismiss = { showLayerPicker = false }
                )
            }
        }

        if (importError != null) {
            AlertDialog(
                onDismissRequest = viewModel::clearImportError,
                title = { Text("Import failed") },
                text = { Text(importError ?: "") },
                confirmButton = {
                    TextButton(onClick = viewModel::clearImportError) { Text("OK") }
                }
            )
        }

        pendingDeleteId?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text("CONFIRM ROUTE DELETION") },
                text = { Text("This removes the route and its waypoints from the library. Shared map tiles stay on the phone unless you delete the Trip Pack separately.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteId = null
                        scope.launch {
                            viewModel.deleteRoute(id)
                            currentScreen = AppScreen.Routes
                        }
                    }) { Text("DELETE ROUTE") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) { Text("CANCEL / KEEP ROUTE") }
                }
            )
        }

        if (showSaveDrawDialog) {
            com.trailmap.gps.ui.components.SaveRouteNameDialog(
                defaultName = "Drawn Route",
                onDismiss = viewModel::dismissSaveDrawDialog,
                onSave = { name ->
                    scope.launch {
                        val id = viewModel.saveDrawnRoute(name)
                        id?.let {
                            viewModel.selectRoute(it)
                            viewModel.fitRouteOnMap()
                        }
                    }
                }
            )
        }
    }
    }
}
