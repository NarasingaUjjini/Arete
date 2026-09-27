package com.trailmap.gps.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.CoordinateFormat
import com.trailmap.gps.geo.Coordinates
import com.trailmap.gps.location.CurrentLocationState
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpineSectionLabel
import com.trailmap.gps.ui.components.qualityColor
import com.trailmap.gps.ui.components.qualityLabel
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils

@Composable
fun GpsDiagnosticsScreen(
    state: CurrentLocationState,
    settings: AppSettings,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    val hasFix = state.timestamp > 0L
    val lat = if (hasFix) state.latitude else state.lastGoodLatitude
    val lon = if (hasFix) state.longitude else state.lastGoodLongitude

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
            Text("GPS DIAGNOSTICS", color = com.trailmap.gps.ui.theme.Amber, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                qualityLabel(state.quality).uppercase(),
                color = qualityColor(state.quality),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                if (hasFix) {
                    "±${state.horizontalAccuracy.toInt()} m · ${state.ageMs / 1000}s ago"
                } else {
                    "No usable fix"
                },
                color = OnSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            AlpineSectionLabel("Fix")
            DiagRow("Horizontal accuracy", if (hasFix && state.horizontalAccuracy.isFinite()) "±${state.horizontalAccuracy.toInt()} m" else "—")
            DiagRow(
                "Vertical accuracy",
                if (state.verticalAccuracy.isFinite()) "±${state.verticalAccuracy.toInt()} m" else "—"
            )
            DiagRow("Fix age", if (hasFix) "${state.ageMs / 1000}s" else "—")
            DiagRow("First fix", if (state.firstFixMs > 0L) "${state.firstFixMs / 1000}s after start" else "Not yet")
            DiagRow("Provider", state.provider.ifBlank { "—" })
            DiagRow("Satellites used", "${state.satellitesUsed} / ${state.satellitesVisible}")
            DiagRow(
                "Average C/N0",
                if (state.averageCn0.isFinite()) String.format("%.1f dB-Hz", state.averageCn0) else "—"
            )
            DiagRow("Constellations", state.constellations.ifBlank { "—" })

            AlpineSectionLabel("Device", modifier = Modifier.padding(top = 20.dp))
            DiagRow("Location permission", if (state.permissionGranted) "Granted" else "Denied")
            DiagRow("GPS service", if (state.gpsEnabled) "Enabled" else "Disabled")
            DiagRow(
                "Last good fix",
                if (state.lastGoodTimestamp > 0L && state.lastGoodLatitude != null && state.lastGoodLongitude != null) {
                    "${Coordinates.format(state.lastGoodLatitude, state.lastGoodLongitude, settings.coordinateFormat)} · ${FormatUtils.formatRelativeTime(state.lastGoodTimestamp)}"
                } else {
                    "None"
                }
            )

            if (lat != null && lon != null) {
                AlpineSectionLabel("Coordinates · tap to copy", modifier = Modifier.padding(top = 20.dp))
                CoordinateFormat.entries.forEach { format ->
                    val text = Coordinates.format(lat, lon, format)
                    Text(
                        "${format.name}  $text",
                        color = OnSurface,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyText(context, text)
                                copied = format.name
                            }
                            .padding(vertical = 8.dp)
                    )
                }
                if (copied != null) {
                    Text("Copied $copied", color = TrailGreen, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                AlpineOutlineButton(
                    "Share location",
                    onClick = {
                        val text = Coordinates.format(lat, lon, settings.coordinateFormat)
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                },
                                "Share location"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = OnSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("coordinates", text))
}
