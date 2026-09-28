package com.trailmap.gps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import com.trailmap.gps.ui.theme.AlpineShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.R
import com.trailmap.gps.data.AccentTheme
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.CoordinateFormat
import com.trailmap.gps.data.DataBarMetric
import com.trailmap.gps.data.DataBarProfile
import com.trailmap.gps.data.DistanceUnit
import com.trailmap.gps.data.ElevationUnit
import com.trailmap.gps.data.MapLayer
import com.trailmap.gps.data.OverlayStrength
import com.trailmap.gps.data.OffRouteCorridorSetting
import com.trailmap.gps.data.PowerProfile
import com.trailmap.gps.data.RouteEntity
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.map.TrailMapView
import com.trailmap.gps.offline.OfflineDownloadState
import com.trailmap.gps.ui.components.AlpineCardDivider
import com.trailmap.gps.ui.components.AlpineDropdown
import com.trailmap.gps.ui.components.AlpineInlineSegmentedControl
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.AlpineRadioRow
import com.trailmap.gps.ui.components.AlpineSectionLabel
import com.trailmap.gps.ui.components.AlpineSegmentedControl
import com.trailmap.gps.ui.components.AlpineSettingsCard
import com.trailmap.gps.ui.components.AlpineSettingsRow
import com.trailmap.gps.ui.components.DataBar
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.GrayMuted
import com.trailmap.gps.ui.theme.Gutter
import com.trailmap.gps.ui.theme.MarginEdge
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.RedAlert
import com.trailmap.gps.ui.theme.SectionGap
import com.trailmap.gps.ui.theme.SurfaceContainerHighest
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.TouchTarget
import com.trailmap.gps.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    gnssRefreshing: Boolean,
    gnssMessage: String?,
    onBack: () -> Unit,
    onDistanceUnitChange: (DistanceUnit) -> Unit,
    onElevationUnitChange: (ElevationUnit) -> Unit,
    onCoordinateFormatChange: (CoordinateFormat) -> Unit,
    onPowerProfileChange: (PowerProfile) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onOffRouteCorridorChange: (OffRouteCorridorSetting) -> Unit,
    onDownloadMapArea: () -> Unit,
    onOpenGpsDiagnostics: () -> Unit,
    onOpenConditions: () -> Unit = {},
    onDataBarProfileChange: (DataBarProfile) -> Unit = {},
    onDataBarSlotChange: (Int, DataBarMetric) -> Unit = { _, _ -> },
    onAccentChange: (AccentTheme) -> Unit = {},
    onOverlayStrengthChange: (OverlayStrength) -> Unit = {},
    onLargeNumbersChange: (Boolean) -> Unit = {},
    onMapChromeChange: (com.trailmap.gps.data.MapChromeLayout) -> Unit = {},
    locationSummary: String
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        "SETTINGS",
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .background(Black)
                .padding(horizontal = 16.dp)
                .padding(bottom = SectionGap)
        ) {
            AlpineSectionLabel("Units", modifier = Modifier.padding(top = 16.dp, bottom = Gutter))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Distance", color = OnSurface, style = MaterialTheme.typography.bodyLarge)
                    AlpineSettingsCard {
                        AlpineSegmentedControl(
                            options = listOf("mi", "km"),
                            selected = if (settings.distanceUnit == DistanceUnit.MILES) 0 else 1,
                            onSelect = { onDistanceUnitChange(if (it == 0) DistanceUnit.MILES else DistanceUnit.KILOMETERS) },
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elevation", color = OnSurface, style = MaterialTheme.typography.bodyLarge)
                    AlpineSettingsCard {
                        AlpineSegmentedControl(
                            options = listOf("ft", "m"),
                            selected = if (settings.elevationUnit == ElevationUnit.FEET) 0 else 1,
                            onSelect = { onElevationUnitChange(if (it == 0) ElevationUnit.FEET else ElevationUnit.METERS) },
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            AlpineSectionLabel("Coordinates", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            AlpineSettingsCard {
                AlpineSettingsRow(label = "Format") {
                    AlpineInlineSegmentedControl(
                        options = listOf("DD", "DMS", "DDM", "UTM", "MGRS"),
                        selected = settings.coordinateFormat.ordinal.coerceIn(0, 4),
                        onSelect = { onCoordinateFormatChange(CoordinateFormat.entries[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AlpineSectionLabel("Power Profile", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            AlpineSettingsCard {
                AlpineSegmentedControl(
                    options = listOf("Battery", "Balanced", "Accuracy"),
                    selected = settings.powerProfile.ordinal,
                    onSelect = { onPowerProfileChange(PowerProfile.entries[it]) },
                    modifier = Modifier.padding(8.dp)
                )
            }
            Text(
                text = when (settings.powerProfile) {
                    PowerProfile.BATTERY -> "Map browsing uses a slower GPS interval. Navigation and recording stay at 1s."
                    PowerProfile.BALANCED -> "Map browsing ~3–4s. Navigation and recording stay high quality."
                    PowerProfile.ACCURACY -> "Browsing prefers frequent updates. Navigation and recording stay at 1s."
                },
                color = OnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )

            AlpineSectionLabel("Prepare for offline", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            AlpineSettingsCard {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Download a map box you pan and scale yourself. That stores USGS tiles and optional 3DEP for that area. GPS assistance is refreshed after the tiles finish.",
                        color = OnSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    val gnssLabel = if (settings.gnssAssistanceUpdatedAt <= 0L) {
                        "GPS data · Never downloaded"
                    } else if (FormatUtils.isStale(settings.gnssAssistanceUpdatedAt)) {
                        "GPS data · Stale (${FormatUtils.formatRelativeTime(settings.gnssAssistanceUpdatedAt)})"
                    } else {
                        "GPS data · ${FormatUtils.formatRelativeTime(settings.gnssAssistanceUpdatedAt)}"
                    }
                    Text(
                        gnssLabel,
                        color = if (FormatUtils.isStale(settings.gnssAssistanceUpdatedAt)) OnSurfaceVariant else LocalAccent.current,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (gnssMessage != null) {
                        Text(gnssMessage, color = OnSurfaceVariant, fontSize = 12.sp)
                    }
                    AlpinePrimaryButton(
                        text = if (gnssRefreshing) "Opening…" else "Download terrain box",
                        onClick = onDownloadMapArea,
                        enabled = !gnssRefreshing,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AlpineSectionLabel("Off-route corridor", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            AlpineSettingsCard {
                AlpineSegmentedControl(
                    options = listOf("Narrow", "Normal", "Wide"),
                    selected = settings.offRouteCorridor.ordinal,
                    onSelect = { onOffRouteCorridorChange(OffRouteCorridorSetting.entries[it]) },
                    modifier = Modifier.padding(8.dp)
                )
            }
            Text(
                "Warnings use distance to the route line and ignore GPS uncertainty larger than the corridor.",
                color = OnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
            Text(
                locationSummary,
                color = LocalAccent.current,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 8.dp, start = 4.dp)
                    .clickable(onClick = onOpenGpsDiagnostics)
            )
            Text(
                "Tap for GPS diagnostics",
                color = OnSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                    .clickable(onClick = onOpenGpsDiagnostics)
            )

            AlpineSectionLabel("Mountain conditions", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            Text(
                "NWS forecast, daylight, wildfire, public land, water, and official avalanche links.",
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(onClick = onOpenConditions)
                    .padding(start = 4.dp, bottom = 8.dp)
            )
            Text(
                "Open conditions",
                color = LocalAccent.current,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onOpenConditions).padding(start = 4.dp, bottom = 4.dp)
            )

            AlpineSectionLabel("Data bar", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            Text("Exactly three slots on the map. Changing a slot switches to Custom.", color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            AlpineSettingsCard {
                DataBarProfile.entries.forEach { profile ->
                    AlpineRadioRow(
                        label = profile.label,
                        selected = settings.dataBarProfile == profile,
                        onClick = { onDataBarProfileChange(profile) }
                    )
                }
            }
            val (slot1, slot2, slot3) = DataBar.metrics(settings)
            val slotMetrics = listOf(slot1, slot2, slot3)
            val metricOptions = DataBarMetric.entries
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                slotMetrics.forEachIndexed { index, current ->
                    AlpineDropdown(
                        label = "Slot ${index + 1}",
                        options = metricOptions.map { it.label },
                        selectedIndex = metricOptions.indexOf(current).coerceAtLeast(0),
                        onSelect = { onDataBarSlotChange(index, metricOptions[it]) }
                    )
                }
            }

            AlpineSectionLabel("Display", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            AlpineSettingsCard {
                SettingsToggle(
                    label = "Keep screen on during navigation",
                    checked = settings.keepScreenOn,
                    onCheckedChange = onKeepScreenOnChange
                )
                AlpineCardDivider()
                SettingsToggle(
                    label = "Large numbers",
                    checked = settings.largeNumbers,
                    onCheckedChange = onLargeNumbersChange
                )
                AlpineCardDivider()
                Text(
                    "Dark theme — Always on. Color is never the only status cue.",
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }

            AlpineSectionLabel("Map layout", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            Text(
                "Same tools. Three faces. This only changes how instruments sit on the map.",
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            AlpineSettingsCard {
                com.trailmap.gps.data.MapChromeLayout.entries.forEach { layout ->
                    AlpineRadioRow(
                        label = layout.title,
                        selected = settings.mapChrome == layout,
                        onClick = { onMapChromeChange(layout) }
                    )
                    Text(
                        layout.summary,
                        color = OnSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }

            AlpineSectionLabel("Accent (UI / route)", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            Text("Buttons, titles, selected chips, and the route line follow this color. Error, warning, GPS, and recording colors stay reserved.", color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            AlpineSettingsCard {
                AccentTheme.entries.forEach { theme ->
                    AlpineRadioRow(
                        label = theme.label,
                        selected = settings.accentTheme == theme,
                        onClick = { onAccentChange(theme) }
                    )
                }
            }
            AlpineSectionLabel("Terrain overlay strength", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
            Text(
                "How hard slope/aspect sit on the map. They only appear over downloaded 3DEP. Low keeps the route visible.",
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            AlpineSettingsCard {
                OverlayStrength.entries.forEach { strength ->
                    AlpineRadioRow(
                        label = strength.label,
                        selected = settings.overlayStrength == strength,
                        onClick = { onOverlayStrengthChange(strength) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(SectionGap))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Arete",
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Arete v0.7", color = GrayMuted, style = MaterialTheme.typography.bodySmall)
                Text("No account · No tracking · No ads", color = GrayMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerPickerSheet(
    settings: AppSettings,
    onLayerChange: (MapLayer) -> Unit,
    onHillshadeChange: (Boolean) -> Unit,
    onContoursChange: (Boolean) -> Unit = {},
    terrainOverlay: com.trailmap.gps.terrain.TerrainOverlay = com.trailmap.gps.terrain.TerrainOverlay.NONE,
    onTerrainOverlayChange: (com.trailmap.gps.terrain.TerrainOverlay) -> Unit = {},
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Black)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
                .size(width = 40.dp, height = 4.dp)
                .background(SurfaceContainerHighest, AlpineShape)
        )
        Text(
            "MAP LAYERS",
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Arete Topo is USGS terrain-first. OpenTopo, Esri, and OSM are online-only.",
            color = OnSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        AlpineSectionLabel("Base Layer", modifier = Modifier.padding(bottom = Gutter))
        Surface(
            color = com.trailmap.gps.ui.theme.SurfaceContainerLow,
            shape = AlpineShape
        ) {
            Column {
                MapLayer.entries.forEachIndexed { index, layer ->
                    AlpineRadioRow(
                        label = layer.label,
                        selected = settings.mapLayer == layer,
                        onClick = { onLayerChange(layer) }
                    )
                    if (index < MapLayer.entries.lastIndex) AlpineCardDivider()
                }
            }
        }
        Text(
            "Historical / classic USGS uses current USGS Topo underneath plus classic paper-style USA Topo Maps online. HTMC is not a public tile cache. Not a substitute for a current map.",
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        AlpineSectionLabel("Overlays", modifier = Modifier.padding(top = SectionGap, bottom = Gutter))
        Surface(
            color = com.trailmap.gps.ui.theme.SurfaceContainerLow,
            shape = AlpineShape
        ) {
            Column {
                SettingsToggle(
                    label = "Hillshade (USGS relief)",
                    checked = settings.hillshadeEnabled,
                    onCheckedChange = onHillshadeChange
                )
                AlpineCardDivider()
                SettingsToggle(
                    label = "DEM contours (local 3DEP)",
                    checked = settings.contoursEnabled,
                    onCheckedChange = onContoursChange
                )
                AlpineCardDivider()
            Text(
                "TERRAIN OVERLAY — local 3DEP only",
                color = OnSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Text(
                "Slope and aspect paint only where you already downloaded 3DEP. Keep strength Low so the trail stays readable.",
                color = OnSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            )
                com.trailmap.gps.terrain.TerrainOverlay.entries.forEach { mode ->
                    AlpineRadioRow(
                        label = when (mode) {
                            com.trailmap.gps.terrain.TerrainOverlay.NONE -> "None"
                            com.trailmap.gps.terrain.TerrainOverlay.SLOPE -> "Slope"
                            com.trailmap.gps.terrain.TerrainOverlay.ASPECT -> "Aspect"
                            com.trailmap.gps.terrain.TerrainOverlay.HILLSHADE -> "DEM hillshade"
                        },
                        selected = terrainOverlay == mode,
                        onClick = { onTerrainOverlayChange(mode) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = AlpineShape,
            colors = ButtonDefaults.buttonColors(containerColor = LocalAccent.current, contentColor = Black)
        ) {
            Text("DONE", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineDownloadScreen(
    route: RouteEntity?,
    points: List<TrackPoint>,
    settings: AppSettings,
    downloadState: OfflineDownloadState,
    layerLabels: String,
    gnssRefreshing: Boolean,
    gnssMessage: String?,
    onBack: () -> Unit,
    onDownload: (bounds: DoubleArray, maxZoom: Int, includeDem: Boolean) -> Unit,
    onCancelDownload: () -> Unit = {},
    estimateTiles: (bounds: DoubleArray, maxZoom: Int) -> Int,
    demProgress: Float = 0f,
    demError: String? = null,
    demDownloading: Boolean = false,
    currentLocation: com.trailmap.gps.location.GpsUpdate? = null
) {
    var maxZoom by remember { mutableFloatStateOf(15f) }
    var areaPadding by remember { mutableFloatStateOf(0.25f) }
    var includeDem by remember { mutableStateOf(true) }
    var downloadBounds by remember { mutableStateOf<DoubleArray?>(null) }
    val boxInset = (16 + (1f - areaPadding) * 56).dp
    val accent = LocalAccent.current
    val fallbackBounds = currentLocation?.let { loc ->
        val pad = 0.04
        doubleArrayOf(loc.longitude - pad, loc.latitude - pad, loc.longitude + pad, loc.latitude + pad)
    }
    val activeBounds = downloadBounds ?: fallbackBounds
    val estimatedTiles = remember(activeBounds, maxZoom) {
        activeBounds?.let { estimateTiles(it, maxZoom.toInt()) } ?: 0
    }
    val estimatedMb = (estimatedTiles * 18L / 1024).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(TouchTarget),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurfaceVariant)
            }
            Text("DOWNLOAD TERRAIN BOX", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Box(modifier = Modifier.fillMaxWidth().weight(0.45f)) {
            val boxCornerRadius = 8.dp
            TrailMapView(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val inset = boxInset.toPx()
                        val rectW = size.width - inset * 2f
                        val rectH = size.height - inset * 2f
                        if (rectW <= 0f || rectH <= 0f) return@drawWithContent
                        val topLeft = Offset(inset, inset)
                        val rectSize = Size(rectW, rectH)
                        val radius = boxCornerRadius.toPx()
                        drawRoundRect(
                            color = accent.copy(alpha = 0.06f),
                            topLeft = topLeft,
                            size = rectSize,
                            cornerRadius = CornerRadius(radius)
                        )
                        drawRoundRect(
                            color = accent,
                            topLeft = topLeft,
                            size = rectSize,
                            cornerRadius = CornerRadius(radius),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    },
                mapLayer = settings.mapLayer,
                hillshade = settings.hillshadeEnabled,
                routePoints = points,
                currentLocation = currentLocation,
                recenterTrigger = if (points.size >= 2) 0 else 1,
                fitRouteTrigger = if (points.size >= 2) 1 else 0,
                autoFitRoute = false,
                regionInsetDp = boxInset,
                onRegionBoundsChanged = { downloadBounds = it }
            )
        }

        Column(
            modifier = Modifier
                .weight(0.55f)
                .navigationBarsPadding()
                .padding(horizontal = MarginEdge, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(route?.name ?: "Custom area", color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Pan and zoom so the green box covers the ground you want offline. Tilt is locked here so the box stays flat. Drag Area Size to grow or shrink the box, then tap Download.",
                color = OnSurface,
                fontSize = 13.sp
            )
            Text(
                "Packs $layerLabels, then refreshes GPS assistance.",
                color = accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            HorizontalDivider(color = OutlineVariant, modifier = Modifier.padding(vertical = 12.dp))

            AlpineSectionLabel("Area Size")
            Text(
                if (areaPadding < 0.33f) "Small download box" else if (areaPadding < 0.66f) "Medium download box" else "Large download box",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Slider(
                value = areaPadding,
                onValueChange = { areaPadding = it },
                valueRange = 0f..1f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = SurfaceContainerHighest
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TIGHT", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("WIDE", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            AlpineSectionLabel("Detail Level")
            Text("Zoom 10 — ${maxZoom.toInt()}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = maxZoom,
                onValueChange = { maxZoom = it },
                valueRange = 12f..16f,
                steps = 3,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = SurfaceContainerHighest
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = com.trailmap.gps.ui.theme.SurfaceContainerLow,
                shape = AlpineShape,
                border = BorderStroke(1.dp, OutlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("EST. SIZE", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("~$estimatedMb MB", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        "$estimatedTiles tiles · $layerLabels" + if (includeDem) " · USGS 3DEP" else "",
                        color = OnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    SettingsToggle(
                        label = "Include 3DEP terrain",
                        checked = includeDem,
                        onCheckedChange = { includeDem = it }
                    )
                    Text(
                        "3DEP slope, aspect, contours, and 3D only exist inside downloaded terrain boxes. They are not a live nationwide layer.",
                        color = OnSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    val gnssStatus = when {
                        settings.gnssAssistanceUpdatedAt <= 0L -> "GPS assistance not downloaded yet"
                        FormatUtils.isStale(settings.gnssAssistanceUpdatedAt) ->
                            "GPS assistance stale · ${FormatUtils.formatRelativeTime(settings.gnssAssistanceUpdatedAt)}"
                        else -> "GPS assistance · ${FormatUtils.formatRelativeTime(settings.gnssAssistanceUpdatedAt)}"
                    }
                    Text(
                        gnssStatus,
                        color = if (FormatUtils.isStale(settings.gnssAssistanceUpdatedAt)) OnSurfaceVariant else accent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (gnssMessage != null) {
                        Text(gnssMessage, color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (!downloadState.error.isNullOrBlank()) {
                Text(downloadState.error ?: "", color = RedAlert, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (!demError.isNullOrBlank()) {
                Text(demError ?: "", color = RedAlert, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (demDownloading) {
                Text("Terrain ${(demProgress * 100).toInt()}%", color = accent, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (downloadState.isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = accent,
                    trackColor = SurfaceContainerHighest
                )
                Text(
                    "Downloading… ${(downloadState.progress * 100).toInt()}%",
                    color = accent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            val busy = downloadState.isDownloading || demDownloading || gnssRefreshing
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gutter)) {
                AlpineOutlineButton(
                    text = if (downloadState.isDownloading || demDownloading) "Stop" else "Cancel",
                    onClick = {
                        if (downloadState.isDownloading || demDownloading) onCancelDownload()
                        else onBack()
                    },
                    modifier = Modifier.weight(1f)
                )
                AlpinePrimaryButton(
                    text = if (route?.offlineDownloaded == true) "Re-download" else "Download",
                    onClick = { activeBounds?.let { onDownload(it, maxZoom.toInt(), includeDem) } },
                    enabled = !busy && estimatedTiles > 0 && activeBounds != null,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), color = OnSurface, style = MaterialTheme.typography.bodyLarge, letterSpacing = 0.5.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LocalAccent.current,
                checkedTrackColor = LocalAccent.current.copy(alpha = 0.4f),
                uncheckedThumbColor = OnSurfaceVariant,
                uncheckedTrackColor = SurfaceContainerHighest
            )
        )
    }
}
