package com.trailmap.gps.map

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.trailmap.gps.data.MapLayer
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.geo.GeoMath
import com.trailmap.gps.location.GpsUpdate
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("MissingPermission")
@Composable
fun TrailMapView(
    modifier: Modifier = Modifier,
    mapLayer: MapLayer = MapLayer.ARETE_TOPO,
    hillshade: Boolean = false,
    contourGeoJson: String? = null,
    routeColorHex: String = "#FF6B00",
    overlayOpacity: Float = 0.48f,
    batterySaver: Boolean = false,
    routePoints: List<TrackPoint> = emptyList(),
    drawPoints: List<TrackPoint> = emptyList(),
    trackPoints: List<TrackPoint> = emptyList(),
    currentLocation: GpsUpdate? = null,
    northUp: Boolean = true,
    followUser: Boolean = false,
    recenterTrigger: Int = 0,
    fitRouteTrigger: Int = 0,
    orientationTrigger: Int = 0,
    autoFitRoute: Boolean = true,
    regionInsetDp: Dp? = null,
    onRegionBoundsChanged: ((DoubleArray) -> Unit)? = null,
    onMapClick: ((lat: Double, lon: Double) -> Unit)? = null,
    onMapLongClick: ((lat: Double, lon: Double) -> Unit)? = null,
    inspectPoint: Pair<Double, Double>? = null,
    terrainOverlay: Bitmap? = null,
    terrainOverlayBounds: BoundingBox? = null,
    onMapReady: (MapLibreMap) -> Unit = {},
    onBearingChanged: (Double) -> Unit = {},
    onScaleChanged: (zoom: Double, latitude: Double) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    var lastFitTrigger by remember { mutableIntStateOf(-1) }
    var lastRecenterTrigger by remember { mutableIntStateOf(-1) }
    var lastOrientationTrigger by remember { mutableIntStateOf(-1) }
    val clickHandlerState = remember { mutableStateOf(onMapClick) }
    val currentClickHandler by rememberUpdatedState(onMapClick)
    clickHandlerState.value = currentClickHandler
    val longClickHandlerState = remember { mutableStateOf(onMapLongClick) }
    longClickHandlerState.value = onMapLongClick
    val regionInsetState = remember { mutableStateOf(regionInsetDp) }
    val boundsCallbackState = remember { mutableStateOf(onRegionBoundsChanged) }
    regionInsetState.value = regionInsetDp
    boundsCallbackState.value = onRegionBoundsChanged
    val density = LocalDensity.current
    val scaleHandler = rememberUpdatedState(onScaleChanged)

    fun reportScale(map: MapLibreMap) {
        val target = map.cameraPosition.target ?: return
        scaleHandler.value(map.cameraPosition.zoom, target.latitude)
    }

    fun reportRegionBounds(map: MapLibreMap, view: MapView) {
        val inset = regionInsetState.value ?: return
        val callback = boundsCallbackState.value ?: return
        if (view.width <= 0 || view.height <= 0) return
        val insetPx = with(density) { inset.toPx() }
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val sw = map.projection.fromScreenLocation(PointF(insetPx, h - insetPx))
        val ne = map.projection.fromScreenLocation(PointF(w - insetPx, insetPx))
        callback(
            doubleArrayOf(
                minOf(sw.longitude, ne.longitude),
                minOf(sw.latitude, ne.latitude),
                maxOf(sw.longitude, ne.longitude),
                maxOf(sw.latitude, ne.latitude)
            )
        )
    }

    val styleJson = if (batterySaver) {
        MapStyles.BATTERY_SAVER_STYLE
    } else {
        MapStyles.styleJson(mapLayer, hillshade)
    }

    fun applyStyle(map: MapLibreMap) {
        styleLoaded = false
        val builder = Style.Builder().fromJson(styleJson)
        map.setStyle(builder) { style ->
            setupRouteLayers(style, routeColorHex)
            setupUncertainty(style)
            setupContourLayers(style)
            updateAllRouteData(style, routePoints, drawPoints, trackPoints)
            updateInspectPoint(style, inspectPoint)
            updateTerrainOverlay(style, terrainOverlay, terrainOverlayBounds, overlayOpacity)
            updateContours(style, contourGeoJson)
            setupLocationMark(style)
            updateLocationMark(style, currentLocation)
            updateUncertainty(style, currentLocation)
            styleLoaded = true
            onMapReady(map)
            reportScale(map)
            mapView?.let { view -> reportRegionBounds(map, view) }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                onCreate(null)
                getMapAsync { map ->
                    mapRef = map
                    map.uiSettings.apply {
                        isAttributionEnabled = !batterySaver
                        isLogoEnabled = false
                        isCompassEnabled = false
                        isRotateGesturesEnabled = true
                    }
                    map.prefetchZoomDelta = if (batterySaver) 0 else 3
                    map.addOnCameraMoveListener {
                        onBearingChanged(map.cameraPosition.bearing.toDouble())
                        reportScale(map)
                    }
                    map.addOnCameraIdleListener {
                        reportRegionBounds(map, this@apply)
                        reportScale(map)
                    }
                    map.addOnMapClickListener { point ->
                        val handler = clickHandlerState.value
                        if (handler != null) {
                            handler(point.latitude, point.longitude)
                            true
                        } else {
                            false
                        }
                    }
                    map.addOnMapLongClickListener { point ->
                        val handler = longClickHandlerState.value
                        if (handler != null) {
                            handler(point.latitude, point.longitude)
                            true
                        } else {
                            false
                        }
                    }
                    applyStyle(map)
                }
                mapView = this
            }
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onPause()
            mapView?.onStop()
        }
    }

    LaunchedEffect(regionInsetDp, styleLoaded) {
        val map = mapRef ?: return@LaunchedEffect
        val view = mapView ?: return@LaunchedEffect
        if (styleLoaded) reportRegionBounds(map, view)
    }

    LaunchedEffect(mapLayer, hillshade, batterySaver, routeColorHex) {
        mapRef?.let { applyStyle(it) }
    }

    LaunchedEffect(routePoints, drawPoints, trackPoints, styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        mapRef?.style?.let { updateAllRouteData(it, routePoints, drawPoints, trackPoints) }
    }

    LaunchedEffect(inspectPoint, styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        mapRef?.style?.let { updateInspectPoint(it, inspectPoint) }
    }

    LaunchedEffect(terrainOverlay, terrainOverlayBounds, overlayOpacity, styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        mapRef?.style?.let { updateTerrainOverlay(it, terrainOverlay, terrainOverlayBounds, overlayOpacity) }
    }

    LaunchedEffect(contourGeoJson, styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        mapRef?.style?.let { updateContours(it, contourGeoJson) }
    }

    LaunchedEffect(fitRouteTrigger, routePoints, styleLoaded) {
        if (!styleLoaded || routePoints.isEmpty()) return@LaunchedEffect
        if (fitRouteTrigger != lastFitTrigger && fitRouteTrigger > 0) {
            lastFitTrigger = fitRouteTrigger
            mapRef?.let { fitBounds(it, routePoints) }
        }
    }

    val routeFitKey = remember(routePoints) {
        if (routePoints.isEmpty()) null
        else "${routePoints.first().lat},${routePoints.first().lon},${routePoints.size}"
    }
    LaunchedEffect(routeFitKey, styleLoaded, autoFitRoute) {
        if (!autoFitRoute || routeFitKey == null || !styleLoaded) return@LaunchedEffect
        mapRef?.let { fitBounds(it, routePoints) }
    }

    val drawFitKey = remember(drawPoints) {
        if (drawPoints.isEmpty()) null else "draw-${drawPoints.size}-${drawPoints.last().lat}"
    }
    LaunchedEffect(drawFitKey, styleLoaded) {
        if (drawFitKey == null || !styleLoaded || drawPoints.size < 2) return@LaunchedEffect
        mapRef?.let { fitBounds(it, drawPoints, paddingPx = 80) }
    }

    LaunchedEffect(orientationTrigger, northUp, styleLoaded) {
        if (!styleLoaded || orientationTrigger == lastOrientationTrigger) return@LaunchedEffect
        lastOrientationTrigger = orientationTrigger
        val map = mapRef ?: return@LaunchedEffect
        val loc = currentLocation
        val bearing = if (northUp) 0.0 else loc?.bearing?.toDouble() ?: 0.0
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(map.cameraPosition.target)
                    .zoom(map.cameraPosition.zoom)
                    .tilt(map.cameraPosition.tilt)
                    .bearing(bearing)
                    .build()
            ),
            350
        )
    }

    LaunchedEffect(recenterTrigger, currentLocation, styleLoaded) {
        if (!styleLoaded || recenterTrigger == lastRecenterTrigger) return@LaunchedEffect
        lastRecenterTrigger = recenterTrigger
        val loc = currentLocation ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val bearing = if (northUp) 0.0 else loc.bearing.toDouble()
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(map.cameraPosition.zoom.coerceAtLeast(15.0))
                    .bearing(bearing)
                    .build()
            ),
            400
        )
    }

    LaunchedEffect(followUser, currentLocation, northUp, styleLoaded) {
        if (!styleLoaded || !followUser) return@LaunchedEffect
        val loc = currentLocation ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val bearing = if (northUp) 0.0 else loc.bearing.toDouble()
        map.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(map.cameraPosition.zoom.coerceAtLeast(14.0))
                    .bearing(bearing)
                    .build()
            ),
            800
        )
    }

    LaunchedEffect(currentLocation, styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        updateLocationMark(style, currentLocation)
        updateUncertainty(style, currentLocation)
    }
}

