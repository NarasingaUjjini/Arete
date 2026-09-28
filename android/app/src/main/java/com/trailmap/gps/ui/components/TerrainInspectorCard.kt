package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.geo.Coordinates
import com.trailmap.gps.terrain.TerrainInspection
import com.trailmap.gps.ui.theme.AlpineShape
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.InstrumentPod
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TelemetryMuted
import com.trailmap.gps.util.FormatUtils
import kotlin.math.abs

@Composable
fun TerrainInspectorCard(
    inspection: TerrainInspection,
    settings: AppSettings,
    gpsElevationMeters: Double?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = InstrumentPod,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (inspection.loading) "TERRAIN INSPECT · LOADING" else "TERRAIN INSPECT",
                    color = LocalAccent.current,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    "DISMISS",
                    color = OnSurfaceVariant,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp)
                )
            }
            Text(
                Coordinates.format(inspection.lat, inspection.lon, settings.coordinateFormat),
                color = OnSurface,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (inspection.loading) {
                Text(
                    "Fetching elevation…",
                    color = OnSurfaceVariant,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectorStat(
                    label = "DEM",
                    value = inspection.demElevationMeters?.let {
                        FormatUtils.formatElevation(it, settings.elevationUnit)
                    } ?: "—",
                    hint = "USGS 3DEP",
                    modifier = Modifier.weight(1f)
                )
                InspectorStat(
                    label = "GPS",
                    value = gpsElevationMeters?.let {
                        FormatUtils.formatElevation(it, settings.elevationUnit)
                    } ?: "—",
                    hint = "GNSS",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectorStat(
                    label = "SLOPE",
                    value = inspection.slopeDegrees?.let { "${it.toInt()}°" } ?: "—",
                    hint = "at point",
                    modifier = Modifier.weight(1f)
                )
                InspectorStat(
                    label = "ASPECT",
                    value = inspection.aspectLabel?.let { label ->
                        val deg = inspection.aspectDegrees?.toInt()?.let { " $it°" } ?: ""
                        "$label$deg"
                    } ?: "—",
                    hint = "",
                    modifier = Modifier.weight(1f)
                )
            }
            if (inspection.avgDistanceMeters != null && inspection.avgGradePercent != null) {
                val rise = inspection.avgRiseMeters ?: 0.0
                val riseLabel = FormatUtils.formatElevation(abs(rise), settings.elevationUnit)
                val dir = when {
                    rise > 0.5 -> "up"
                    rise < -0.5 -> "down"
                    else -> "level"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InspectorStat(
                        label = "AVG GRADE",
                        value = "${"%.1f".format(inspection.avgGradePercent)}%",
                        hint = dir,
                        modifier = Modifier.weight(1f)
                    )
                    InspectorStat(
                        label = "A→B",
                        value = FormatUtils.formatDistance(inspection.avgDistanceMeters, settings.distanceUnit),
                        hint = "$riseLabel $dir",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    "Tap a second point for average slope between the two.",
                    color = OnSurfaceVariant,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            inspection.routeDistanceMeters?.let {
                Text(
                    "Distance to route · ${FormatUtils.formatDistance(it, settings.distanceUnit)}",
                    color = OnSurfaceVariant,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Text(
                "DEM is 3DEP surface, not GPS altitude. Slope/aspect/3D only exist inside a downloaded terrain box.",
                color = TelemetryMuted,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun InspectorStat(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = InstrumentPod,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                label,
                color = OnSurfaceVariant,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(value, color = OnSurface, fontFamily = JetBrainsMono, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (hint.isNotEmpty()) {
                Text(hint, color = TelemetryMuted, fontFamily = JetBrainsMono, fontSize = 8.sp)
            }
        }
    }
}
