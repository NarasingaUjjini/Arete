package com.trailmap.gps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.ParsedRoute
import com.trailmap.gps.map.TrailMapView
import com.trailmap.gps.ui.components.AlpineBackRow
import com.trailmap.gps.ui.components.AlpineMetadataRow
import com.trailmap.gps.ui.components.AlpineMetricCard
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.AlpineSourceBadge
import com.trailmap.gps.ui.components.AlpineSubHeader
import com.trailmap.gps.ui.components.ElevationProfile
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.Gutter
import com.trailmap.gps.ui.theme.MarginEdge
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.SectionGap
import com.trailmap.gps.ui.theme.SurfaceContainer
import com.trailmap.gps.ui.theme.SurfaceContainerLowest
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.ui.theme.TouchTarget
import com.trailmap.gps.util.FormatUtils

@Composable
fun ImportPreviewScreen(
    preview: ParsedRoute,
    settings: AppSettings,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onSaveAndDownload: () -> Unit
) {
    val distance = splitFormatted(FormatUtils.formatDistance(preview.distanceMeters, settings.distanceUnit))
    val elevation = splitFormatted(FormatUtils.formatElevation(preview.elevationGainMeters, settings.elevationUnit))
    val duration = splitFormatted(FormatUtils.formatDuration(preview.estimatedTimeSeconds))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
    ) {
        AlpineSubHeader(title = "Import Route", onBack = onBack)

        Box(modifier = Modifier.fillMaxWidth().weight(0.55f)) {
            TrailMapView(
                modifier = Modifier.fillMaxSize(),
                mapLayer = settings.mapLayer,
                routePoints = preview.points
            )
            Box(modifier = Modifier.fillMaxSize().background(Black.copy(alpha = 0.2f)))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .background(Black)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
                .verticalScroll(rememberScrollState())
        ) {
            HorizontalDivider(color = OutlineVariant)
            Column(modifier = Modifier.padding(MarginEdge)) {
                AlpineSourceBadge("Imported from GPX")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = preview.name.uppercase(),
                    color = OnSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Gutter)
                ) {
                    AlpineMetricCard("Distance", distance.first, distance.second, Modifier.weight(1f))
                    AlpineMetricCard("Elevation", elevation.first, elevation.second, Modifier.weight(1f))
                    AlpineMetricCard("Est. Time", duration.first, duration.second, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(SectionGap))
                ElevationProfile(points = preview.points)
                Spacer(modifier = Modifier.height(SectionGap))
                HorizontalDivider(color = OutlineVariant)
                Spacer(modifier = Modifier.height(16.dp))
                AlpinePrimaryButton(
                    text = "Save Route",
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                AlpineOutlineButton(text = "Save & Download Maps", onClick = onSaveAndDownload)
            }
        }
    }
}

@Composable
fun ImportPickerScreen(onBrowseFiles: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 48.dp)
            .verticalScroll(rememberScrollState())
    ) {
        AlpineBackRow(label = "Back to Routes", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = MarginEdge).padding(top = 32.dp)
        ) {
            SurfaceUploadCard(onBrowseFiles)
            Column(
                modifier = Modifier.padding(top = SectionGap),
                verticalArrangement = Arrangement.spacedBy(Gutter)
            ) {
                AlpineMetadataRow("Supported Formats", "GPX, KML, GEOJSON")
                AlpineMetadataRow("Max File Size", "25.0 MB")
                AlpineMetadataRow("Waypoint Limit", "10,000")
            }
        }
    }
}

@Composable
private fun SurfaceUploadCard(onBrowseFiles: () -> Unit) {
    Surface(
        onClick = onBrowseFiles,
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceContainer,
        shape = com.trailmap.gps.ui.theme.AlpineShape
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SectionGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(SurfaceContainerLowest, com.trailmap.gps.ui.theme.AlpineShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, tint = LocalAccent.current, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Import Route Data", color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Select a GPX, KML, or GeoJSON file. Share directly from AllTrails or tap to browse.",
                color = OnSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Surface(
                onClick = onBrowseFiles,
                modifier = Modifier.fillMaxWidth().height(TouchTarget),
                color = Color.Transparent,
                shape = com.trailmap.gps.ui.theme.AlpineShape,
                border = BorderStroke(1.dp, LocalAccent.current)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = LocalAccent.current, modifier = Modifier.size(18.dp))
                    Text(
                        "BROWSE FILES",
                        color = LocalAccent.current,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private fun splitFormatted(formatted: String): Pair<String, String> {
    val parts = formatted.trim().split(" ", limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else formatted to ""
}