private fun setupContourLayers(style: Style) {
    if (style.getSource(CONTOUR_SOURCE) != null) return
    style.addSource(GeoJsonSource(CONTOUR_SOURCE))
    val intermediate = LineLayer(CONTOUR_LAYER, CONTOUR_SOURCE).withFilter(
        org.maplibre.android.style.expressions.Expression.eq(
            org.maplibre.android.style.expressions.Expression.get("index"),
            org.maplibre.android.style.expressions.Expression.literal(false)
        )
    ).withProperties(
        PropertyFactory.lineColor(AndroidColor.parseColor("#C4A574")),
        PropertyFactory.lineWidth(0.7f),
        PropertyFactory.lineOpacity(0.55f)
    )
    intermediate.minZoom = 12f
    val index = LineLayer(CONTOUR_INDEX_LAYER, CONTOUR_SOURCE).withFilter(
        org.maplibre.android.style.expressions.Expression.eq(
            org.maplibre.android.style.expressions.Expression.get("index"),
            org.maplibre.android.style.expressions.Expression.literal(true)
        )
    ).withProperties(
        PropertyFactory.lineColor(AndroidColor.parseColor("#B0894F")),
        PropertyFactory.lineWidth(1.4f),
        PropertyFactory.lineOpacity(0.72f)
    )
    index.minZoom = 10f
    if (style.getLayer(ROUTE_LAYER) != null) {
        style.addLayerBelow(intermediate, ROUTE_LAYER)
        style.addLayerBelow(index, ROUTE_LAYER)
    } else {
        style.addLayer(intermediate)
        style.addLayer(index)
    }
}

