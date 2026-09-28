package com.trailmap.gps.ui.maptools

import android.hardware.GeomagneticField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.DrawTool
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.map.MapScaleReading
import com.trailmap.gps.terrain.TerrainInspection
import com.trailmap.gps.terrain.TerrainOverlay
import com.trailmap.gps.ui.components.ElevationProfile
import com.trailmap.gps.ui.components.TerrainInspectorCard
import com.trailmap.gps.ui.theme.AlpineShape
import com.trailmap.gps.ui.theme.Canvas
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.HazardRed
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TouchTarget
import com.trailmap.gps.util.FormatUtils
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MapScaleBar(scale: MapScaleReading, modifier: Modifier = Modifier, ink: Color = OnSurface) {
    val widthDp = (scale.barMeters / scale.metersPerPixel / 3.0).coerceIn(28.0, 96.0).dp
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .width(widthDp)
                .height(8.dp)
                .background(Color.Transparent)
                .padding(0.dp)
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawLine(ink, Offset(0f, h - 1f), Offset(w, h - 1f), 2f)
                drawLine(ink, Offset(1f, 0f), Offset(1f, h), 2f)
                drawLine(ink, Offset(w - 1f, 0f), Offset(w - 1f, h), 2f)
            }
        }
        Text(scale.label, color = ink, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CompassInstrument(
    snapshot: CompassSnapshot,
    onDismiss: () -> Unit,
    onToggleNorth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CompassRing(
                heading = snapshot.headingShown,
                bearing = snapshot.bearingShown,
                summit = snapshot.summitShown,
                modifier = Modifier.size(220.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    FormatUtils.formatBearing(snapshot.headingShown),
                    color = OnSurface,
                    fontFamily = JetBrainsMono,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (snapshot.headingReady) "HEADING" else "COURSE",
                    color = OnSurfaceVariant,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp
                )
            }
        }
        if (snapshot.headingReady && snapshot.course != 0.0) {
            Text(
                "GPS COURSE  ${FormatUtils.formatBearing(snapshot.courseShown)}",
                color = OnSurfaceVariant,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Text(
            "TO ${snapshot.nextLabel.uppercase().take(12)}  ${FormatUtils.formatBearing(snapshot.bearingShown)}",
            color = LocalAccent.current,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            if (snapshot.magneticNorth) "Magnetic · decl ${FormatUtils.formatBearing(snapshot.declination)}"
            else "True north",
            color = OnSurfaceVariant,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            modifier = Modifier
                .clickable(onClick = onToggleNorth)
                .padding(top = 4.dp, bottom = 4.dp)
        )
        Text("CLOSE", color = OnSurface, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
    }
}

@Composable
private fun CompassRing(heading: Double, bearing: Double, summit: Double?, modifier: Modifier) {
    val accent = LocalAccent.current
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 6f
        drawCircle(Color(0x99121A16), radius = r, center = c)
        drawCircle(Color(0x66F4F6F2), radius = r, center = c, style = Stroke(1.5f))
        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble() - 90.0)
            val inner = if (deg % 90 == 0) r - 14f else r - 8f
            drawLine(
                color = if (deg == 0) accent else Color(0x88F4F6F2),
                start = Offset(c.x + (inner * cos(rad)).toFloat(), c.y + (inner * sin(rad)).toFloat()),
                end = Offset(c.x + (r * cos(rad)).toFloat(), c.y + (r * sin(rad)).toFloat()),
                strokeWidth = if (deg % 90 == 0) 2.5f else 1f
            )
        }
        fun mark(degrees: Double, color: Color, inset: Float) {
            rotate(degrees.toFloat(), pivot = c) {
                drawCircle(color, 5f, Offset(c.x, c.y - r + inset))
            }
        }
        rotate(heading.toFloat(), pivot = c) {
            drawLine(Color(0xCC38BDF8), c, Offset(c.x, c.y - r * 0.62f), 4f, StrokeCap.Round)
        }
        mark(bearing, accent, 10f)
        summit?.let { mark(it, Color(0xFFF2F4F7), 18f) }
    }
}

@Composable
fun LocationInstrument(
    field: MapFieldSnapshot,
    onRecenter: () -> Unit,
    onToggleNorthUp: () -> Unit,
    northUp: Boolean,
    onOpenDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color(0xCC121518), shape = AlpineShape, border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.padding(12.dp)) {
            Text("${field.qualityLabel}  ${field.accuracyLabel}  ·  ${field.fixAgeLabel}", color = OnSurface, fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (field.positionReason.isNotBlank()) {
                Text(field.positionReason, color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(field.coordinates, color = OnSurface, fontFamily = JetBrainsMono, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Text(field.elevationLabel, color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CENTER", color = LocalAccent.current, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onRecenter))
                Text(if (northUp) "NORTH UP" else "TRACK UP", color = OnSurface, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onToggleNorthUp))
                Text("MORE", color = OnSurface, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onOpenDiagnostics))
                Text("CLOSE", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onDismiss))
            }
        }
    }
}

