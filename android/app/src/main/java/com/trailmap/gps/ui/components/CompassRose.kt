package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.TouchTarget
import kotlin.math.abs

@Composable
fun CompassRoseButton(
    mapBearing: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heading = ((mapBearing % 360.0) + 360.0) % 360.0
    val aligned = heading < 2.0 || heading > 358.0
    val accent = if (aligned) LocalAccent.current else OnSurface

    Surface(
        onClick = onClick,
        modifier = modifier.size(TouchTarget),
        color = Black.copy(alpha = 0.82f),
        shape = CircleShape,
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier
                .size(TouchTarget)
                .rotate((-heading).toFloat()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(text = "▲", color = accent, fontSize = 10.sp, lineHeight = 10.sp)
            Text(
                text = "N",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = OnSurface
    val accent = LocalAccent.current
    Surface(
        onClick = onClick,
        modifier = modifier.size(TouchTarget),
        color = Black.copy(alpha = 0.82f),
        shape = CircleShape,
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f - 1.5f
                drawCircle(ink, radius = r, style = Stroke(2.2f))
                drawCircle(accent, radius = 3.2f, center = c)
                drawLine(ink, Offset(c.x, 0f), Offset(c.x, 5f), 2.2f, StrokeCap.Round)
                drawLine(ink, Offset(c.x, size.height), Offset(c.x, size.height - 5f), 2.2f, StrokeCap.Round)
                drawLine(ink, Offset(0f, c.y), Offset(5f, c.y), 2.2f, StrokeCap.Round)
                drawLine(ink, Offset(size.width, c.y), Offset(size.width - 5f, c.y), 2.2f, StrokeCap.Round)
            }
        }
    }
}