private fun updateContours(style: Style, geoJson: String?) {
    val source = style.getSource(CONTOUR_SOURCE) as? GeoJsonSource ?: return
    source.setGeoJson(geoJson?.takeIf { it.isNotBlank() } ?: EMPTY_FEATURE_COLLECTION)
}

private fun setupRouteLayers(style: Style, routeColorHex: String) {
    data class LayerDef(val sourceId: String, val layerId: String, val color: String, val width: Float)
    listOf(
        LayerDef(ROUTE_SOURCE, ROUTE_LAYER, routeColorHex, 5f),
        LayerDef(DRAW_SOURCE, DRAW_LAYER, "#FBBF24", 4f),
        LayerDef(TRACK_SOURCE, TRACK_LAYER, "#60A5FA", 3f)
    ).forEach { def ->
        if (style.getSource(def.sourceId) == null) {
            style.addSource(GeoJsonSource(def.sourceId))
            style.addLayer(
                LineLayer(def.layerId, def.sourceId).withProperties(
                    PropertyFactory.lineColor(AndroidColor.parseColor(def.color)),
                    PropertyFactory.lineWidth(def.width),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        } else if (def.layerId == ROUTE_LAYER) {
            (style.getLayer(ROUTE_LAYER) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor(def.color))
            )
        }
    }
    if (style.getSource(DRAW_POINTS_SOURCE) == null) {
        style.addSource(GeoJsonSource(DRAW_POINTS_SOURCE))
        style.addLayer(
            CircleLayer(DRAW_POINTS_LAYER, DRAW_POINTS_SOURCE).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(AndroidColor.parseColor("#FBBF24")),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(2f)
            )
        )
    }
    if (style.getSource(INSPECT_SOURCE) == null) {
        style.addSource(GeoJsonSource(INSPECT_SOURCE))
        style.addLayer(
            CircleLayer(INSPECT_LAYER, INSPECT_SOURCE).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(AndroidColor.parseColor("#FBBF24")),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(2f)
            )
        )
    }
}

