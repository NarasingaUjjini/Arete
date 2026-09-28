package com.trailmap.gps.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.conditions.AvalancheDirectory
import com.trailmap.gps.conditions.DaylightTimes
import com.trailmap.gps.conditions.MountainConditions
import com.trailmap.gps.conditions.NwsPointForecast
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.AlpineSectionLabel
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.RedAlert
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConditionsScreen(
    conditions: MountainConditions?,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
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
            Column(modifier = Modifier.weight(1f)) {
                Text("MOUNTAIN CONDITIONS", color = LocalAccent.current, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        loading -> "Loading official sources…"
                        conditions?.cached == true -> "Cached · offline"
                        else -> "NWS · NIFC · PAD-US · USGS"
                    },
                    color = OnSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            AlpinePrimaryButton(
                if (loading) "Refreshing…" else "Refresh conditions",
                onRefresh,
                Modifier.fillMaxWidth(),
                enabled = !loading
            )
            conditions?.error?.let {
                Text(it, color = RedAlert, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            conditions?.daylight?.let { DaylightBlock(it) }

            if (!conditions?.weather.isNullOrEmpty()) {
                AlpineSectionLabel("NWS forecast", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                conditions!!.weather.forEach { card ->
                    WeatherCard(card)
                }
                conditions.weather.firstOrNull { it.hourly.size >= 4 }?.let { HourlyGraph(it) }
            }

            val alerts = conditions?.weather.orEmpty().flatMap { it.alerts }.distinctBy { it.headline }
            if (alerts.isNotEmpty()) {
                AlpineSectionLabel("Alerts", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                alerts.forEach { alert ->
                    Text(alert.event, color = RedAlert, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(alert.headline, color = OnSurface, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }

            AlpineSectionLabel("Wildfire (NIFC)", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (conditions?.fires.isNullOrEmpty()) {
                Text("No current incidents within 80 km, or data unavailable.", color = OnSurfaceVariant, fontSize = 13.sp)
            } else {
                conditions!!.fires.forEach { fire ->
                    Text(fire.name, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            fire.acres?.let { "${it.toInt()} acres" },
                            fire.contained?.let { "${it.toInt()}% contained" },
                            fire.updated.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = OnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Text("A perimeter is not a guarantee that an area is safe to enter.", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }

            AlpineSectionLabel("Public land (PAD-US)", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            val land = conditions?.land
            if (land == null) {
                Text("No PAD-US unit at this point, or the service is unavailable.", color = OnSurfaceVariant, fontSize = 13.sp)
                Text("USGS PAD-US", color = TrailGreen, fontSize = 13.sp, modifier = Modifier.clickable { open("https://www.usgs.gov/programs/gap-analysis-project/science/pad-us-data-overview") }.padding(top = 4.dp))
            } else {
                Text(land.name, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text("${land.manager} · ${land.designation}", color = OnSurfaceVariant, fontSize = 12.sp)
                Text("Source: PAD-US — boundaries are only as precise as the dataset.", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }

            AlpineSectionLabel("Water (USGS NHD)", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (conditions?.water.isNullOrEmpty()) {
                Text("No mapped water within ~400 m.", color = OnSurfaceVariant, fontSize = 13.sp)
            } else {
                conditions!!.water.forEach { w ->
                    Text("${w.name} · ${w.type}", color = OnSurface, fontSize = 13.sp)
                }
                Text("Mapped water is not a flow or passability report.", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }

            AlpineSectionLabel("Avalanche — official source", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            val avy = conditions?.avalanche
            if (avy != null) {
                Text(avy.centerName, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(avy.region, color = OnSurfaceVariant, fontSize = 12.sp)
                Text(avy.note, color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                AlpineOutlineButton("Open official forecast", { open(avy.url) }, Modifier.fillMaxWidth())
            }
            Text(
                "Avalanche.org centers",
                color = TrailGreen,
                fontSize = 13.sp,
                modifier = Modifier.clickable { open(AvalancheDirectory.INDEX_URL) }.padding(top = 8.dp)
            )

            AlpineSectionLabel("Parks & recreation", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Text(conditions?.recreation?.note ?: "", color = OnSurfaceVariant, fontSize = 12.sp)
            conditions?.recreation?.nearby.orEmpty().forEach { place ->
                Text(
                    listOf(place.name, place.type, place.distanceNote).filter { it.isNotBlank() }.joinToString(" · "),
                    color = OnSurface,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { open(place.url) }
                        .padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AlpineOutlineButton("NPS Find a Park", { open(conditions?.recreation?.npsFindParkUrl ?: "https://www.nps.gov/findapark/index.htm") }, Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            AlpineOutlineButton("Recreation.gov", { open(conditions?.recreation?.recreationGovUrl ?: "https://www.recreation.gov/") }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DaylightBlock(times: DaylightTimes) {
    AlpineSectionLabel("Daylight (local calculation)", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayCell("SUNRISE", formatTime(times.sunriseMs))
        DayCell("SUNSET", formatTime(times.sunsetMs))
        DayCell("CIVIL DUSK", formatTime(times.civilDuskMs))
    }
    times.remainingUntilSunset(System.currentTimeMillis())?.let { rem ->
        val text = if (rem >= 0) "Daylight remaining ${FormatUtils.formatDuration(rem / 1000)}" else "After sunset"
        Text(text, color = TrailGreen, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
    Text("Computed on-device from coordinates and date. No API.", color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun DayCell(label: String, value: String) {
    Column {
        Text(value, color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = OnSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeatherCard(forecast: NwsPointForecast) {
    val period = forecast.periods.firstOrNull()
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(forecast.label, color = TrailGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(
            period?.temperatureF?.let { "$it°F" } ?: "—",
            color = OnSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            listOfNotNull(period?.wind?.takeIf { it.isNotBlank() }?.let { "Wind $it" }, period?.gust?.takeIf { it.isNotBlank() }?.let { "Gust $it" }).joinToString(" · "),
            color = OnSurface,
            fontSize = 13.sp
        )
        Text(period?.shortForecast.orEmpty(), color = OnSurfaceVariant, fontSize = 13.sp)
        val age = if (forecast.isStale()) "STALE · " else ""
        Text(
            "${age}NWS ${forecast.office} · updated ${forecast.updatedAt.ifBlank { forecast.generatedAt }.ifBlank { FormatUtils.formatRelativeTime(forecast.savedAt) }}",
            color = if (forecast.isStale()) RedAlert else OnSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (forecast.cachedOrOfflineHint()) {
            Text("Saved forecast — not a live observation.", color = OnSurfaceVariant, fontSize = 11.sp)
        }
    }
}

private fun NwsPointForecast.cachedOrOfflineHint(): Boolean = periods.firstOrNull()?.name == "Cached"

@Composable
private fun HourlyGraph(forecast: NwsPointForecast) {
    val temps = forecast.hourly.mapNotNull { it.temperatureF }
    if (temps.size < 4) return
    AlpineSectionLabel("Temperature / wind (hourly)", modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val minT = temps.min().toFloat()
        val maxT = temps.max().toFloat().coerceAtLeast(minT + 1f)
        val path = Path()
        forecast.hourly.forEachIndexed { i, hour ->
            val t = hour.temperatureF ?: return@forEachIndexed
            val x = size.width * i / (forecast.hourly.size - 1).coerceAtLeast(1)
            val y = size.height - ((t - minT) / (maxT - minT)) * size.height
            if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, TrailGreen, style = Stroke(width = 3f))
        forecast.hourly.forEachIndexed { i, hour ->
            val wind = hour.windMph ?: return@forEachIndexed
            val x = size.width * i / (forecast.hourly.size - 1).coerceAtLeast(1)
            drawCircle(
                color = TrailGreen.copy(alpha = 0.35f),
                radius = (2.0 + wind / 8.0).coerceAtMost(8.0).toFloat(),
                center = Offset(x, 6f)
            )
        }
    }
    Text("Line = temperature · dots = wind. Source NWS hourly.", color = OnSurfaceVariant, fontSize = 11.sp)
}

private fun formatTime(ms: Long?): String {
    if (ms == null) return "—"
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
}
