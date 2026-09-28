package com.trailmap.gps.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.Waypoint
import com.trailmap.gps.terrain.DemGrid
import com.trailmap.gps.terrain.TerrainMath
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TrailGreen
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Terrain3DScreen(
    grid: DemGrid,
    routePoints: List<TrackPoint>,
    waypoints: List<Waypoint>,
    onBack: () -> Unit
) {
    var azimuth by remember { mutableFloatStateOf(35f) }
    var tilt by remember { mutableFloatStateOf(58f) }
    var exaggeration by remember { mutableFloatStateOf(1.6f) }

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
                Text("3D TERRAIN", color = LocalAccent.current, fontWeight = FontWeight.SemiBold)
                Text("${grid.source} · local mesh", color = OnSurfaceVariant, fontSize = 11.sp)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        azimuth = (azimuth + drag.x * 0.35f) % 360f
                        tilt = (tilt - drag.y * 0.2f).coerceIn(18f, 85f)
                    }
                }
        ) {
            TerrainMesh(
                grid = grid,
                routePoints = routePoints,
                waypoints = waypoints,
                azimuthDeg = azimuth,
                tiltDeg = tilt,
                exaggeration = exaggeration,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Exaggeration ${"%.1f".format(exaggeration)}×", color = OnSurfaceVariant, fontSize = 11.sp)
            Slider(
                value = exaggeration,
                onValueChange = { exaggeration = it },
                valueRange = 0.6f..3.2f
            )
            Text("Drag to rotate · offline after DEM download", color = OnSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TerrainMesh(
    grid: DemGrid,
    routePoints: List<TrackPoint>,
    waypoints: List<Waypoint>,
    azimuthDeg: Float,
    tiltDeg: Float,
    exaggeration: Float,
    modifier: Modifier = Modifier
) {
    val step = maxOf(1, maxOf(grid.cols, grid.rows) / 36)
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.58f
        val scale = minOf(size.width, size.height) * 0.9f
        val az = Math.toRadians(azimuthDeg.toDouble())
        val ti = Math.toRadians(tiltDeg.toDouble())
        val cosA = cos(az)
        val sinA = sin(az)
        val cosT = cos(ti)
        val sinT = sin(ti)
        val minE = grid.elevations.filter { !it.isNaN() }.minOrNull() ?: 0f
        val maxE = grid.elevations.filter { !it.isNaN() }.maxOrNull() ?: 1f
        val eRange = (maxE - minE).coerceAtLeast(1f)

        fun project(col: Float, row: Float, elev: Float): Triple<Float, Float, Float> {
            val x = (col / (grid.cols - 1) - 0.5f)
            val z = (row / (grid.rows - 1) - 0.5f)
            val y = ((elev - minE) / eRange) * 0.28f * exaggeration
            val xr = (x * cosA - z * sinA).toFloat()
            val zr = (x * sinA + z * cosA).toFloat()
            val yr = (y * cosT - zr * sinT).toFloat()
            val zd = (y * sinT + zr * cosT).toFloat()
            return Triple(cx + xr * scale, cy - yr * scale, zd)
        }

        data class Quad(val depth: Float, val pts: List<Offset>, val color: Color)

        val quads = ArrayList<Quad>()
        var row = 0
        while (row < grid.rows - step) {
            var col = 0
            while (col < grid.cols - step) {
                val e00 = grid.elevationAt(row, col)
                val e10 = grid.elevationAt(row, col + step)
                val e01 = grid.elevationAt(row + step, col)
                val e11 = grid.elevationAt(row + step, col + step)
                if (!e00.isNaN() && !e10.isNaN() && !e01.isNaN() && !e11.isNaN()) {
                    val p00 = project(col.toFloat(), row.toFloat(), e00)
                    val p10 = project((col + step).toFloat(), row.toFloat(), e10)
                    val p01 = project(col.toFloat(), (row + step).toFloat(), e01)
                    val p11 = project((col + step).toFloat(), (row + step).toFloat(), e11)
                    val cell = TerrainMath.analyzeCell(grid, (row + 1).coerceAtMost(grid.rows - 2), (col + 1).coerceAtMost(grid.cols - 2))
                    val shade = cell?.let {
                        TerrainMath.multidirectionalHillshade(Math.toRadians(it.slopeDegrees), it.aspectDegrees)
                    } ?: 0.55
                    val v = (40 + shade * 170).toInt()
                    val depth = (p00.third + p10.third + p01.third + p11.third) / 4f
                    quads += Quad(
                        depth,
                        listOf(Offset(p00.first, p00.second), Offset(p10.first, p10.second), Offset(p11.first, p11.second), Offset(p01.first, p01.second)),
                        Color(v, (v * 0.95).toInt(), (v * 0.8).toInt())
                    )
                }
                col += step
            }
            row += step
        }
        quads.sortBy { it.depth }
        quads.forEach { quad ->
            val path = Path().apply {
                moveTo(quad.pts[0].x, quad.pts[0].y)
                quad.pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, quad.color)
        }

        if (routePoints.size >= 2) {
            val path = Path()
            routePoints.forEachIndexed { index, point ->
                val col = ((point.lon - grid.minLon) / grid.cellSizeX).toFloat().coerceIn(0f, (grid.cols - 1).toFloat())
                val rowI = ((grid.maxLat - point.lat) / grid.cellSizeY).toFloat().coerceIn(0f, (grid.rows - 1).toFloat())
                val elev = grid.interpolate(point.lat, point.lon)?.toFloat() ?: point.elevation.toFloat()
                val p = project(col, rowI, elev)
                if (index == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second)
            }
            drawPath(path, TrailGreen, style = Stroke(width = 4f))
        }

        waypoints.forEach { wp ->
            if (!grid.contains(wp.lat, wp.lon)) return@forEach
            val col = ((wp.lon - grid.minLon) / grid.cellSizeX).toFloat()
            val rowI = ((grid.maxLat - wp.lat) / grid.cellSizeY).toFloat()
            val elev = grid.interpolate(wp.lat, wp.lon)?.toFloat() ?: wp.elevation.toFloat()
            val p = project(col, rowI, elev)
            drawCircle(TrailGreen, 6f, Offset(p.first, p.second))
        }
    }
}
