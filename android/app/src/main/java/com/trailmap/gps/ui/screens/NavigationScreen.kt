package com.trailmap.gps.ui.screens



import androidx.compose.foundation.BorderStroke
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

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.BatterySaver

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.trailmap.gps.data.AppSettings

import com.trailmap.gps.data.RouteEntity

import com.trailmap.gps.data.TrackPoint

import com.trailmap.gps.conditions.Daylight
import com.trailmap.gps.location.CurrentLocationState

import com.trailmap.gps.location.GpsUpdate

import com.trailmap.gps.map.TrailMapView

import com.trailmap.gps.ui.NavigationState

import com.trailmap.gps.ui.components.GpsStatusChip

import com.trailmap.gps.ui.components.RouteProgressBar

import com.trailmap.gps.ui.components.StatBlock

import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.LocalAccent

import com.trailmap.gps.ui.theme.OnSurface

import com.trailmap.gps.ui.theme.OnSurfaceVariant

import com.trailmap.gps.ui.theme.OutlineVariant

import com.trailmap.gps.ui.theme.RedAlert

import com.trailmap.gps.ui.theme.TrailGreen

import com.trailmap.gps.util.FormatUtils



@Composable

fun NavigationScreen(

    route: RouteEntity,

    routePoints: List<TrackPoint>,

    settings: AppSettings,

    location: GpsUpdate?,

    locationState: CurrentLocationState,

    navState: NavigationState,

    breadcrumbs: List<TrackPoint>,

    batterySaver: Boolean,

    elapsedSeconds: Long,

    onExit: () -> Unit,

    onToggleBatterySaver: () -> Unit,

    onGpsClick: () -> Unit,

    verticalMetersPerHour: Double? = null,
    contourGeoJson: String? = null,
    onOpenEmergency: () -> Unit = {},
    onOpenCompass: () -> Unit = {},
    turnaroundLabel: String? = null

) {

    Box(modifier = Modifier.fillMaxSize().background(Black)) {

        if (batterySaver) {

            BatterySaverView(
                routePoints = routePoints,
                navState = navState,
                settings = settings,
                location = location,
                elapsedSeconds = elapsedSeconds,
                onToggleBatterySaver = onToggleBatterySaver
            )

        } else {

            TrailMapView(

                modifier = Modifier.fillMaxSize(),

                mapLayer = settings.mapLayer,

                hillshade = settings.hillshadeEnabled,
                contourGeoJson = contourGeoJson,

                routePoints = routePoints,

                trackPoints = breadcrumbs,

                currentLocation = location,

                northUp = settings.northUp,

                followUser = true

            )



            Surface(

                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),

                color = Black.copy(alpha = 0.85f)

            ) {

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .statusBarsPadding()

                        .padding(horizontal = 4.dp, vertical = 4.dp),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Surface(

                        onClick = onExit,

                        color = Color.Transparent

                    ) {

                        Text(

                            "← EXIT",

                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),

                            color = OnSurface,

                            fontSize = 12.sp,

                            fontWeight = FontWeight.SemiBold,

                            letterSpacing = 0.5.sp

                        )

                    }

                    Text(

                        text = route.name.uppercase(),

                        color = OnSurface,

                        fontSize = 14.sp,

                        fontWeight = FontWeight.Medium,

                        maxLines = 1,

                        overflow = TextOverflow.Ellipsis,

                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)

                    )

                    GpsStatusChip(
                        state = locationState,
                        onClick = onGpsClick,
                        integrityLabel = location?.integrityLabel.orEmpty(),
                        uncertaintyLabel = location?.let { "±${it.accuracy.toInt()}m" }.orEmpty()
                    )

                    Text(
                        "SOS",
                        color = com.trailmap.gps.ui.theme.HazardRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onOpenEmergency)
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                    Text(
                        "CMP",
                        color = LocalAccent.current,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onOpenCompass)
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    )

                    IconButton(onClick = onToggleBatterySaver) {

                        Icon(Icons.Default.BatterySaver, contentDescription = "Battery saver", tint = OnSurfaceVariant)

                    }

                }

            }



            val banner = when {

                navState.gpsDegraded -> "GPS ACCURACY DEGRADED — OFF-ROUTE NOT CONFIRMED"

                navState.isOffRoute -> "OFF ROUTE"

                location != null && location.quality == com.trailmap.gps.location.LocationQuality.STALE ->

                    "GPS SIGNAL STALE · LAST FIX ${location.ageMs / 1000}s AGO"

                else -> null

            }

            if (banner != null) {

                Surface(

                    modifier = Modifier

                        .align(Alignment.TopCenter)

                        .statusBarsPadding()

                        .padding(top = 52.dp)

                        .fillMaxWidth(),

                    color = if (navState.isOffRoute) RedAlert else Color(0xFF8A6D1B)

                ) {

                    Text(

                        text = banner,

                        modifier = Modifier.padding(12.dp),

                        color = Color.White,

                        fontWeight = FontWeight.Bold,

                        fontSize = 12.sp,

                        letterSpacing = 0.5.sp

                    )

                }

            }



            Surface(

                modifier = Modifier.align(Alignment.BottomCenter),

                color = Black,

                border = BorderStroke(1.dp, OutlineVariant)

            ) {

                Column(

                    modifier = Modifier

                        .fillMaxWidth()

                        .navigationBarsPadding()

                        .padding(top = 12.dp, bottom = 12.dp)

                ) {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceEvenly

                    ) {

                        StatBlock(

                            value = FormatUtils.formatDistance(navState.remainingDistance, settings.distanceUnit),

                            label = "REMAINING"

                        )

                        StatBlock(

                            value = FormatUtils.formatElevation(navState.remainingAscent, settings.elevationUnit),

                            label = "ASCENT LEFT"

                        )

                        StatBlock(

                            value = FormatUtils.formatBearing(navState.bearing),

                            label = "TO ${navState.nextLabel.uppercase().take(12)}"

                        )

                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    RouteProgressBar(progress = navState.progress)

                    Text(

                        text = "${FormatUtils.formatDistance(navState.distanceAlongRoute, settings.distanceUnit)} / " +

                            "${FormatUtils.formatDistance(navState.totalDistance, settings.distanceUnit)}  ·  " +

                            "${(navState.progress * 100).toInt()}%  ·  " +

                            "course ${FormatUtils.formatBearing(navState.course)}",

                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),

                        color = OnSurfaceVariant,

                        fontSize = 12.sp

                    )

                    Text(

                        text = "${navState.nextLabel} · ${FormatUtils.formatDuration(elapsedSeconds)} elapsed" +

                            (location?.let { loc ->
                                val sun = Daylight.calculate(loc.latitude, loc.longitude)
                                val rem = sun.remainingUntilSunset(System.currentTimeMillis())
                                val sunset = sun.sunsetMs?.let {
                                    java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(it))
                                }
                                if (sunset != null && rem != null) {
                                    " · SUNSET $sunset" + if (rem > 0) " ${FormatUtils.formatDuration(rem / 1000)}" else " passed"
                                } else ""
                            } ?: "") +
                            (turnaroundLabel?.let { " · $it" } ?: "") +

                            (verticalMetersPerHour?.let { v ->
                                val signed = if (v >= 0) "+" else ""
                                " · VERTICAL $signed${FormatUtils.formatElevation(kotlin.math.abs(v), settings.elevationUnit)}/hr"
                            } ?: "") +

                            (location?.let { " · ${FormatUtils.formatCoordinates(it.latitude, it.longitude, settings.coordinateFormat)} · ±${it.accuracy.toInt()}m" } ?: ""),

                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),

                        color = OnSurfaceVariant,

                        fontSize = 11.sp

                    )

                }

            }

        }

    }

}



@Composable
private fun BatterySaverView(
    routePoints: List<TrackPoint>,
    navState: NavigationState,
    settings: AppSettings,
    location: GpsUpdate?,
    elapsedSeconds: Long,
    onToggleBatterySaver: () -> Unit
) {
    val number = if (settings.largeNumbers) 56.sp else 48.sp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .clickable(onClick = onToggleBatterySaver)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ARETE", color = LocalAccent.current, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("NAVIGATION", color = OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                FormatUtils.formatDistance(navState.remainingDistance, settings.distanceUnit),
                fontSize = number,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Text("REMAINING", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "+${FormatUtils.formatElevation(navState.remainingAscent, settings.elevationUnit)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Text(FormatUtils.formatBearing(navState.bearing), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                location?.let { "GPS ±${it.accuracy.toInt()}m" } ?: "GPS no usable fix",
                color = OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (navState.isOffRoute) {
                Text("OFF ROUTE", color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text("TAP FOR MAP", color = LocalAccent.current, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}