private fun updateAllRouteData(
    style: Style,
    routePoints: List<TrackPoint>,
    drawPoints: List<TrackPoint>,
    trackPoints: List<TrackPoint>
) {
    (style.getSource(ROUTE_SOURCE) as? GeoJsonSource)?.setGeoJson(pointsToLineGeoJson(routePoints))
    (style.getSource(DRAW_SOURCE) as? GeoJsonSource)?.setGeoJson(pointsToLineGeoJson(drawPoints))
    (style.getSource(TRACK_SOURCE) as? GeoJsonSource)?.setGeoJson(pointsToLineGeoJson(trackPoints))
    (style.getSource(DRAW_POINTS_SOURCE) as? GeoJsonSource)?.setGeoJson(pointsToCollectionGeoJson(drawPoints))
}

private fun updateInspectPoint(style: Style, point: Pair<Double, Double>?) {
    val geo = if (point == null) {
        "{\"type\":\"FeatureCollection\",\"features\":[]}"
    } else {
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${point.second},${point.first}]}}]}"""
    }
    (style.getSource(INSPECT_SOURCE) as? GeoJsonSource)?.setGeoJson(geo)
}

private fun updateTerrainOverlay(style: Style, bitmap: Bitmap?, bounds: BoundingBox?, opacity: Float = 0.48f) {
    val existing = style.getSource(OVERLAY_SOURCE) as? ImageSource
    if (bitmap == null || bounds == null) {
        if (style.getLayer(OVERLAY_LAYER) != null) style.removeLayer(OVERLAY_LAYER)
        if (existing != null) style.removeSource(OVERLAY_SOURCE)
        return
    }
    val quad = LatLngQuad(
        LatLng(bounds.maxLat, bounds.minLon),
        LatLng(bounds.maxLat, bounds.maxLon),
        LatLng(bounds.minLat, bounds.maxLon),
        LatLng(bounds.minLat, bounds.minLon)
    )
    if (existing == null) {
        style.addSource(ImageSource(OVERLAY_SOURCE, quad, bitmap))
        val layer = RasterLayer(OVERLAY_LAYER, OVERLAY_SOURCE).withProperties(
            PropertyFactory.rasterOpacity(opacity)
        )
        if (style.getLayer(ROUTE_LAYER) != null) {
            style.addLayerBelow(layer, ROUTE_LAYER)
        } else {
            style.addLayer(layer)
        }
    } else {
        existing.setImage(bitmap)
        existing.setCoordinates(quad)
        (style.getLayer(OVERLAY_LAYER) as? RasterLayer)?.setProperties(
            PropertyFactory.rasterOpacity(opacity)
        )
    }
}

private fun pointsToCollectionGeoJson(points: List<TrackPoint>): String {
    if (points.isEmpty()) return EMPTY_FEATURE_COLLECTION
    val features = JSONArray()
    points.forEachIndexed { index, p ->
        features.put(
            JSONObject().apply {
                put("type", "Feature")
                put("properties", JSONObject().put("n", index + 1).put("last", index == points.lastIndex))
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().put(p.lon).put(p.lat))
                })
            }
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

private fun pointsToLineGeoJson(points: List<TrackPoint>): String {
    if (points.isEmpty()) return "{\"type\":\"FeatureCollection\",\"features\":[]}"
    val coordinates = JSONArray()
    points.forEach { p ->
        coordinates.put(JSONArray().apply {
            put(p.lon)
            put(p.lat)
            if (p.elevation != 0.0) put(p.elevation)
        })
    }
    val geometry = JSONObject().apply {
        put("type", "LineString")
        put("coordinates", coordinates)
    }
    val feature = JSONObject().apply {
        put("type", "Feature")
        put("geometry", geometry)
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", JSONArray().put(feature))
    }.toString()
}

fun fitBounds(map: MapLibreMap, points: List<TrackPoint>, paddingPx: Int = 120) {
    if (points.isEmpty()) return
    val builder = LatLngBounds.Builder()
    points.forEach { builder.include(LatLng(it.lat, it.lon)) }
    try {
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), paddingPx), 600)
    } catch (_: Exception) {
        // Fallback if map not laid out yet
        val center = LatLng(
            points.map { it.lat }.average(),
            points.map { it.lon }.average()
        )
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 12.0), 600)
    }
}

private fun setupLocationMark(style: Style) {
    if (style.getSource(LOCATION_SOURCE) != null) return
    style.addSource(GeoJsonSource(LOCATION_SOURCE))
    style.addLayer(
        CircleLayer(LOCATION_HALO_LAYER, LOCATION_SOURCE).withProperties(
            PropertyFactory.circleRadius(14f),
            PropertyFactory.circleColor(AndroidColor.parseColor("#38BDF8")),
            PropertyFactory.circleOpacity(0.28f)
        )
    )
    style.addLayer(
        CircleLayer(LOCATION_LAYER, LOCATION_SOURCE).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(AndroidColor.parseColor("#2563EB")),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(2.5f)
        )
    )
}

private fun updateLocationMark(style: Style, loc: GpsUpdate?) {
    val source = style.getSource(LOCATION_SOURCE) as? GeoJsonSource ?: return
    if (loc == null || !loc.latitude.isFinite() || !loc.longitude.isFinite()) {
        source.setGeoJson(EMPTY_FEATURE_COLLECTION)
        return
    }
    source.setGeoJson(
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${loc.longitude},${loc.latitude}]}}]}"""
    )
}

