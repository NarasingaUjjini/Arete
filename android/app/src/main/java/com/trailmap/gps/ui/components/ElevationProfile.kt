package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.trailmap.gps.geo.ElevationStats
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.SurfaceContainer
import com.trailmap.gps.ui.theme.SurfaceContainerHighest
import com.trailmap.gps.ui.theme.TrailGreen
import kotlin.math.hypot

@Composable
fun ElevationProfile(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    highlightDistanceMeters: Double? = null,
    rangeStartMeters: Double? = null,
    rangeEndMeters: Double? = null,
    demPoints: List<TrackPoint> = emptyList(),
    lineColor: Color = com.trailmap.gps.ui.theme.Amber,
    showContainer: Boolean = true,
    label: String = "ELEVATION PROFILE",
    onScrub: ((distanceMeters: Double, point: TrackPoint) -> Unit)? = null
) {
    if (points.size < 2) return
    val distances = profileDistances(points)
    val maxDist = distances.last().coerceAtLeast(1.0)

    val chartContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onScrub != null) {
                        Modifier
                            .pointerInput(points, maxDist) {
                                detectTapGestures { tap ->
                                    val t = (tap.x / size.width).coerceIn(0f, 1f)
                                    val dist = t * maxDist
                                    val point = ElevationStats.pointAtDistance(points, dist) ?: return@detectTapGestures
                                    onScrub(dist, point)
                                }
                            }
                            .pointerInput(points, maxDist) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val t = (start.x / size.width).coerceIn(0f, 1f)
                                        val dist = t * maxDist
                                        val point = ElevationStats.pointAtDistance(points, dist) ?: return@detectDragGestures
                                        onScrub(dist, point)
                                    },
                                    onDrag = { change, _ ->
                                        val t = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val dist = t * maxDist
                                        val point = ElevationStats.pointAtDistance(points, dist) ?: return@detectDragGestures
                                        onScrub(dist, point)
                                    }
                                )
                            }
                    } else Modifier
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val elevations = points.map { it.elevation }
                val minEle = elevations.minOrNull() ?: 0.0
                val maxEle = elevations.maxOrNull() ?: 1.0
                val range = (maxEle - minEle).coerceAtLeast(1.0)
                val padTop = size.height * 0.08f
                val padBottom = size.height * 0.08f
                val chartHeight = size.height - padTop - padBottom

                for (i in 1..3) {
                    val y = padTop + chartHeight * i / 4f
                    drawLine(SurfaceContainerHighest, Offset(0f, y), Offset(size.width, y), 1f)
                }

                if (rangeStartMeters != null && rangeEndMeters != null) {
                    val x0 = (rangeStartMeters / maxDist).toFloat().coerceIn(0f, 1f) * size.width
                    val x1 = (rangeEndMeters / maxDist).toFloat().coerceIn(0f, 1f) * size.width
                    drawRect(
                        color = TrailGreen.copy(alpha = 0.12f),
                        topLeft = Offset(minOf(x0, x1), padTop),
                        size = androidx.compose.ui.geometry.Size(kotlin.math.abs(x1 - x0), chartHeight)
                    )
                }

                fun plot(src: List<TrackPoint>, dists: List<Double>, color: Color, stroke: Float) {
                    if (src.size < 2) return
                    val path = Path()
                    val fill = Path()
                    src.forEachIndexed { index, point ->
                        val x = (dists.getOrElse(index) { 0.0 } / maxDist).toFloat() * size.width
                        val y = padTop + chartHeight - ((point.elevation - minEle) / range * chartHeight).toFloat()
                        if (index == 0) {
                            path.moveTo(x, y)
                            fill.moveTo(x, size.height)
                            fill.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fill.lineTo(x, y)
                        }
                    }
                    fill.lineTo(size.width, size.height)
                    fill.close()
                    drawPath(fill, color.copy(alpha = 0.18f))
                    drawPath(path, color, style = Stroke(width = stroke))
                }

                plot(points, distances, lineColor, 2f)
                if (demPoints.size >= 2) {
                    plot(demPoints, profileDistances(demPoints), Color(0xFF7DD3FC), 1.5f)
                }

                val markDist = highlightDistanceMeters
                    ?: highlightIndex?.let { distances.getOrNull(it) }
                if (markDist != null) {
                    val point = ElevationStats.pointAtDistance(points, markDist) ?: points.first()
                    val x = (markDist / maxDist).toFloat() * size.width
                    val y = padTop + chartHeight - ((point.elevation - minEle) / range * chartHeight).toFloat()
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(x, padTop), Offset(x, size.height - padBottom), 1f)
                    drawCircle(Color.White, 4f, Offset(x, y))
                    drawCircle(lineColor, 2.5f, Offset(x, y))
                }
            }
        }
    }

    if (showContainer) {
        Column(modifier = modifier) {
            Text(
                text = label,
                color = OnSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Surface(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                color = SurfaceContainer,
                shape = com.trailmap.gps.ui.theme.AlpineShape,
                border = BorderStroke(1.dp, OutlineVariant)
            ) {
                Box(modifier = Modifier.padding(8.dp)) { chartContent() }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxWidth().height(120.dp)) { chartContent() }
    }
}

@Composable
fun ElevationProfileLarge(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    highlightDistanceMeters: Double? = null,
    rangeStartMeters: Double? = null,
    rangeEndMeters: Double? = null,
    demPoints: List<TrackPoint> = emptyList(),
    onScrub: ((distanceMeters: Double, point: TrackPoint) -> Unit)? = null
) {
    if (points.size < 2) return
    Column(modifier = modifier) {
        Text(
            text = "ELEVATION PROFILE",
            color = OnSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            color = com.trailmap.gps.ui.theme.SurfaceContainerLow,
            shape = com.trailmap.gps.ui.theme.AlpineShape,
            border = BorderStroke(1.dp, OutlineVariant)
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                ElevationProfile(
                    points = points,
                    highlightIndex = highlightIndex,
                    highlightDistanceMeters = highlightDistanceMeters,
                    rangeStartMeters = rangeStartMeters,
                    rangeEndMeters = rangeEndMeters,
                    demPoints = demPoints,
                    showContainer = false,
                    onScrub = onScrub
                )
            }
        }
    }
}

internal fun profileDistances(points: List<TrackPoint>): List<Double> {
    if (points.last().cumulativeDistanceMeters > 0.0) return points.map { it.cumulativeDistanceMeters }
    val acc = mutableListOf(0.0)
    for (i in 1 until points.size) {
        acc += acc.last() + hypot(points[i].lat - points[i - 1].lat, points[i].lon - points[i - 1].lon)
    }
    return acc
}
