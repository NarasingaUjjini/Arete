package com.trailmap.gps.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.location.CurrentLocationState
import com.trailmap.gps.location.GpsUpdate
import com.trailmap.gps.map.TrailMapView
import com.trailmap.gps.terrain.ContourGenerator
import com.trailmap.gps.terrain.TerrainInspection
import com.trailmap.gps.terrain.TerrainOverlay
import com.trailmap.gps.ui.NavigationState
import com.trailmap.gps.ui.components.CompassRoseButton
import com.trailmap.gps.ui.components.DataBarInputs
import com.trailmap.gps.ui.components.RecenterButton
import com.trailmap.gps.ui.maptools.CompassInstrument
import com.trailmap.gps.ui.maptools.DestinationMenu
import com.trailmap.gps.ui.maptools.ElevationInstrument
import com.trailmap.gps.ui.maptools.LocationInstrument
import com.trailmap.gps.ui.maptools.MapChrome
import com.trailmap.gps.ui.maptools.MapDestination
import com.trailmap.gps.ui.maptools.MapFieldSnapshot
import com.trailmap.gps.ui.maptools.MapInspectorHost
import com.trailmap.gps.ui.maptools.MapTool
import com.trailmap.gps.ui.maptools.RouteInstrument
import com.trailmap.gps.ui.maptools.TerrainInstrument
import com.trailmap.gps.ui.maptools.declinationAt
import com.trailmap.gps.ui.theme.MarginEdge

enum class BottomTab {
    ROUTES, RECORD, LAYERS, SETTINGS
}

