package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.location.CurrentLocationState
import com.trailmap.gps.location.LocationQuality
import com.trailmap.gps.ui.theme.AlpineShape
import com.trailmap.gps.ui.theme.GpsGreen
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.HazardRed
import com.trailmap.gps.ui.theme.InstrumentBay
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.WarningAmber

@Composable
fun GpsStatusChip(
    state: CurrentLocationState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = qualityColor(state.quality)
    val accuracy = if (state.timestamp > 0L && state.horizontalAccuracy.isFinite()) {
        "±${state.horizontalAccuracy.toInt()}m"
    } else {
        "no fix"
    }
    val age = if (state.timestamp > 0L) {
        val seconds = state.ageMs / 1000.0
        if (seconds < 1.0) "now" else String.format("%.0fs", seconds)
    } else {
        "—"
    }
    val statusText = qualityLabel(state.quality)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "GPS $statusText $accuracy $age"
        },
        color = InstrumentBay,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).background(color))
            Text(
                text = "$statusText  $accuracy  ·  $age",
                color = OnSurface,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

fun qualityColor(quality: LocationQuality): Color = when (quality) {
    LocationQuality.EXCELLENT, LocationQuality.GOOD -> GpsGreen
    LocationQuality.DEGRADED -> WarningAmber
    LocationQuality.STALE -> Color(0xFFE07A3D)
    LocationQuality.NO_FIX, LocationQuality.INVALID -> HazardRed
}

fun qualityLabel(quality: LocationQuality): String = when (quality) {
    LocationQuality.EXCELLENT -> "EXCELLENT"
    LocationQuality.GOOD -> "GOOD"
    LocationQuality.DEGRADED -> "DEGRADED"
    LocationQuality.STALE -> "STALE"
    LocationQuality.NO_FIX -> "NO FIX"
    LocationQuality.INVALID -> "INVALID"
}
