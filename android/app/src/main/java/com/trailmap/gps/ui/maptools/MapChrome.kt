package com.trailmap.gps.ui.maptools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.MapChromeLayout
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.TouchTarget

@Composable
fun BoxScope.MapChrome(
    layout: MapChromeLayout,
    field: MapFieldSnapshot,
    activeTool: MapTool?,
    isRecording: Boolean,
    onTool: (MapTool) -> Unit,
    onMore: () -> Unit,
    onRecInfo: () -> Unit
) {
    when (layout) {
        MapChromeLayout.RAIL -> RailChrome(field, activeTool, isRecording, onTool, onMore, onRecInfo)
        MapChromeLayout.SPATIAL -> SpatialChrome(field, activeTool, isRecording, onTool, onMore, onRecInfo)
        MapChromeLayout.EDGE -> EdgeChrome(field, activeTool, isRecording, onTool, onMore, onRecInfo)
    }
}

@Composable
private fun BoxScope.RailChrome(
    field: MapFieldSnapshot,
    activeTool: MapTool?,
    isRecording: Boolean,
    onTool: (MapTool) -> Unit,
    onMore: () -> Unit,
    onRecInfo: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .statusBarsPadding()
            .padding(top = 72.dp)
            .navigationBarsPadding()
            .width(48.dp)
            .fillMaxHeight()
            .background(Color(0xCC121518)),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MapTool.entries.forEach { tool ->
            ToolTick(tool.code, activeTool == tool, { onTool(tool) })
        }
        ToolTick("NPS", false, onRecInfo)
        ToolTick(if (isRecording) "REC" else "···", isRecording, onMore, alert = isRecording)
    }
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(start = 56.dp, top = 8.dp, end = 12.dp)
    ) {
        QuietReadout(field)
        MapScaleBar(field.scale, modifier = Modifier.padding(top = 4.dp), ink = Color(0xF21F2420))
    }
}

@Composable
private fun BoxScope.SpatialChrome(
    field: MapFieldSnapshot,
    activeTool: MapTool?,
    isRecording: Boolean,
    onTool: (MapTool) -> Unit,
    onMore: () -> Unit,
    onRecInfo: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(start = 12.dp, top = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpatialMark(if (open) "HIDE" else "I", selected = open) { open = !open }
            SpatialMark("NPS", selected = false, onClick = onRecInfo)
            SpatialMark(if (isRecording) "REC" else "···", selected = isRecording, alert = isRecording, onClick = onMore)
        }
        MapScaleBar(field.scale, modifier = Modifier.padding(top = 6.dp), ink = Color(0xF21F2420))
        if (open) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                MapTool.entries.forEach { tool ->
                    SpatialMark(tool.code, selected = activeTool == tool, wide = true) {
                        onTool(tool)
                        open = false
                    }
                }
                SpatialMark("NPS/REC", selected = false, wide = true) {
                    onRecInfo()
                    open = false
                }
            }
        }
    }
}

@Composable
private fun SpatialMark(
    label: String,
    selected: Boolean,
    wide: Boolean = false,
    alert: Boolean = false,
    onClick: () -> Unit
) {
    val accent = LocalAccent.current
    val color = when {
        alert -> com.trailmap.gps.ui.theme.HazardRed
        selected -> accent
        else -> OnSurface
    }
    Surface(
        onClick = onClick,
        color = Color(0xF2121518),
        border = BorderStroke(1.dp, if (selected || alert) color else Hairline),
        modifier = Modifier
            .then(if (wide) Modifier.width(72.dp) else Modifier.width(TouchTarget))
            .height(TouchTarget)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = color,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun BoxScope.EdgeChrome(
    field: MapFieldSnapshot,
    activeTool: MapTool?,
    isRecording: Boolean,
    onTool: (MapTool) -> Unit,
    onMore: () -> Unit,
    onRecInfo: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(start = 12.dp, top = 8.dp)
    ) {
        MapScaleBar(field.scale, ink = Color(0xF21F2420))
        if (activeTool == null) {
            Text(
                "${field.qualityLabel} · ${field.accuracyLabel}",
                color = Color(0xF21F2420),
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(end = 2.dp, top = 48.dp, bottom = 24.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        MapTool.entries.forEach { tool ->
            ToolTick(tool.code, activeTool == tool, { onTool(tool) }, edge = true)
        }
        ToolTick("NPS", false, onRecInfo, edge = true)
        ToolTick(if (isRecording) "REC" else "···", isRecording, onMore, edge = true, alert = isRecording)
    }
}

@Composable
private fun QuietReadout(field: MapFieldSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        field.readings.take(3).forEach { reading ->
            Column {
                Text(reading.label, color = Color(0xF21F2420), fontFamily = JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("${reading.value} ${reading.unit}".trim(), color = Color(0xF21F2420), fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ToolTick(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    wide: Boolean = false,
    edge: Boolean = false,
    alert: Boolean = false
) {
    val color = when {
        alert -> com.trailmap.gps.ui.theme.HazardRed
        selected -> LocalAccent.current
        else -> if (edge) Color(0xF21F2420) else OnSurface
    }
    Surface(
        onClick = onClick,
        color = if (wide && selected) LocalAccent.current.copy(alpha = 0.18f) else Color.Transparent,
        modifier = Modifier
            .then(if (wide) Modifier.fillMaxWidth() else Modifier.width(TouchTarget))
            .height(TouchTarget)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = color,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}
