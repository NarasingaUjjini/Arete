package com.trailmap.gps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.geo.ElevationStats
import com.trailmap.gps.location.GpsUpdate
import com.trailmap.gps.location.RecordingPhase
import com.trailmap.gps.map.TrailMapView
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.SaveRouteNameDialog
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils
import kotlinx.coroutines.delay

@Composable
fun RecordingScreen(
    phase: RecordingPhase,
    points: List<TrackPoint>,
    location: GpsUpdate?,
    settings: AppSettings,
    elapsedProvider: () -> Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    contourGeoJson: String? = null
) {
    var elapsed by remember { mutableLongStateOf(elapsedProvider()) }
    var showSave by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        while (phase == RecordingPhase.RECORDING || phase == RecordingPhase.PAUSED) {
            elapsed = elapsedProvider()
            delay(1000)
        }
        elapsed = elapsedProvider()
    }
    val stats = remember(points) { ElevationStats.calculate(points) }

    val title = when (phase) {
        RecordingPhase.READY -> "READY TO RECORD"
        RecordingPhase.RECORDING -> "RECORDING"
        RecordingPhase.PAUSED -> "PAUSED"
        RecordingPhase.STOPPED_UNSAVED -> "TRACK COMPLETE"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            color = if (phase == RecordingPhase.RECORDING) Color.Red else OnSurface,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = FormatUtils.formatDuration(elapsed),
            fontSize = 48.sp,
            color = OnSurface,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Text("TOTAL TIME", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        if (points.size >= 2) {
            Spacer(modifier = Modifier.height(16.dp))
            TrailMapView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 220.dp)
                    .height(200.dp)
                    .clip(com.trailmap.gps.ui.theme.AlpineShape),
                mapLayer = settings.mapLayer,
                hillshade = settings.hillshadeEnabled,
                contourGeoJson = contourGeoJson,
                trackPoints = points,
                currentLocation = location,
                followUser = phase == RecordingPhase.RECORDING,
                northUp = settings.northUp,
                autoFitRoute = phase != RecordingPhase.RECORDING
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RecStat(FormatUtils.formatDistance(stats.distanceMeters, settings.distanceUnit), "DISTANCE")
            RecStat(FormatUtils.formatElevation(stats.elevationGainMeters, settings.elevationUnit), "GAIN")
            RecStat(FormatUtils.formatElevation(stats.elevationLossMeters, settings.elevationUnit), "LOSS")
        }
        Spacer(modifier = Modifier.height(12.dp))
        location?.let {
            Text(
                "GPS ±${it.accuracy.toInt()}m · ${it.quality.name.lowercase()}",
                color = TrailGreen,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(36.dp))
        when (phase) {
            RecordingPhase.READY -> {
                AlpinePrimaryButton("Start Recording", onStart, Modifier.fillMaxWidth(0.8f))
                Spacer(modifier = Modifier.height(12.dp))
                AlpineOutlineButton("Back to Map", onBack, Modifier.fillMaxWidth(0.8f))
            }
            RecordingPhase.RECORDING -> {
                AlpinePrimaryButton("Pause", onPause, Modifier.fillMaxWidth(0.8f))
                Spacer(modifier = Modifier.height(12.dp))
                AlpineOutlineButton("Stop", onStop, Modifier.fillMaxWidth(0.8f))
            }
            RecordingPhase.PAUSED -> {
                AlpinePrimaryButton("Resume", onResume, Modifier.fillMaxWidth(0.8f))
                Spacer(modifier = Modifier.height(12.dp))
                AlpineOutlineButton("Stop", onStop, Modifier.fillMaxWidth(0.8f))
            }
            RecordingPhase.STOPPED_UNSAVED -> {
                AlpinePrimaryButton("Save Track", { showSave = true }, Modifier.fillMaxWidth(0.8f))
                Spacer(modifier = Modifier.height(12.dp))
                AlpineOutlineButton("Discard", onDiscard, Modifier.fillMaxWidth(0.8f))
            }
        }
    }

    if (showSave) {
        SaveRouteNameDialog(
            defaultName = "Recorded track",
            onDismiss = { showSave = false },
            onSave = {
                showSave = false
                onSave(it)
            }
        )
    }
}

@Composable
private fun RecStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}