@Composable
fun MapHomeScreen(
    settings: AppSettings,
    currentLocation: GpsUpdate?,
    routePoints: List<TrackPoint>,
    drawPoints: List<TrackPoint>,
    trackPoints: List<TrackPoint> = emptyList(),
    isRecording: Boolean,
    isDrawingRoute: Boolean,
    isFullscreen: Boolean,
    mapBearing: Double,
    recenterTrigger: Int,
    fitRouteTrigger: Int,
    orientationTrigger: Int,
    onImportClick: () -> Unit,
    onOpenConditions: () -> Unit = {},
    onOpenRecInfo: () -> Unit = {},
    onDownloadArea: () -> Unit = {},
    onTabSelected: (BottomTab) -> Unit,
    onRecenter: () -> Unit,
    onToggleNorthUp: () -> Unit,
    onAlignNorth: () -> Unit = {},
    onToggleFullscreen: () -> Unit,
    onToggleDrawRoute: () -> Unit,
    onSaveDrawnRoute: () -> Unit,
    onMapClick: (Double, Double) -> Unit,
    onBearingChanged: (Double) -> Unit,
    locationState: CurrentLocationState,
    onGpsClick: () -> Unit,
    inspection: TerrainInspection? = null,
    inspectPoint: Pair<Double, Double>? = null,
    terrainOverlay: Bitmap? = null,
    terrainOverlayBounds: BoundingBox? = null,
    onInspect: (Double, Double) -> Unit = { _, _ -> },
    onClearInspection: () -> Unit = {},
    selectedTab: BottomTab? = null,
    contourGeoJson: String? = null,
    contourProvider: ((Int) -> String?)? = null,
    dataBarInputs: DataBarInputs = DataBarInputs(),
    drawTool: com.trailmap.gps.data.DrawTool = com.trailmap.gps.data.DrawTool.NONE,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndoDraw: () -> Unit = {},
    onRedoDraw: () -> Unit = {},
    onCancelDraw: () -> Unit = {},
    onFinishDraw: () -> Unit = {},
    onDrawToolChange: (com.trailmap.gps.data.DrawTool) -> Unit = {},
    routeColorHex: String = "#FF6B00",
    overlayOpacity: Float = 0.48f,
    terrainOverlayRequested: Boolean = false,
    hasLocalTerrainOverlay: Boolean = false,
    activeTool: MapTool? = null,
    onToggleTool: (MapTool) -> Unit = {},
    onDismissTool: () -> Unit = {},
    navState: NavigationState = NavigationState(),
    summitBearing: Double? = null,
    magneticNorth: Boolean = false,
    onToggleMagneticNorth: () -> Unit = {},
    terrainOverlayMode: TerrainOverlay = TerrainOverlay.NONE,
    onTerrainOverlayChange: (TerrainOverlay) -> Unit = {},
    hasLocalDem: Boolean = false,
    deviceHeadingMagnetic: Double = 0.0,
    deviceHeadingReady: Boolean = false,
    onMapChromeChange: (com.trailmap.gps.data.MapChromeLayout) -> Unit = {}
) {
    var mapZoom by remember { mutableDoubleStateOf(12.0) }
    var mapLat by remember { mutableDoubleStateOf(currentLocation?.latitude ?: 37.0) }
    var moreOpen by remember { mutableStateOf(false) }
    val contourMaxDim = ContourGenerator.maxDimForZoom(mapZoom)
    val liveContourGeoJson = remember(contourProvider, contourGeoJson, contourMaxDim) {
        contourProvider?.invoke(contourMaxDim) ?: contourGeoJson
    }
    val field = remember(
        settings, dataBarInputs, currentLocation, locationState, navState,
        summitBearing, magneticNorth, routePoints, hasLocalDem, mapZoom, mapLat,
        deviceHeadingMagnetic, deviceHeadingReady
    ) {
        MapFieldSnapshot.from(
            settings = settings,
            inputs = dataBarInputs,
            location = currentLocation,
            locationState = locationState,
            nav = navState,
            summitBearing = summitBearing,
            declination = declinationAt(currentLocation),
            magneticNorth = magneticNorth,
            hasRoute = routePoints.size >= 2,
            hasLocalDem = hasLocalDem,
            mapLatitude = mapLat,
            mapZoom = mapZoom,
            headingMagnetic = deviceHeadingMagnetic,
            headingReady = deviceHeadingReady
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TrailMapView(
            modifier = Modifier.fillMaxSize(),
            mapLayer = settings.mapLayer,
            hillshade = settings.hillshadeEnabled,
            contourGeoJson = liveContourGeoJson,
            routeColorHex = routeColorHex,
            overlayOpacity = overlayOpacity,
            routePoints = routePoints,
            drawPoints = drawPoints,
            trackPoints = trackPoints,
            currentLocation = currentLocation,
            northUp = settings.northUp,
            followUser = false,
            recenterTrigger = recenterTrigger,
            fitRouteTrigger = fitRouteTrigger,
            orientationTrigger = orientationTrigger,
            onMapClick = if (isDrawingRoute) onMapClick else { lat, lon -> onInspect(lat, lon) },
            onMapLongClick = onInspect,
            inspectPoint = inspectPoint,
            terrainOverlay = terrainOverlay,
            terrainOverlayBounds = terrainOverlayBounds,
            autoFitRoute = !isDrawingRoute,
            onBearingChanged = onBearingChanged,
            onScaleChanged = { zoom, lat ->
                mapZoom = zoom
                mapLat = lat
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 56.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompassRoseButton(mapBearing = mapBearing, onClick = onAlignNorth)
            RecenterButton(onClick = onRecenter)
        }

        if (!isFullscreen) {
            MapChrome(
                layout = settings.mapChrome,
                field = field,
                activeTool = activeTool,
                isRecording = isRecording,
                onTool = { tool ->
                    moreOpen = false
                    if (tool == MapTool.LAYERS) {
                        onTabSelected(BottomTab.LAYERS)
                        onDismissTool()
                    } else {
                        onToggleTool(tool)
                    }
                },
                onMore = { moreOpen = !moreOpen },
                onRecInfo = onOpenRecInfo
            )
        }

        when (activeTool) {
            MapTool.COMPASS -> CompassInstrument(
                snapshot = field.compass,
                onDismiss = onDismissTool,
                onToggleNorth = onToggleMagneticNorth,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
            )
            MapTool.LOCATION -> LocationInstrument(
                field = field,
                onRecenter = onRecenter,
                onToggleNorthUp = onToggleNorthUp,
                northUp = settings.northUp,
                onOpenDiagnostics = onGpsClick,
                onDismiss = onDismissTool,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = MarginEdge, vertical = 56.dp)
            )
            MapTool.TERRAIN -> TerrainInstrument(
                overlay = terrainOverlayMode,
                hasLocalDem = hasLocalDem,
                onOverlayChange = onTerrainOverlayChange,
                onDismiss = onDismissTool,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = MarginEdge, vertical = 56.dp)
            )
            MapTool.ELEVATION -> ElevationInstrument(
                points = routePoints,
                onDismiss = onDismissTool,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = MarginEdge, vertical = 16.dp)
            )
            else -> {}
        }

        if (isDrawingRoute && !isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                RouteInstrument(
                    drawTool = drawTool,
                    vertexCount = drawPoints.size,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = onUndoDraw,
                    onRedo = onRedoDraw,
                    onToolChange = onDrawToolChange,
                    onCancel = {
                        onCancelDraw()
                        onDismissTool()
                    },
                    onFinish = onFinishDraw
                )
            }
        }

        if (moreOpen && !isFullscreen) {
            DestinationMenu(
                isRecording = isRecording,
                isFullscreen = isFullscreen,
                mapChrome = settings.mapChrome,
                onMapChrome = onMapChromeChange,
                onSelect = { dest ->
                    moreOpen = false
                    when (dest) {
                        MapDestination.ROUTES -> onTabSelected(BottomTab.ROUTES)
                        MapDestination.RECORD -> onTabSelected(BottomTab.RECORD)
                        MapDestination.SETTINGS -> onTabSelected(BottomTab.SETTINGS)
                        MapDestination.CONDITIONS -> onOpenConditions()
                        MapDestination.REC_INFO -> onOpenRecInfo()
                        MapDestination.IMPORT -> onImportClick()
                        MapDestination.DOWNLOAD -> onDownloadArea()
                        MapDestination.GPS -> onGpsClick()
                        MapDestination.FULLSCREEN -> onToggleFullscreen()
                    }
                },
                onDismiss = { moreOpen = false },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth(0.62f)
            )
        }

        MapInspectorHost(
            inspection = inspection,
            settings = settings,
            gpsElevation = currentLocation?.elevation,
            onDismiss = onClearInspection,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = MarginEdge, end = MarginEdge, bottom = if (isDrawingRoute) 88.dp else 16.dp)
        )

        if (isFullscreen) {
            androidx.compose.material3.Text(
                "EXIT",
                color = com.trailmap.gps.ui.theme.OnSurface,
                fontFamily = com.trailmap.gps.ui.theme.JetBrainsMono,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .clickable(onClick = onToggleFullscreen)
            )
        }
    }
}
