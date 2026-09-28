package com.trailmap.gps.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.RouteEntity
import com.trailmap.gps.data.RouteSource
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.map.TrailMapView
import com.trailmap.gps.ui.components.AlpineDragHandle
import com.trailmap.gps.ui.components.AlpineIconButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.AlpineSourceBadge
import com.trailmap.gps.ui.components.AlpineStatTile
import com.trailmap.gps.ui.components.ElevationProfileLarge
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.Gutter
import com.trailmap.gps.ui.theme.MarginEdge
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.RedAlert
import com.trailmap.gps.ui.theme.SectionGap
import com.trailmap.gps.ui.theme.SurfaceContainer
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RouteDetailScreen(
    route: RouteEntity,
    points: List<TrackPoint>,
    waypoints: List<com.trailmap.gps.data.Waypoint> = emptyList(),
    settings: AppSettings,
    onBack: () -> Unit,
    onNavigate: () -> Unit,
    onDownload: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    profileDistanceMeters: Double? = null,
    sectionStartMeters: Double? = null,
    sectionEndMeters: Double? = null,
    demPoints: List<TrackPoint> = emptyList(),
    hasLocalDem: Boolean = false,
    demDownloading: Boolean = false,
    onScrubProfile: (Double, TrackPoint) -> Unit = { _, _ -> },
    onMarkSectionStart: () -> Unit = {},
    onMarkSectionEnd: () -> Unit = {},
    onClearSection: () -> Unit = {},
    onDownloadTerrain: () -> Unit = {},
    onOpen3d: () -> Unit = {},
    onOpenConditions: () -> Unit = {},
    packStatus: com.trailmap.gps.data.TripPackStatus? = null,
    packBytes: Long = 0,
    contourGeoJson: String? = null,
    onOpenObjective: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
    ) {
        TrailMapView(
            modifier = Modifier.fillMaxSize(),
            mapLayer = settings.mapLayer,
            contourGeoJson = contourGeoJson,
            routePoints = points,
            fitRouteTrigger = 1
        )
        Box(modifier = Modifier.fillMaxSize().background(Black.copy(alpha = 0.15f)))

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxSize(0.65f)
                .clip(com.trailmap.gps.ui.theme.AlpineShape)
                .background(SurfaceContainer)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlpineDragHandle()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MarginEdge)
            ) {
                Text(
                    text = route.name,
                    color = OnSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AlpineSourceBadge(
                        when (route.source) {
                            RouteSource.IMPORTED -> "Imported from GPX"
                            RouteSource.RECORDED -> "Recorded track"
                            RouteSource.DRAWN -> "Drawn route"
                        }
                    )
                    val status = packStatus
                    if (status != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (status == com.trailmap.gps.data.TripPackStatus.READY) TrailGreen else OnSurfaceVariant, modifier = Modifier.height(14.dp))
                            Text(
                                when (status) {
                                    com.trailmap.gps.data.TripPackStatus.READY -> "TRIP PACK READY"
                                    com.trailmap.gps.data.TripPackStatus.PARTIAL -> "TRIP PACK PARTIAL"
                                    com.trailmap.gps.data.TripPackStatus.STALE -> "TRIP PACK STALE"
                                    com.trailmap.gps.data.TripPackStatus.FAILED -> "TRIP PACK FAILED"
                                    com.trailmap.gps.data.TripPackStatus.DOWNLOADING -> "DOWNLOADING"
                                    com.trailmap.gps.data.TripPackStatus.PAUSED -> "PACK PAUSED"
                                    else -> "TRIP PACK"
                                },
                                color = if (status == com.trailmap.gps.data.TripPackStatus.READY) TrailGreen else OnSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else if (route.offlineDownloaded) {
                        Text("MAPS SAVED", color = TrailGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(SectionGap))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gutter)) {
                    AlpineStatTile(formatStatValue(FormatUtils.formatDistance(route.distanceMeters, settings.distanceUnit)), FormatUtils.formatDistance(route.distanceMeters, settings.distanceUnit).substringAfter(" "), Modifier.weight(1f))
                    AlpineStatTile(formatStatValue(FormatUtils.formatElevation(route.elevationGainMeters, settings.elevationUnit)), "gain", Modifier.weight(1f))
                    AlpineStatTile(formatStatValue(FormatUtils.formatElevation(route.elevationLossMeters, settings.elevationUnit)), "loss", Modifier.weight(1f))
                    AlpineStatTile(formatStatValue(FormatUtils.formatElevation(route.maxElevationMeters, settings.elevationUnit)), "max", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(SectionGap))
                ElevationProfileLarge(
                    points = points,
                    highlightDistanceMeters = profileDistanceMeters,
                    rangeStartMeters = sectionStartMeters,
                    rangeEndMeters = sectionEndMeters,
                    demPoints = demPoints,
                    onScrub = onScrubProfile
                )
                val scrubPoint = profileDistanceMeters?.let { com.trailmap.gps.geo.ElevationStats.pointAtDistance(points, it) }
                if (scrubPoint != null) {
                    val span = if (sectionStartMeters != null && sectionEndMeters != null) {
                        com.trailmap.gps.geo.ElevationStats.slice(points, sectionStartMeters, sectionEndMeters)
                    } else null
                    Text(
                        "At ${FormatUtils.formatDistance(scrubPoint.cumulativeDistanceMeters, settings.distanceUnit)} · " +
                            FormatUtils.formatElevation(scrubPoint.elevation, settings.elevationUnit) +
                            (span?.let {
                                " · section ${FormatUtils.formatDistance(it.distanceMeters, settings.distanceUnit)} · max grade ${it.maxGradePercent.toInt()}%"
                            } ?: ""),
                        color = OnSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Text("A", color = TrailGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onMarkSectionStart))
                        Text("B", color = TrailGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onMarkSectionEnd))
                        Text("Clear", color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClearSection))
                    }
                }
                Text(
                    when {
                        demDownloading -> "Downloading USGS 3DEP…"
                        hasLocalDem -> "Local 3DEP loaded · cyan line is terrain, green is the route file"
                        else -> "No local 3DEP in this area — slope, aspect, contours, and 3D only work after you download terrain for a map box"
                    },
                    color = if (hasLocalDem) TrailGreen else OnSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        if (demDownloading) "TERRAIN…" else "DOWNLOAD TERRAIN",
                        color = TrailGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !demDownloading, onClick = onDownloadTerrain)
                    )
                    if (hasLocalDem) {
                        Text(
                            "3D TERRAIN",
                            color = TrailGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onOpen3d)
                        )
                    }
                    Text(
                        "CONDITIONS",
                        color = TrailGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onOpenConditions)
                    )
                    Text(
                        "OBJECTIVE",
                        color = TrailGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onOpenObjective)
                    )
                }
                if (waypoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("CHECKPOINTS", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    waypoints.take(12).forEach { wp ->
                        Text("${wp.name} · ${wp.type}", color = OnSurface, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (packStatus != null || route.offlineDownloaded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Trip pack · ${FormatUtils.formatFileSize(if (packBytes > 0) packBytes else route.offlineSizeBytes)}",
                        color = if (packStatus == com.trailmap.gps.data.TripPackStatus.READY) TrailGreen else OnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    val gpsLine = if (settings.gnssAssistanceUpdatedAt <= 0L) {
                        "GPS assistance not downloaded — use Settings or re-download this area"
                    } else if (FormatUtils.isStale(settings.gnssAssistanceUpdatedAt)) {
                        "GPS assistance is stale — refresh before you leave signal"
                    } else {
                        "GPS assistance · ${FormatUtils.formatRelativeTime(settings.gnssAssistanceUpdatedAt)}"
                    }
                    Text(gpsLine, color = if (FormatUtils.isStale(settings.gnssAssistanceUpdatedAt)) OnSurfaceVariant else TrailGreen, fontSize = 12.sp)
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No offline map pack — download this area before you lose signal", color = OnSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(88.dp))
            }
            HorizontalDivider(color = OutlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = MarginEdge, vertical = 16.dp)
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(Gutter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlpinePrimaryButton(text = "Navigate", onClick = onNavigate, modifier = Modifier.weight(1f))
                AlpineIconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = TrailGreen)
                }
                AlpineIconButton(onClick = onExport) {
                    Icon(Icons.Default.Share, contentDescription = "Export GPX", tint = TrailGreen)
                }
                AlpineIconButton(onClick = onDelete, borderColor = RedAlert) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedAlert)
                }
            }
        }
    }
}

private fun formatStatValue(formatted: String): String = formatted.split(" ").firstOrNull() ?: formatted

fun formatRouteDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestamp))

fun sourceBadge(source: RouteSource): String = when (source) {
    RouteSource.IMPORTED -> "GPX"
    RouteSource.RECORDED -> "REC"
    RouteSource.DRAWN -> "Drawn"
}
