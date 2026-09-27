package com.trailmap.gps.importing

import com.trailmap.gps.data.ParsedRoute
import com.trailmap.gps.data.RouteStats
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.Waypoint
import com.trailmap.gps.geo.ElevationStats
import com.trailmap.gps.geo.GeoMath
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

class ImportException(message: String) : IllegalArgumentException(message)

object RouteParser {
    fun parse(inputStream: InputStream, fileName: String): ParsedRoute {
        val content = inputStream.bufferedReader().use { it.readText() }.trim()
        if (content.isEmpty()) throw ImportException("File is empty")
        val parsed = when {
            content.contains("<gpx", ignoreCase = true) -> parseGpx(content, fileName)
            content.contains("<kml", ignoreCase = true) -> parseKml(content, fileName)
            content.startsWith("{") || content.startsWith("[") -> parseGeoJson(content, fileName)
            else -> throw ImportException("Unsupported file format. Use GPX, KML, or GeoJSON.")
        }
        if (parsed.points.size < 2 && parsed.waypoints.isEmpty()) {
            throw ImportException("No usable coordinates found in $fileName")
        }
        if (parsed.points.size < 2) {
            throw ImportException("Need at least two route points. Found only waypoints.")
        }
        return parsed
    }

    private fun parseGpx(content: String, fileName: String): ParsedRoute {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(content.reader())

        var metadataName: String? = null
        var currentTrackName: String? = null
        var currentRouteName: String? = null
        val tracks = mutableListOf<Pair<String?, MutableList<TrackPoint>>>()
        val routes = mutableListOf<Pair<String?, MutableList<TrackPoint>>>()
        var currentTrack: MutableList<TrackPoint>? = null
        var currentRoute: MutableList<TrackPoint>? = null
        val waypoints = mutableListOf<Waypoint>()

        var inMetadata = false
        var inWaypoint = false
        var wpLat = 0.0
        var wpLon = 0.0
        var wpEle = 0.0
        var wpName = ""
        var wpDesc = ""
        var ptLat = 0.0
        var ptLon = 0.0
        var ptEle = 0.0
        var ptTime: Long? = null
        var inTrkpt = false
        var inRtept = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':')
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "metadata" -> inMetadata = true
                    "trk" -> {
                        currentTrack = mutableListOf()
                        currentTrackName = null
                    }
                    "rte" -> {
                        currentRoute = mutableListOf()
                        currentRouteName = null
                    }
                    "name" -> {
                        val text = readText(parser)
                        when {
                            inWaypoint -> wpName = text
                            inMetadata && metadataName == null -> metadataName = text
                            currentTrack != null && currentTrackName == null && !inTrkpt -> currentTrackName = text
                            currentRoute != null && currentRouteName == null && !inRtept -> currentRouteName = text
                        }
                    }
                    "desc" -> if (inWaypoint) wpDesc = readText(parser)
                    "trkpt" -> {
                        inTrkpt = true
                        ptLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                        ptLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                        ptEle = 0.0
                        ptTime = null
                    }
                    "rtept" -> {
                        inRtept = true
                        ptLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                        ptLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                        ptEle = 0.0
                        ptTime = null
                    }
                    "wpt" -> {
                        inWaypoint = true
                        wpLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                        wpLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                        wpEle = 0.0
                        wpName = ""
                        wpDesc = ""
                    }
                    "ele" -> {
                        val value = readText(parser).toDoubleOrNull() ?: 0.0
                        when {
                            inWaypoint -> wpEle = value
                            inTrkpt || inRtept -> ptEle = value
                        }
                    }
                    "time" -> if (inTrkpt || inRtept) {
                        ptTime = parseIsoTime(readText(parser))
                    }
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "metadata" -> inMetadata = false
                    "trkpt" -> {
                        addPoint(currentTrack, ptLat, ptLon, ptEle, ptTime)
                        inTrkpt = false
                    }
                    "rtept" -> {
                        addPoint(currentRoute, ptLat, ptLon, ptEle, ptTime)
                        inRtept = false
                    }
                    "wpt" -> {
                        if (GeoMath.isValidLatitude(wpLat) && GeoMath.isValidLongitude(wpLon) &&
                            !GeoMath.isNullIsland(wpLat, wpLon)
                        ) {
                            waypoints.add(
                                Waypoint(
                                    name = wpName.ifBlank { "Waypoint" },
                                    lat = wpLat,
                                    lon = wpLon,
                                    elevation = wpEle,
                                    type = com.trailmap.gps.geo.WaypointKind.from(wpName, wpDesc).label,
                                    notes = wpDesc
                                )
                            )
                        }
                        inWaypoint = false
                    }
                    "trk" -> {
                        currentTrack?.let { tracks.add(currentTrackName to it) }
                        currentTrack = null
                    }
                    "rte" -> {
                        currentRoute?.let { routes.add(currentRouteName to it) }
                        currentRoute = null
                    }
                }
            }
            event = parser.next()
        }

        val chosen = tracks.firstOrNull { it.second.size >= 2 }
            ?: routes.firstOrNull { it.second.size >= 2 }
            ?: throw ImportException("No track or route with at least two valid points")

        val name = metadataName?.takeIf { it.isNotBlank() }
            ?: chosen.first?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.')
        val points = ElevationStats.enrich(chosen.second)
        val stats = ElevationStats.calculate(points)
        return toParsed(name, points, waypoints, stats)
    }

    private fun parseKml(content: String, fileName: String): ParsedRoute {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(content.reader())

        var documentName: String? = null
        var placemarkName: String? = null
        var currentName: String? = null
        val lines = mutableListOf<Pair<String?, List<TrackPoint>>>()
        val waypoints = mutableListOf<Waypoint>()
        var inCoordinates = false
        var inPoint = false
        var coordBuffer = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':')
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "name" -> {
                        val text = readText(parser)
                        if (documentName == null) documentName = text
                        currentName = text
                        placemarkName = text
                    }
                    "Point" -> inPoint = true
                    "coordinates" -> {
                        inCoordinates = true
                        coordBuffer = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> if (inCoordinates) coordBuffer.append(parser.text)
                XmlPullParser.END_TAG -> when (tag) {
                    "coordinates" -> {
                        val pts = parseCoordinateList(coordBuffer.toString())
                        if (inPoint && pts.isNotEmpty()) {
                            val p = pts.first()
                            waypoints.add(
                                Waypoint(
                                    name = placemarkName?.ifBlank { "Waypoint" } ?: "Waypoint",
                                    lat = p.lat,
                                    lon = p.lon,
                                    elevation = p.elevation,
                                    type = com.trailmap.gps.geo.WaypointKind.from(placemarkName.orEmpty()).label
                                )
                            )
                        } else if (!inPoint && pts.size >= 2) {
                            lines.add(currentName to pts)
                        }
                        inCoordinates = false
                    }
                    "Point" -> inPoint = false
                    "Placemark" -> placemarkName = null
                }
            }
            event = parser.next()
        }

        val chosen = lines.firstOrNull() ?: throw ImportException("No line geometry found in KML")
        val name = documentName?.takeIf { it.isNotBlank() }
            ?: chosen.first?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.')
        val points = ElevationStats.enrich(chosen.second)
        return toParsed(name, points, waypoints, ElevationStats.calculate(points))
    }

    private fun parseGeoJson(content: String, fileName: String): ParsedRoute {
        val root = try {
            if (content.trimStart().startsWith("[")) JSONObject().put("features", JSONArray(content))
            else JSONObject(content)
        } catch (e: Exception) {
            throw ImportException("Invalid GeoJSON: ${e.message}")
        }
        val name = root.optString("name").ifBlank {
            root.optJSONObject("properties")?.optString("name").orEmpty()
        }.ifBlank { fileName.substringBeforeLast('.') }

        val lines = mutableListOf<List<TrackPoint>>()
        val waypoints = mutableListOf<Waypoint>()
        collectGeoJson(root, lines, waypoints, name)

        val chosen = lines.firstOrNull { it.size >= 2 }
            ?: throw ImportException("No LineString with at least two valid points")
        val points = ElevationStats.enrich(chosen)
        return toParsed(name, points, waypoints, ElevationStats.calculate(points))
    }

    private fun collectGeoJson(
        obj: JSONObject,
        lines: MutableList<List<TrackPoint>>,
        waypoints: MutableList<Waypoint>,
        fallbackName: String
    ) {
        val type = obj.optString("type")
        when (type) {
            "FeatureCollection" -> {
                val features = obj.optJSONArray("features") ?: return
                for (i in 0 until features.length()) {
                    collectGeoJson(features.getJSONObject(i), lines, waypoints, fallbackName)
                }
            }
            "Feature" -> {
                val geom = obj.optJSONObject("geometry") ?: return
                val featureName = obj.optJSONObject("properties")?.optString("name").orEmpty()
                    .ifBlank { fallbackName }
                collectGeometry(geom, lines, waypoints, featureName)
            }
            else -> collectGeometry(obj, lines, waypoints, fallbackName)
        }
    }

    private fun collectGeometry(
        geom: JSONObject,
        lines: MutableList<List<TrackPoint>>,
        waypoints: MutableList<Waypoint>,
        name: String
    ) {
        when (geom.optString("type")) {
            "Point" -> pointFromArray(geom.optJSONArray("coordinates"))?.let {
                waypoints.add(
                    Waypoint(
                        name.ifBlank { "Waypoint" },
                        it.lat,
                        it.lon,
                        it.elevation,
                        com.trailmap.gps.geo.WaypointKind.from(name).label
                    )
                )
            }
            "MultiPoint" -> {
                val arr = geom.optJSONArray("coordinates") ?: return
                for (i in 0 until arr.length()) {
                    pointFromArray(arr.optJSONArray(i))?.let {
                        waypoints.add(
                            Waypoint(
                                name.ifBlank { "Waypoint" },
                                it.lat,
                                it.lon,
                                it.elevation,
                                com.trailmap.gps.geo.WaypointKind.from(name).label
                            )
                        )
                    }
                }
            }
            "LineString" -> {
                val pts = lineFromArray(geom.optJSONArray("coordinates"))
                if (pts.size >= 2) lines.add(pts)
            }
            "MultiLineString" -> {
                val arr = geom.optJSONArray("coordinates") ?: return
                for (i in 0 until arr.length()) {
                    val pts = lineFromArray(arr.optJSONArray(i))
                    if (pts.size >= 2) lines.add(pts)
                }
            }
            "GeometryCollection" -> {
                val arr = geom.optJSONArray("geometries") ?: return
                for (i in 0 until arr.length()) {
                    collectGeometry(arr.getJSONObject(i), lines, waypoints, name)
                }
            }
        }
    }

    private fun pointFromArray(arr: JSONArray?): TrackPoint? {
        if (arr == null || arr.length() < 2) return null
        val lon = arr.optDouble(0, Double.NaN)
        val lat = arr.optDouble(1, Double.NaN)
        val ele = arr.optDouble(2, 0.0)
        return if (validCoord(lat, lon)) TrackPoint(lat, lon, ele) else null
    }

    private fun lineFromArray(arr: JSONArray?): List<TrackPoint> {
        if (arr == null) return emptyList()
        val pts = mutableListOf<TrackPoint>()
        for (i in 0 until arr.length()) {
            pointFromArray(arr.optJSONArray(i))?.let { pts.add(it) }
        }
        return pts
    }

    private fun parseCoordinateList(text: String): List<TrackPoint> {
        val pts = mutableListOf<TrackPoint>()
        text.trim().split(Regex("\\s+")).forEach { token ->
            val parts = token.split(',')
            if (parts.size >= 2) {
                val lon = parts[0].toDoubleOrNull() ?: return@forEach
                val lat = parts[1].toDoubleOrNull() ?: return@forEach
                val ele = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                if (validCoord(lat, lon)) pts.add(TrackPoint(lat, lon, ele))
            }
        }
        return pts
    }

    private fun addPoint(target: MutableList<TrackPoint>?, lat: Double, lon: Double, ele: Double, time: Long?) {
        if (target == null) return
        if (!validCoord(lat, lon)) return
        target.add(TrackPoint(lat, lon, ele, time))
    }

    private fun validCoord(lat: Double, lon: Double): Boolean =
        GeoMath.isValidLatitude(lat) && GeoMath.isValidLongitude(lon) && !GeoMath.isNullIsland(lat, lon)

    private fun toParsed(
        name: String,
        points: List<TrackPoint>,
        waypoints: List<Waypoint>,
        stats: RouteStats
    ) = ParsedRoute(
        name = name,
        points = points,
        waypoints = waypoints,
        distanceMeters = stats.distanceMeters,
        elevationGainMeters = stats.elevationGainMeters,
        elevationLossMeters = stats.elevationLossMeters,
        maxElevationMeters = stats.maxElevationMeters,
        estimatedTimeSeconds = 0
    )

    private fun readText(parser: XmlPullParser): String {
        return if (parser.next() == XmlPullParser.TEXT) parser.text?.trim().orEmpty() else ""
    }

    private fun parseIsoTime(text: String): Long? = try {
        java.time.Instant.parse(text).toEpochMilli()
    } catch (_: Exception) {
        null
    }

    fun calculateStats(points: List<TrackPoint>): RouteStats = ElevationStats.calculate(points)

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        GeoMath.haversineMeters(lat1, lon1, lat2, lon2)
}
