package com.trailmap.gps.conditions

import android.content.Context
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.Waypoint
import com.trailmap.gps.offline.NetworkGate
import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ConditionsRepository(context: Context) {
    private val app = context.applicationContext
    private val nws = NwsProvider()
    private val fires = NifcWildfireProvider()
    private val land = PadusLandProvider()
    private val water = HydrographyProvider()
    private val cacheDir: File get() = File(app.filesDir, "conditions").also { it.mkdirs() }

    val nwsProvider: NwsProvider get() = nws
    val fireProvider: NifcWildfireProvider get() = fires

    suspend fun load(
        points: List<TrackPoint>,
        waypoints: List<Waypoint> = emptyList(),
        userLat: Double? = null,
        userLon: Double? = null
    ): MountainConditions = withContext(Dispatchers.IO) {
        val trailhead = points.firstOrNull()
        val summit = points.maxByOrNull { it.elevation }?.takeIf { points.size >= 2 }
        val focusLat = userLat ?: trailhead?.lat ?: summit?.lat
        val focusLon = userLon ?: trailhead?.lon ?: summit?.lon
        if (focusLat == null || focusLon == null) {
            return@withContext MountainConditions(error = "No location for conditions")
        }
        val daylight = Daylight.calculate(focusLat, focusLon)
        val avalanche = AvalancheDirectory.nearest(focusLat, focusLon)
        if (!NetworkGate.allowNetwork()) {
            val cached = readCache()
            return@withContext MountainConditions(
                weather = cached,
                daylight = daylight,
                avalanche = avalanche,
                cached = true,
                error = if (cached.isEmpty()) "Offline — no saved forecast" else null
            )
        }
        val weather = mutableListOf<NwsPointForecast>()
        val errors = mutableListOf<String>()
        suspend fun addPoint(label: String, lat: Double, lon: Double, ele: Double?) {
            runCatching { nws.fetchPoint(label, lat, lon, ele) }
                .onSuccess { weather += it }
                .onFailure {
                    AppLog.w("conditions", "NWS $label failed", it)
                    errors += "${it.message}"
                }
        }
        trailhead?.let { addPoint("TRAILHEAD", it.lat, it.lon, it.elevation) }
        if (summit != null && (trailhead == null || GeoFar(summit, trailhead))) {
            addPoint("SUMMIT", summit.lat, summit.lon, summit.elevation)
        }
        waypoints.firstOrNull { it.type.contains("camp", true) }?.let {
            addPoint("CAMP", it.lat, it.lon, it.elevation)
        }
        if (weather.isEmpty() && trailhead == null) {
            addPoint("POINT", focusLat, focusLon, null)
        }
        if (weather.isNotEmpty()) writeCache(weather)

        val fireList = runCatching { fires.query(focusLat, focusLon) }.getOrDefault(emptyList())
        val landUnit = runCatching { land.identify(focusLat, focusLon) }.getOrNull()
        val waterList = runCatching { water.nearby(focusLat, focusLon) }.getOrDefault(emptyList())
        MountainConditions(
            weather = weather.ifEmpty { readCache() },
            daylight = daylight,
            fires = fireList,
            land = landUnit,
            water = waterList,
            avalanche = avalanche,
            cached = weather.isEmpty() && readCache().isNotEmpty(),
            error = errors.firstOrNull()
        )
    }

    private fun GeoFar(a: TrackPoint, b: TrackPoint): Boolean {
        val dlat = a.lat - b.lat
        val dlon = a.lon - b.lon
        return dlat * dlat + dlon * dlon > 0.0004
    }

    private fun writeCache(forecasts: List<NwsPointForecast>) {
        val arr = JSONArray()
        forecasts.forEach { f ->
            arr.put(JSONObject().apply {
                put("label", f.label)
                put("lat", f.lat)
                put("lon", f.lon)
                put("generatedAt", f.generatedAt)
                put("updatedAt", f.updatedAt)
                put("savedAt", f.savedAt)
                put("office", f.office)
                put("sourceUrl", f.sourceUrl)
                put("summary", f.periods.firstOrNull()?.shortForecast.orEmpty())
                put("tempF", f.periods.firstOrNull()?.temperatureF)
                put("wind", f.periods.firstOrNull()?.wind.orEmpty())
                put("detail", f.periods.firstOrNull()?.detailedForecast.orEmpty())
            })
        }
        File(cacheDir, "latest.json").writeText(arr.toString())
    }

    private fun readCache(): List<NwsPointForecast> {
        val file = File(cacheDir, "latest.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                NwsPointForecast(
                    label = o.optString("label"),
                    lat = o.optDouble("lat"),
                    lon = o.optDouble("lon"),
                    elevationMeters = null,
                    office = o.optString("office"),
                    gridId = "",
                    periods = listOf(
                        NwsPeriod(
                            name = "Cached",
                            temperatureF = o.optInt("tempF").takeIf { o.has("tempF") },
                            wind = o.optString("wind"),
                            gust = "",
                            shortForecast = o.optString("summary"),
                            detailedForecast = o.optString("detail"),
                            precipChance = null,
                            startMs = null
                        )
                    ),
                    hourly = emptyList(),
                    alerts = emptyList(),
                    generatedAt = o.optString("generatedAt"),
                    updatedAt = o.optString("updatedAt"),
                    savedAt = o.optLong("savedAt"),
                    sourceUrl = o.optString("sourceUrl")
                )
            }
        }.getOrDefault(emptyList())
    }
}
