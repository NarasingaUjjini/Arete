package com.trailmap.gps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.TrackPoint
import android.graphics.Bitmap
import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.location.CurrentLocationState
import com.trailmap.gps.location.GpsUpdate
import com.trailmap.gps.map.TrailMapView
import com.trailmap.gps.terrain.TerrainInspection
import com.trailmap.gps.ui.components.TerrainInspectorCard
import com.trailmap.gps.ui.components.AlpineBottomNav
import com.trailmap.gps.ui.components.AlpineBottomNavHeight
import com.trailmap.gps.ui.components.CompassRoseButton
import com.trailmap.gps.ui.components.GpsStatusChip
import com.trailmap.gps.ui.components.MapMetricsBar
import com.trailmap.gps.ui.theme.AlpineShape
import com.trailmap.gps.ui.theme.Amber
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.Canvas
import com.trailmap.gps.ui.theme.Gutter
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.InstrumentBay
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.MarginEdge
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.TouchTarget

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
    onTabSelected: (BottomTab) -> Unit,
    onRecenter: () -> Unit,
    onToggleNorthUp: () -> Unit,
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
    dataBarInputs: com.trailmap.gps.ui.components.DataBarInputs = com.trailmap.gps.ui.components.DataBarInputs(),
    drawTool: com.trailmap.gps.data.DrawTool = com.trailmap.gps.data.DrawTool.NONE,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndoDraw: () -> Unit = {},
    onRedoDraw: () -> Unit = {},
    onCancelDraw: () -> Unit = {},
    onFinishDraw: () -> Unit = {},
    onDrawToolChange: (com.trailmap.gps.data.DrawTool) -> Unit = {},
    routeColorHex: String = "#FF6B00",
    overlayOpacity: Float = 0.48f
) {
    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        TrailMapView(
            modifier = Modifier.fillMaxSize(),
            mapLayer = settings.mapLayer,
            hillshade = settings.hillshadeEnabled,
            contourGeoJson = contourGeoJson,
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
            onBearingChanged = onBearingChanged
        )

        // Top chrome — visible in fullscreen too (metrics stay on screen)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = MarginEdge)
                .padding(top = 4.dp)
        ) {
            if (!isFullscreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ARETE",
                            color = OnSurface,
                            fontFamily = JetBrainsMono,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "PLAN",
                            color = Amber,
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                    GpsStatusChip(state = locationState, onClick = onGpsClick)
                    CompassRoseButton(
                        mapBearing = mapBearing,
                        northUp = settings.northUp,
                        onClick = onToggleNorthUp
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(top = if (isFullscreen) 0.dp else Gutter),
                contentAlignment = Alignment.Center
            ) {
                MapMetricsBar(settings = settings, inputs = dataBarInputs.copy(location = currentLocation))
            }
        }

        if (!isFullscreen) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = MarginEdge),
                verticalArrangement = Arrangement.spacedBy(Gutter)
            ) {
                AlpineMapFab(Icons.Default.MyLocation, "Center on my location", false, onRecenter)
                if (!isDrawingRoute) {
                    AlpineMapFab(Icons.Default.Download, "Import route", true, onImportClick)
                }
                if (!isDrawingRoute) {
                    AlpineMapFab(Icons.Default.Edit, "Draw route", false, onToggleDrawRoute)
                    AlpineMapFab(Icons.Default.WbSunny, "Mountain conditions", false, onOpenConditions)
                }
            }

            if (isDrawingRoute) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = AlpineBottomNavHeight + 8.dp)
                        .navigationBarsPadding(),
                    color = InstrumentBay,
                    shape = AlpineShape,
                    border = BorderStroke(1.dp, Hairline)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            if (drawTool == com.trailmap.gps.data.DrawTool.WAYPOINT) {
                                "PIN  ·  ${drawPoints.size} vertices"
                            } else {
                                "ROUTE PLANNER  ·  ${drawPoints.size} vertices"
                            },
                            color = Amber,
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DrawAction("UNDO", canUndo, onUndoDraw)
                            DrawAction("REDO", canRedo, onRedoDraw)
                            DrawAction(
                                "ROUTE",
                                true,
                                { onDrawToolChange(com.trailmap.gps.data.DrawTool.ROUTE) },
                                selected = drawTool == com.trailmap.gps.data.DrawTool.ROUTE
                            )
                            DrawAction(
                                "PIN",
                                true,
                                { onDrawToolChange(com.trailmap.gps.data.DrawTool.WAYPOINT) },
                                selected = drawTool == com.trailmap.gps.data.DrawTool.WAYPOINT
                            )
                            DrawAction("CANCEL", true, onCancelDraw)
                            DrawAction("FINISH", drawPoints.size >= 2, onFinishDraw, primary = true)
                        }
                    }
                }
            }

            inspection?.let { sample ->
                TerrainInspectorCard(
                    inspection = sample,
                    settings = settings,
                    gpsElevationMeters = currentLocation?.elevation,
                    onDismiss = onClearInspection,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = AlpineBottomNavHeight + 12.dp)
                        .navigationBarsPadding()
                        .padding(horizontal = MarginEdge)
                )
            }

            AlpineBottomNav(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = selectedTab,
                isRecording = isRecording,
                onTabSelected = onTabSelected
            )
        }

        // Fullscreen toggle — bottom-left, clear of bottom nav
        Surface(
            onClick = onToggleFullscreen,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = MarginEdge)
                .then(
                    if (isFullscreen) {
                        Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                    } else {
                        Modifier.padding(bottom = AlpineBottomNavHeight + 8.dp).navigationBarsPadding()
                    }
                )
                .size(TouchTarget),
            shape = AlpineShape,
            color = InstrumentBay,
            border = BorderStroke(1.dp, Hairline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isFullscreen) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen map",
                    modifier = Modifier.size(20.dp),
                    tint = OnSurface
                )
            }
        }
    }
}

@Composable
private fun DrawAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
    primary: Boolean = false
) {
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        color = when {
            primary && enabled -> Amber
            selected -> Amber.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        shape = AlpineShape,
        border = BorderStroke(1.dp, if (primary || selected) Amber else Hairline),
        modifier = Modifier.height(TouchTarget)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            color = if (primary && enabled) Canvas else if (enabled) OnSurface else OnSurfaceVariant,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AlpineMapFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    primary: Boolean,
    onClick: () -> Unit,
    tint: Color = if (primary) Black else OnSurface
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(TouchTarget),
        shape = AlpineShape,
        color = if (primary) Amber else InstrumentBay,
        border = if (primary) null else BorderStroke(1.dp, Hairline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = tint)
        }
    }
}
