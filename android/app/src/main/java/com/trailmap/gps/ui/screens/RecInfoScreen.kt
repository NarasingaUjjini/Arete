package com.trailmap.gps.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.trailmap.gps.conditions.RecAlert
import com.trailmap.gps.conditions.RecInfoParser
import com.trailmap.gps.conditions.RecInfoSnapshot
import com.trailmap.gps.conditions.RecPlace
import com.trailmap.gps.ui.components.AlpineOutlineButton
import com.trailmap.gps.ui.components.AlpinePrimaryButton
import com.trailmap.gps.ui.components.AlpineSectionLabel
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.RedAlert
import java.util.Locale

@Composable
fun RecInfoScreen(
    snapshot: RecInfoSnapshot?,
    loading: Boolean,
    hasFix: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    fun open(url: String) {
        if (url.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
                Text("NPS / REC INFO", color = LocalAccent.current, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        loading -> "Loading official listings…"
                        snapshot?.cached == true -> "Cached · ${snapshot.radiusMiles.toInt()} mi"
                        snapshot != null -> "Within ${snapshot.radiusMiles.toInt()} mi of your fix"
                        else -> "NPS alerts · NPS campgrounds · RIDB facilities"
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
                if (loading) "Refreshing…" else "Refresh from this location",
                onRefresh,
                Modifier.fillMaxWidth(),
                enabled = !loading && hasFix
            )
            if (!hasFix) {
                Text("Need a GPS fix to search nearby.", color = RedAlert, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            snapshot?.error?.let {
                Text(it, color = RedAlert, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            AlpineSectionLabel("Alerts", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            if (snapshot?.alerts.isNullOrEmpty()) {
                Text(
                    if (snapshot?.parks.isNullOrEmpty()) "No nearby NPS units, so no park alerts to show."
                    else "No current NPS alerts for parks near this fix.",
                    color = OnSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                snapshot!!.alerts.forEach { AlertRow(it, ::open) }
            }

            AlpineSectionLabel("Campsites", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Text(
                "NPS campgrounds and RIDB camping facilities within ${RecInfoParser.RADIUS_MILES.toInt()} miles.",
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (snapshot?.campsites.isNullOrEmpty()) {
                Text("No campgrounds returned for this point.", color = OnSurfaceVariant, fontSize = 13.sp)
            } else {
                snapshot!!.campsites.forEach { PlaceRow(it, ::open) }
            }

            AlpineSectionLabel("Facilities", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Text(
                "RIDB recreation facilities near this fix — visitor centers, boat ramps, and other sites.",
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (snapshot?.facilities.isNullOrEmpty()) {
                Text("No RIDB facilities returned for this point.", color = OnSurfaceVariant, fontSize = 13.sp)
            } else {
                snapshot!!.facilities.forEach { PlaceRow(it, ::open) }
            }

            AlpineOutlineButton(
                "NPS Find a Park",
                { open("https://www.nps.gov/findapark/index.htm") },
                Modifier.fillMaxWidth().padding(top = 20.dp)
            )
            AlpineOutlineButton(
                "Recreation.gov",
                { open("https://www.recreation.gov/") },
                Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AlertRow(alert: RecAlert, open: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open(alert.url) }
            .padding(bottom = 12.dp)
    ) {
        Text(alert.category.uppercase(), color = RedAlert, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(alert.title, color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(alert.parkName, color = OnSurfaceVariant, fontSize = 12.sp)
        if (alert.description.isNotBlank()) {
            Text(alert.description, color = OnSurface, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PlaceRow(place: RecPlace, open: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open(place.url) }
            .padding(bottom = 12.dp)
    ) {
        Text(place.name, color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            listOfNotNull(
                place.source,
                place.kind,
                place.distanceMiles?.let { String.format(Locale.US, "%.0f mi", it) },
                if (place.reservable) "reservable" else null
            ).joinToString(" · "),
            color = OnSurfaceVariant,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp
        )
        if (place.summary.isNotBlank()) {
            Text(place.summary, color = OnSurface, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
