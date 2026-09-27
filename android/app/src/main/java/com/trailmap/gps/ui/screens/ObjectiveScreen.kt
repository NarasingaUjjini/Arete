package com.trailmap.gps.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.Waypoint
import com.trailmap.gps.geo.RouteSegment
import com.trailmap.gps.geo.TurnaroundPlan
import com.trailmap.gps.geo.WaypointKind
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.AlpineSectionLabel
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ObjectiveScreen(
    routeName: String,
    settings: AppSettings,
    segments: List<RouteSegment>,
    checkpoints: List<Waypoint>,
    summit: Waypoint?,
    bailouts: List<Waypoint>,
    turnaround: TurnaroundPlan?,
    returnByLabel: String,
    onCycleReturnBy: () -> Unit,
    onOpenCompass: () -> Unit,
    onOpenEmergency: () -> Unit,
    onBack: () -> Unit
) {
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
                Text("OBJECTIVE", color = com.trailmap.gps.ui.theme.Amber, fontWeight = FontWeight.SemiBold)
                Text(routeName, color = OnSurfaceVariant, fontSize = 12.sp)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            summit?.let {
                AlpineSectionLabel("Summit / high point", modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                Text(it.name, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    FormatUtils.formatElevation(it.elevation, settings.elevationUnit),
                    color = OnSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            AlpineSectionLabel("Segments", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (segments.isEmpty()) {
                Text("Import or draw a route to build segments.", color = OnSurfaceVariant, fontSize = 13.sp)
            } else {
                segments.forEach { seg ->
                    Text("${seg.fromName} → ${seg.toName}", color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${FormatUtils.formatDistance(seg.distanceMeters, settings.distanceUnit)} · +" +
                            "${FormatUtils.formatElevation(seg.gainMeters, settings.elevationUnit)} · " +
                            "max ${seg.maxGradePercent.toInt()}%",
                        color = OnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            AlpineSectionLabel("Checkpoints", modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            if (checkpoints.isEmpty()) {
                Text("No named checkpoints. Next point stays ROUTE AHEAD.", color = OnSurfaceVariant, fontSize = 13.sp)
            } else {
                checkpoints.forEach { wp ->
                    val kind = WaypointKind.from(wp.name, wp.type)
                    Text("${kind.label} · ${wp.name}", color = OnSurface, fontSize = 13.sp)
                }
            }

            AlpineSectionLabel("Bailouts / alternates", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (bailouts.isEmpty()) {
                Text(
                    "Mark a GPX waypoint as Bailout or import a separate bailout route. Arete never auto-reroutes.",
                    color = OnSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                bailouts.forEach { wp ->
                    Text(wp.name, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(wp.notes.ifBlank { wp.type }, color = OnSurfaceVariant, fontSize = 12.sp)
                }
            }

            AlpineSectionLabel("Turnaround (estimate)", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            AlpineOutlineButton("Return to trailhead by $returnByLabel", onCycleReturnBy, Modifier.fillMaxWidth())
            turnaround?.let { plan ->
                Spacer(modifier = Modifier.height(8.dp))
                Text("Current ${clock(plan.nowMs)}", color = OnSurface, fontSize = 13.sp)
                plan.sunsetMs?.let { Text("Sunset ${clock(it)}", color = OnSurface, fontSize = 13.sp) }
                Text(
                    "Remaining out ${FormatUtils.formatDistance(plan.remainingOutMeters, settings.distanceUnit)} · back ${FormatUtils.formatDistance(plan.remainingBackMeters, settings.distanceUnit)}",
                    color = OnSurfaceVariant,
                    fontSize = 12.sp
                )
                plan.paceMetersPerSecond?.let {
                    Text("Moving pace ${FormatUtils.formatSpeed(it.toFloat(), settings.distanceUnit)}", color = OnSurfaceVariant, fontSize = 12.sp)
                }
                plan.estimatedBackMs?.let {
                    Text("Est. time back ${FormatUtils.formatDuration(it / 1000)}", color = OnSurface, fontSize = 13.sp)
                }
                plan.latestTurnaroundMs?.let {
                    Text("Latest theoretical turnaround ${clock(it)}", color = TrailGreen, fontSize = 13.sp)
                }
                Text("Estimates only. Weather, terrain, and fatigue are not modeled.", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            AlpinePrimaryButton("Compass", onOpenCompass, Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            AlpineOutlineButton("Emergency reference", onOpenEmergency, Modifier.fillMaxWidth())
        }
    }
}

private fun clock(ms: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
