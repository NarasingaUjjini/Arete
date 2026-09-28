package com.trailmap.gps.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.CoordinateFormat
import com.trailmap.gps.geo.Coordinates
import com.trailmap.gps.location.CurrentLocationState
import com.trailmap.gps.location.GpsUpdate
import com.trailmap.gps.location.PositionSnapshot
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.AlpineSectionLabel
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils

@Composable
fun EmergencyScreen(
    location: GpsUpdate?,
    locationState: CurrentLocationState,
    settings: AppSettings,
    backtrackMeters: Double,
    remainingToTrailheadMeters: Double,
    position: PositionSnapshot? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lat = location?.latitude ?: locationState.lastGoodLatitude
    val lon = location?.longitude ?: locationState.lastGoodLongitude
    val text = buildShareText(lat, lon, location, locationState, settings, batteryPct(context), position)
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
                Text("EMERGENCY REFERENCE", color = LocalAccent.current, fontWeight = FontWeight.SemiBold)
                Text("Positioning tool — not a beacon", color = OnSurfaceVariant, fontSize = 11.sp)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            AlpineSectionLabel("Current position", modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            if (lat == null || lon == null) {
                Text("No usable or last-good fix.", color = OnSurfaceVariant)
            } else {
                CoordLine("LAT/LON", Coordinates.decimal(lat, lon))
                CoordLine("DMS", Coordinates.dms(lat, lon))
                CoordLine("UTM", Coordinates.utm(lat, lon))
                CoordLine("MGRS", Coordinates.mgrs(lat, lon))
            }
            location?.let {
                CoordLine("ELEVATION", FormatUtils.formatElevation(it.elevation, settings.elevationUnit))
            }
            position?.let {
                CoordLine("UNCERTAINTY", if (it.horizontalUncertaintyM.isFinite()) "±${it.horizontalUncertaintyM.toInt()} m" else "—")
                CoordLine("INTEGRITY", "${it.integrity.name} · ${it.reason}")
                CoordLine(
                    "LAST VERIFIED",
                    if (it.anchor == null || it.verifiedAgeMs > Long.MAX_VALUE / 4) "—" else "${it.verifiedAgeMs / 1000}s ago"
                )
                it.anchor?.let { anchor ->
                    CoordLine("VERIFIED POSITION", Coordinates.decimal(anchor.latitude, anchor.longitude))
                }
            } ?: location?.let {
                CoordLine("GPS ACCURACY", "±${it.accuracy.toInt()} m")
            }
            CoordLine("FIX AGE", if (locationState.ageMs > 0) "${locationState.ageMs / 1000}s" else "—")
            CoordLine("BATTERY", "${batteryPct(context)}%")
            if (locationState.lastGoodLatitude != null && location != null &&
                (locationState.lastGoodLatitude != location.latitude)
            ) {
                AlpineSectionLabel("Last good fix", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                CoordLine(
                    "POSITION",
                    Coordinates.format(
                        locationState.lastGoodLatitude,
                        locationState.lastGoodLongitude ?: 0.0,
                        settings.coordinateFormat
                    )
                )
            }
            AlpineSectionLabel("Route reference", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            CoordLine("BACK TO ROUTE", FormatUtils.formatDistance(backtrackMeters, settings.distanceUnit))
            CoordLine("TO TRAILHEAD", FormatUtils.formatDistance(remainingToTrailheadMeters, settings.distanceUnit))
            Text(
                "Uses the breadcrumb track and imported/drawn route. Arete does not transmit a distress signal.",
                color = OnSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            AlpinePrimaryButton("Share position", {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        "Share position"
                    )
                )
            }, Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            AlpineOutlineButton("Copy coordinates", {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Arete position", text))
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CoordLine(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(label, color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun batteryPct(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}

private fun buildShareText(
    lat: Double?,
    lon: Double?,
    location: GpsUpdate?,
    state: CurrentLocationState,
    settings: AppSettings,
    battery: Int,
    position: PositionSnapshot? = null
): String {
    if (lat == null || lon == null) return "Arete: no usable GPS position. Not a beacon."
    return buildString {
        appendLine("Arete position (not a beacon)")
        appendLine(Coordinates.decimal(lat, lon))
        appendLine(Coordinates.utm(lat, lon))
        appendLine(Coordinates.mgrs(lat, lon))
        location?.let {
            appendLine("Elev ${FormatUtils.formatElevation(it.elevation, settings.elevationUnit)}")
        }
        position?.let {
            if (it.horizontalUncertaintyM.isFinite()) appendLine("±${it.horizontalUncertaintyM.toInt()} m")
            appendLine("${it.integrity.name} · ${it.reason}")
            if (it.anchor != null && it.verifiedAgeMs < Long.MAX_VALUE / 4) {
                appendLine("Last verified ${it.verifiedAgeMs / 1000}s ago")
                appendLine(Coordinates.decimal(it.anchor.latitude, it.anchor.longitude))
            }
        }
        appendLine("Fix age ${state.ageMs / 1000}s · battery $battery%")
        appendLine(Coordinates.format(lat, lon, CoordinateFormat.DMS))
    }
}
