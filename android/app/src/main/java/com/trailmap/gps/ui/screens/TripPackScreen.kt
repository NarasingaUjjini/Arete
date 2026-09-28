package com.trailmap.gps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.PackCheckItem
import com.trailmap.gps.data.PackItemStatus
import com.trailmap.gps.data.RouteEntity
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.TripPackAreaMode
import com.trailmap.gps.data.TripPackEntity
import com.trailmap.gps.data.TripPackStatus
import com.trailmap.gps.offline.CorridorBuffer
import com.trailmap.gps.offline.TripPackProgress
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.AlpineSectionLabel
import com.trailmap.gps.ui.components.AlpineSegmentedControl
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.RedAlert
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils

@Composable
fun TripPackScreen(
    route: RouteEntity,
    points: List<TrackPoint>,
    settings: AppSettings,
    pack: TripPackEntity?,
    checks: List<PackCheckItem>,
    progress: TripPackProgress,
    storageBytes: Long,
    forcedOffline: Boolean,
    estimateTiles: (CorridorBuffer, TripPackAreaMode, Boolean, Boolean, Int) -> Int,
    onBack: () -> Unit,
    onPrepareAndStart: (CorridorBuffer, TripPackAreaMode, Boolean, Boolean, Boolean, Boolean, Boolean, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    onToggleAirplaneTest: (Boolean) -> Unit
) {
    var corridor by remember { mutableStateOf(CorridorBuffer.ONE) }
    var areaMode by remember { mutableIntStateOf(0) }
    var includeTopo by remember { mutableStateOf(true) }
    var includeDem by remember { mutableStateOf(true) }
    var includeHillshade by remember { mutableStateOf(false) }
    var includeImagery by remember { mutableStateOf(false) }
    var includeConditions by remember { mutableStateOf(false) }
    var maxZoom by remember { mutableIntStateOf(14) }
    val mode = if (areaMode == 0) TripPackAreaMode.CORRIDOR else TripPackAreaMode.CUSTOM
    val tiles = remember(corridor, areaMode, includeTopo, includeHillshade, maxZoom) {
        estimateTiles(corridor, mode, includeTopo, includeHillshade, maxZoom)
    }
    val estMb = ((tiles * 18L + (if (includeDem) 350 else 0) + (if (includeImagery) 400 else 0)) / 1024)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface)
            }
            Column {
                Text("TRIP PACK", color = LocalAccent.current, fontWeight = FontWeight.SemiBold)
                Text(route.name, color = OnSurface, fontSize = 13.sp)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            pack?.let {
                Text(
                    it.packStatus().name.replace('_', ' '),
                    color = when (it.packStatus()) {
                        TripPackStatus.READY -> TrailGreen
                        TripPackStatus.FAILED -> RedAlert
                        else -> OnSurface
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Measured ${FormatUtils.formatFileSize(it.actualBytes)} · est ${FormatUtils.formatFileSize(it.estimatedBytes)}",
                    color = OnSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            AlpineSectionLabel("Area", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            AlpineSegmentedControl(
                options = listOf("Corridor", "Custom box"),
                selected = areaMode,
                onSelect = { areaMode = it }
            )
            if (areaMode == 0) {
                Text("Buffer", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Text(
                    "How far off the route line to save map tiles, in miles. Wider buffer covers bailouts and wrong turns, and uses more storage.",
                    color = OnSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                AlpineSegmentedControl(
                    options = listOf("0.25", "0.5", "1", "2", "5"),
                    selected = CorridorBuffer.entries.indexOf(corridor),
                    onSelect = { corridor = CorridorBuffer.entries[it] }
                )
            } else {
                Text("Uses the current map download box from the last custom bounds, or the route envelope.", color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            AlpineSectionLabel("Data", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            PackToggle("Arete Topo (USGS public domain)", includeTopo) { includeTopo = it }
            PackToggle("USGS 3DEP terrain / 3D", includeDem) { includeDem = it }
            PackToggle("USGS shaded relief", includeHillshade) { includeHillshade = it }
            PackToggle("USGS NAIP imagery", includeImagery) { includeImagery = it }
            Text(
                "NAIP is USDA aerial photography — leaf-off photos of the ground, not a live satellite feed. Useful for seeing talus, snow, and roadheads when you already have topo. Optional and large.",
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            PackToggle("NWS forecast snapshot", includeConditions) { includeConditions = it }
            Text("Esri, OSM, and OpenTopo tiles are not bulk-downloaded.", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))

            AlpineSectionLabel("Detail", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Text(
                "Highest map zoom saved in the pack. Higher zoom shows more trail and contour detail and uses more tiles. z12 is regional, z14 is typical on-trail, z15 is closest in.",
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            AlpineSegmentedControl(
                options = listOf("z12", "z13", "z14", "z15"),
                selected = (maxZoom - 12).coerceIn(0, 3),
                onSelect = { maxZoom = 12 + it }
            )
            Text("~$estMb MB · $tiles USGS tiles", color = TrailGreen, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

            if (progress.isRunning || progress.paused) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(progress = { progress.progress }, modifier = Modifier.fillMaxWidth(), color = TrailGreen)
                Text(
                    "${progress.phase} · ${progress.completedTiles}/${progress.totalTiles} · ${FormatUtils.formatFileSize(progress.downloadedBytes)}",
                    color = TrailGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            progress.error?.let { Text(it, color = RedAlert, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    progress.isRunning -> {
                        AlpineOutlineButton("Pause", onPause, Modifier.weight(1f))
                        AlpineOutlineButton("Cancel", onCancel, Modifier.weight(1f))
                    }
                    pack?.packStatus() == TripPackStatus.PAUSED -> {
                        AlpinePrimaryButton("Resume", onResume, Modifier.weight(1f))
                        AlpineOutlineButton("Cancel", onCancel, Modifier.weight(1f))
                    }
                    else -> {
                        AlpinePrimaryButton(
                            if (pack == null) "Download pack" else "Download / resume",
                            {
                                onPrepareAndStart(
                                    corridor, mode, includeTopo, includeDem,
                                    includeHillshade, includeImagery, includeConditions, maxZoom
                                )
                            },
                            Modifier.weight(1f),
                            enabled = !progress.isRunning && points.size >= 2
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                AlpineOutlineButton("Verify", onVerify, Modifier.weight(1f))
                AlpineOutlineButton("Delete pack", onDelete, Modifier.weight(1f))
            }

            if (checks.isNotEmpty()) {
                AlpineSectionLabel("Trip pack check", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                checks.forEach { item ->
                    val mark = when (item.status) {
                        PackItemStatus.OK -> "✓"
                        PackItemStatus.ONLINE_ONLY -> "!"
                        PackItemStatus.SKIPPED -> "·"
                        PackItemStatus.STALE -> "!"
                        else -> "✗"
                    }
                    val color = when (item.status) {
                        PackItemStatus.OK -> TrailGreen
                        PackItemStatus.ONLINE_ONLY, PackItemStatus.STALE -> OnSurface
                        PackItemStatus.SKIPPED -> OnSurfaceVariant
                        else -> RedAlert
                    }
                    Text(
                        "$mark  ${item.label}" + if (item.message.isNotBlank()) "  · ${item.message}" else "",
                        color = color,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }

            AlpineSectionLabel("Airplane test", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Force offline", color = OnSurface)
                    Text("Blocks network in the app so you can test the pack.", color = OnSurfaceVariant, fontSize = 11.sp)
                }
                Switch(
                    checked = forcedOffline,
                    onCheckedChange = onToggleAirplaneTest,
                    colors = SwitchDefaults.colors(checkedThumbColor = TrailGreen)
                )
            }

            AlpineSectionLabel("Storage", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Text("Local maps + DEM + packs · ${FormatUtils.formatFileSize(storageBytes)}", color = OnSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PackToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = TrailGreen))
    }
}
