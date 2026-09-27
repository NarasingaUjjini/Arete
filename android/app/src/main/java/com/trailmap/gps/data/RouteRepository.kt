package com.trailmap.gps.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class RouteRepository(private val dao: RouteDao) {
    fun observeAll(): Flow<List<RouteEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<RouteEntity?> = dao.observeById(id)
    fun search(query: String): Flow<List<RouteEntity>> = dao.search(query)

    suspend fun getById(id: Long): RouteEntity? = dao.getById(id)

    suspend fun save(parsed: ParsedRoute, source: RouteSource): Long {
        val entity = RouteEntity(
            name = parsed.name,
            source = source,
            distanceMeters = parsed.distanceMeters,
            elevationGainMeters = parsed.elevationGainMeters,
            elevationLossMeters = parsed.elevationLossMeters,
            maxElevationMeters = parsed.maxElevationMeters,
            estimatedTimeSeconds = parsed.estimatedTimeSeconds,
            pointsJson = pointsToJson(parsed.points),
            waypointsJson = waypointsToJson(parsed.waypoints)
        )
        return dao.insert(entity)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun markOfflineDownloaded(id: Long, sizeBytes: Long) {
        val route = dao.getById(id) ?: return
        dao.update(route.copy(offlineDownloaded = true, offlineSizeBytes = sizeBytes))
    }

    fun parsePoints(json: String): List<TrackPoint> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(TrackPoint(
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon"),
                    elevation = obj.optDouble("elevation", 0.0),
                    time = obj.optLong("time").takeIf { it > 0 },
                    cumulativeDistanceMeters = obj.optDouble("cumDist", 0.0),
                    cumulativeElevationGainMeters = obj.optDouble("cumGain", 0.0),
                    cumulativeElevationLossMeters = obj.optDouble("cumLoss", 0.0)
                ))
            }
        }
    }

    fun parseWaypoints(json: String): List<Waypoint> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(Waypoint(
                    name = obj.getString("name"),
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon"),
                    elevation = obj.optDouble("elevation", 0.0),
                    type = obj.optString("type", "Custom"),
                    notes = obj.optString("notes", "")
                ))
            }
        }
    }

    private fun pointsToJson(points: List<TrackPoint>): String {
        val array = JSONArray()
        points.forEach { p ->
            array.put(JSONObject().apply {
                put("lat", p.lat)
                put("lon", p.lon)
                put("elevation", p.elevation)
                p.time?.let { put("time", it) }
                put("cumDist", p.cumulativeDistanceMeters)
                put("cumGain", p.cumulativeElevationGainMeters)
                put("cumLoss", p.cumulativeElevationLossMeters)
            })
        }
        return array.toString()
    }

    private fun waypointsToJson(waypoints: List<Waypoint>): String {
        val array = JSONArray()
        waypoints.forEach { w ->
            array.put(JSONObject().apply {
                put("name", w.name)
                put("lat", w.lat)
                put("lon", w.lon)
                put("elevation", w.elevation)
                put("type", w.type)
                put("notes", w.notes)
            })
        }
        return array.toString()
    }
}