private fun setupUncertainty(style: Style) {
    if (style.getSource(UNCERTAINTY_SOURCE) != null) return
    style.addSource(GeoJsonSource(UNCERTAINTY_SOURCE))
    style.addLayer(
        FillLayer(UNCERTAINTY_FILL, UNCERTAINTY_SOURCE).withProperties(
            PropertyFactory.fillColor(AndroidColor.parseColor("#38BDF8")),
            PropertyFactory.fillOpacity(0.18f)
        )
    )
    style.addLayer(
        LineLayer(UNCERTAINTY_LINE, UNCERTAINTY_SOURCE).withProperties(
            PropertyFactory.lineColor(AndroidColor.parseColor("#38BDF8")),
            PropertyFactory.lineWidth(1.5f)
        )
    )
}

private fun updateUncertainty(style: Style, loc: GpsUpdate?) {
    val source = style.getSource(UNCERTAINTY_SOURCE) as? GeoJsonSource ?: return
    val radius = loc?.accuracy?.toDouble() ?: Double.NaN
    if (loc == null || !radius.isFinite() || radius <= 0.0) {
        source.setGeoJson(EMPTY_FEATURE_COLLECTION)
        return
    }
    source.setGeoJson(uncertaintyPolygon(loc.latitude, loc.longitude, radius.coerceAtLeast(6.0)))
}

private fun uncertaintyPolygon(lat: Double, lon: Double, radiusM: Double): String {
    val ring = JSONArray()
    for (step in 0..36) {
        val (pointLat, pointLon) = GeoMath.destination(lat, lon, step * 10.0, radiusM)
        ring.put(JSONArray().put(pointLon).put(pointLat))
    }
    val feature = JSONObject()
        .put("type", "Feature")
        .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring)))
    return JSONObject().put("type", "FeatureCollection").put("features", JSONArray().put(feature)).toString()
}

private const val UNCERTAINTY_SOURCE = "uncertainty-source"
private const val UNCERTAINTY_FILL = "uncertainty-fill"
private const val UNCERTAINTY_LINE = "uncertainty-line"
private const val LOCATION_SOURCE = "location-source"
private const val LOCATION_LAYER = "location-layer"
private const val LOCATION_HALO_LAYER = "location-halo"
private const val ROUTE_SOURCE = "route-source"
private const val ROUTE_LAYER = "route-layer"
private const val DRAW_SOURCE = "draw-source"
private const val DRAW_LAYER = "draw-layer"
private const val DRAW_POINTS_SOURCE = "draw-points-source"
private const val DRAW_POINTS_LAYER = "draw-points-layer"
private const val TRACK_SOURCE = "track-source"
private const val TRACK_LAYER = "track-layer"
private const val INSPECT_SOURCE = "inspect-source"
private const val INSPECT_LAYER = "inspect-layer"
private const val OVERLAY_SOURCE = "terrain-overlay"
private const val OVERLAY_LAYER = "terrain-overlay-layer"
private const val CONTOUR_SOURCE = "dem-contours"
private const val CONTOUR_LAYER = "dem-contours-layer"
private const val CONTOUR_INDEX_LAYER = "dem-contours-index"
private const val EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"
