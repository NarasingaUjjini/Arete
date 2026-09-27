package com.trailmap.gps.ui.screens

import android.hardware.GeomagneticField
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.location.GpsUpdate
import com.trailmap.gps.ui.NavigationState
import com.trailmap.gps.ui.components.AlpineSectionLabel
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassScreen(
    location: GpsUpdate?,
    navState: NavigationState,
    summitBearing: Double?,
    magneticNorth: Boolean,
    onToggleNorth: () -> Unit,
    onBack: () -> Unit
) {
    val declination = location?.let {
        GeomagneticField(
            it.latitude.toFloat(),
            it.longitude.toFloat(),
            it.elevation.toFloat(),
            System.currentTimeMillis()
        ).declination.toDouble()
    } ?: 0.0
    val courseTrue = navState.course
    val courseShown = if (magneticNorth) (courseTrue - declination + 360.0) % 360.0 else courseTrue
    val checkShown = if (magneticNorth) (navState.bearing - declination + 360.0) % 360.0 else navState.bearing
    val summitShown = summitBearing?.let {
        if (magneticNorth) (it - declination + 360.0) % 360.0 else it
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
                Text("COMPASS", color = com.trailmap.gps.ui.theme.Amber, fontWeight = FontWeight.SemiBold)
                Text(
                    if (magneticNorth) "Magnetic north · decl ${FormatUtils.formatBearing(declination)}"
                    else "True north",
                    color = OnSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        CompassDial(
            course = courseShown,
            checkpoint = checkShown,
            summit = summitShown,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(24.dp)
        )
        AlpineSectionLabel("Readings", modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Reading("CURRENT COURSE", FormatUtils.formatBearing(courseShown))
            Reading("TO ${navState.nextLabel.uppercase().take(10)}", FormatUtils.formatBearing(checkShown))
            Reading("TO SUMMIT", summitShown?.let { FormatUtils.formatBearing(it) } ?: "—")
        }
        Text(
            "Course is GPS movement. Bearing is direction to a checkpoint or summit. They are not the same.",
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(16.dp)
        )
        TextButton(onClick = onToggleNorth) {
            Text(if (magneticNorth) "Use true north" else "Use magnetic north", color = TrailGreen)
        }
    }
}

@Composable
private fun Reading(label: String, value: String) {
    Column {
        Text(value, color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = OnSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CompassDial(course: Double, checkpoint: Double, summit: Double?, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 8f
        drawCircle(color = Color(0xFF121518), radius = r, center = c)
        drawCircle(color = Color(0xFF2F3742), radius = r, center = c, style = Stroke(width = 2f))
        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble() - 90.0)
            val inner = if (deg % 90 == 0) r - 18f else r - 10f
            drawLine(
                color = if (deg == 0) Color(0xFFFF6B00) else OnSurfaceVariant,
                start = Offset(c.x + (inner * cos(rad)).toFloat(), c.y + (inner * sin(rad)).toFloat()),
                end = Offset(c.x + (r * cos(rad)).toFloat(), c.y + (r * sin(rad)).toFloat()),
                strokeWidth = if (deg % 90 == 0) 3f else 1.5f
            )
        }
        fun needle(degrees: Double, color: Color, length: Float, width: Float) {
            rotate(degrees.toFloat(), pivot = c) {
                drawLine(
                    color = color,
                    start = c,
                    end = Offset(c.x, c.y - length),
                    strokeWidth = width,
                    cap = StrokeCap.Round
                )
            }
        }
        needle(course, Color(0xFF38BDF8), r * 0.72f, 6f)
        needle(checkpoint, Color(0xFFFF6B00), r * 0.62f, 4f)
        summit?.let { needle(it, Color(0xFFF2F4F7), r * 0.50f, 3f) }
        drawCircle(Color(0xFFFF6B00), radius = 6f, center = c)
    }
}
