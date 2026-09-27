package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.ui.theme.TouchTarget

@Composable
fun CompassRoseButton(
    mapBearing: Double,
    northUp: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (northUp) TrailGreen else OnSurface

    Surface(
        onClick = onClick,
        modifier = modifier.size(TouchTarget),
        color = Black.copy(alpha = 0.8f),
        shape = CircleShape,
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier
                .size(TouchTarget)
                .rotate((-mapBearing).toFloat()),
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