@Composable
fun TerrainInstrument(
    overlay: TerrainOverlay,
    hasLocalDem: Boolean,
    onOverlayChange: (TerrainOverlay) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color(0xCC121518), shape = AlpineShape, border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.padding(12.dp)) {
            Text("TERRAIN", color = OnSurface, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                if (hasLocalDem) "Long-press the map to inspect. Slope/aspect only paint this downloaded box."
                else "No 3DEP here. Download a map area first. Long-press still inspects GPS vs DEM if a box exists.",
                color = OnSurfaceVariant,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerrainOverlay.entries.forEach { mode ->
                    val selected = overlay == mode
                    Text(
                        when (mode) {
                            TerrainOverlay.NONE -> "OFF"
                            TerrainOverlay.SLOPE -> "SLOPE"
                            TerrainOverlay.ASPECT -> "ASPECT"
                            TerrainOverlay.HILLSHADE -> "SHADE"
                        },
                        color = if (selected) LocalAccent.current else OnSurface,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOverlayChange(mode) }.padding(vertical = 8.dp)
                    )
                }
                Text("CLOSE", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun ElevationInstrument(
    points: List<TrackPoint>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color(0xCC121518), shape = AlpineShape, border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ELEVATION", color = OnSurface, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("CLOSE", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onDismiss))
            }
            if (points.size < 2) {
                Text("Select or draw a route to see a profile.", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                ElevationProfile(
                    points = points,
                    showContainer = false,
                    label = "",
                    modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun RouteInstrument(
    drawTool: DrawTool,
    vertexCount: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToolChange: (DrawTool) -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit
) {
    Surface(color = Color(0xCC121518), shape = AlpineShape, border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                if (drawTool == DrawTool.WAYPOINT) "PIN  ·  $vertexCount" else "ROUTE  ·  $vertexCount",
                color = LocalAccent.current,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolWord("UNDO", canUndo, onUndo)
                ToolWord("REDO", canRedo, onRedo)
                ToolWord("ROUTE", true, { onToolChange(DrawTool.ROUTE) }, selected = drawTool == DrawTool.ROUTE)
                ToolWord("PIN", true, { onToolChange(DrawTool.WAYPOINT) }, selected = drawTool == DrawTool.WAYPOINT)
                ToolWord("CANCEL", true, onCancel)
                ToolWord("FINISH", vertexCount >= 2, onFinish, primary = true)
            }
        }
    }
}

@Composable
fun DestinationMenu(
    isRecording: Boolean,
    isFullscreen: Boolean,
    mapChrome: com.trailmap.gps.data.MapChromeLayout = com.trailmap.gps.data.MapChromeLayout.RAIL,
    onMapChrome: (com.trailmap.gps.data.MapChromeLayout) -> Unit = {},
    onSelect: (MapDestination) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = Color(0xEE121518), shape = AlpineShape, border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.padding(12.dp)) {
            Text("MORE", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("MAP LAYOUT", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                com.trailmap.gps.data.MapChromeLayout.entries.forEach { layout ->
                    val selected = mapChrome == layout
                    Text(
                        layout.name,
                        color = if (selected) LocalAccent.current else OnSurface,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                onMapChrome(layout)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
            MapDestination.entries.forEach { dest ->
                val label = when (dest) {
                    MapDestination.RECORD -> if (isRecording) "Record · live" else "Record"
                    MapDestination.FULLSCREEN -> if (isFullscreen) "Exit fullscreen" else "Fullscreen"
                    else -> dest.label
                }
                Text(
                    label.uppercase(),
                    color = if (dest == MapDestination.RECORD && isRecording) HazardRed else OnSurface,
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(dest) }
                        .padding(vertical = 10.dp)
                )
            }
            Text("CLOSE", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(top = 4.dp))
        }
    }
}

@Composable
fun ToolWord(label: String, enabled: Boolean, onClick: () -> Unit, selected: Boolean = false, primary: Boolean = false) {
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        color = when {
            primary && enabled -> LocalAccent.current
            selected -> LocalAccent.current.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        shape = AlpineShape,
        border = BorderStroke(1.dp, if (primary || selected) LocalAccent.current else Hairline),
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
fun MapInspectorHost(
    inspection: TerrainInspection?,
    settings: AppSettings,
    gpsElevation: Double?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    inspection?.let {
        TerrainInspectorCard(
            inspection = it,
            settings = settings,
            gpsElevationMeters = gpsElevation,
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}

fun declinationAt(location: com.trailmap.gps.location.GpsUpdate?): Double {
    val loc = location ?: return 0.0
    return GeomagneticField(
        loc.latitude.toFloat(),
        loc.longitude.toFloat(),
        loc.elevation.toFloat(),
        System.currentTimeMillis()
    ).declination.toDouble()
}
