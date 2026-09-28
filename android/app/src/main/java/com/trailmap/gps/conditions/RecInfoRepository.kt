package com.trailmap.gps.conditions

import android.content.Context
import com.trailmap.gps.BuildConfig
import com.trailmap.gps.offline.NetworkGate
import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecInfoRepository(context: Context) {
    private val app = context.applicationContext
    private val cacheFile: File get() = File(File(app.filesDir, "recinfo").also { it.mkdirs() }, "latest.json")

    suspend fun load(lat: Double, lon: Double): RecInfoSnapshot = withContext(Dispatchers.IO) {
        val hasNps = BuildConfig.NPS_API_KEY.isNotBlank()
        val hasRidb = BuildConfig.RIDB_API_KEY.isNotBlank()
        if (!hasNps && !hasRidb) {
            return@withContext RecInfoSnapshot(
                latitude = lat,
                longitude = lon,
                error = "No NPS or RIDB key on this build."
            )
        }
        if (!NetworkGate.allowNetwork()) {
            return@withContext readCache()
                ?: RecInfoSnapshot(latitude = lat, longitude = lon, error = "Offline — no saved NPS/RIDB list.")
        }
        val errors = mutableListOf<String>()
        val parks = if (hasNps) {
            runCatching { fetchNpsParks(lat, lon) }
                .onFailure {
                    AppLog.w("recinfo", "NPS parks failed", it)
                    errors += "NPS parks unavailable"
                }
                .getOrDefault(emptyList())
        } else emptyList()
        val parkNames = parks.associate { it.parkCode to it.name }
        val parkCodes = parks.map { it.parkCode }.filter { it.isNotBlank() }.distinct().take(12)
        val alerts = if (hasNps && parkCodes.isNotEmpty()) {
            runCatching { fetchNpsAlerts(parkCodes, parkNames) }
                .onFailure {
                    AppLog.w("recinfo", "NPS alerts failed", it)
                    errors += "NPS alerts unavailable"
                }
                .getOrDefault(emptyList())
        } else emptyList()
        val npsCamps = if (hasNps && parkCodes.isNotEmpty()) {
            runCatching { fetchNpsCampgrounds(lat, lon, parkCodes, parkNames) }
                .onFailure {
                    AppLog.w("recinfo", "NPS campgrounds failed", it)
                    errors += "NPS campgrounds unavailable"
                }
                .getOrDefault(emptyList())
        } else emptyList()
        val ridbAll = if (hasRidb) {
            runCatching { fetchRidbFacilities(lat, lon, activity = null) }
                .onFailure {
                    AppLog.w("recinfo", "RIDB facilities failed", it)
                    errors += "RIDB facilities unavailable"
                }
                .getOrDefault(emptyList())
        } else emptyList()
        val ridbCamps = if (hasRidb) {
            runCatching { fetchRidbFacilities(lat, lon, activity = "CAMPING") }
                .onFailure { AppLog.w("recinfo", "RIDB camping failed", it) }
                .getOrDefault(emptyList())
        } else emptyList()
        val campsites = (npsCamps + ridbCamps + ridbAll.filter { RecInfoParser.isCampFacility(it.kind) })
            .distinctBy { "${it.source}|${it.name.lowercase()}" }
            .sortedBy { it.distanceMiles ?: Double.MAX_VALUE }
            .take(24)
        val facilities = ridbAll
            .distinctBy { it.name.lowercase() }
            .sortedBy { it.distanceMiles ?: Double.MAX_VALUE }
            .take(24)
        val snapshot = RecInfoSnapshot(
            latitude = lat,
            longitude = lon,
            parks = parks,
            alerts = alerts,
            campsites = campsites,
            facilities = facilities,
            error = when {
                campsites.isEmpty() && facilities.isEmpty() && alerts.isEmpty() && errors.isNotEmpty() ->
                    errors.joinToString(" · ")
                else -> null
            }
        )
        writeCache(snapshot)
        snapshot
    }

    private fun fetchNpsParks(lat: Double, lon: Double): List<NearbyPark> {
        val url = "https://developer.nps.gov/api/v1/parks?latitude=$lat&longitude=$lon&limit=12"
        return RecInfoParser.parseNpsParks(npsGet(url), lat, lon)
    }

    private fun fetchNpsAlerts(parkCodes: List<String>, parkNames: Map<String, String>): List<RecAlert> {
        val codes = parkCodes.joinToString(",")
        val url = "https://developer.nps.gov/api/v1/alerts?parkCode=$codes&limit=50"
        return RecInfoParser.parseNpsAlerts(npsGet(url), parkNames)
    }

    private fun fetchNpsCampgrounds(
        lat: Double,
        lon: Double,
        parkCodes: List<String>,
        parkNames: Map<String, String>
    ): List<RecPlace> {
        val codes = parkCodes.joinToString(",")
        val url = "https://developer.nps.gov/api/v1/campgrounds?parkCode=$codes&limit=50"
        return RecInfoParser.parseNpsCampgrounds(npsGet(url), lat, lon, parkNames)
    }

    private fun fetchRidbFacilities(lat: Double, lon: Double, activity: String?): List<RecPlace> {
        val extra = if (activity.isNullOrBlank()) "" else "&activity=$activity"
        val url =
            "https://ridb.recreation.gov/api/v1/facilities?latitude=$lat&longitude=$lon&radius=${RecInfoParser.RADIUS_MILES.toInt()}&limit=50$extra"
        return RecInfoParser.parseRidbFacilities(ridbGet(url), lat, lon)
    }

    private fun npsGet(url: String): JSONObject =
        HttpJson.get(url, extraHeaders = mapOf("X-Api-Key" to BuildConfig.NPS_API_KEY))

    private fun ridbGet(url: String): JSONObject =
        HttpJson.get(url, extraHeaders = mapOf("apikey" to BuildConfig.RIDB_API_KEY))

    private fun writeCache(snapshot: RecInfoSnapshot) {
        runCatching {
            val json = JSONObject().apply {
                put("lat", snapshot.latitude)
                put("lon", snapshot.longitude)
                put("loadedAt", snapshot.loadedAt)
                put("alerts", JSONArray().apply {
                    snapshot.alerts.forEach { alert ->
                        put(JSONObject().apply {
                            put("title", alert.title)
                            put("category", alert.category)
                            put("parkName", alert.parkName)
                            put("parkCode", alert.parkCode)
                            put("description", alert.description)
                            put("url", alert.url)
                        })
                    }
                })
                put("campsites", placesArray(snapshot.campsites))
                put("facilities", placesArray(snapshot.facilities))
            }
            cacheFile.writeText(json.toString())
        }
    }

    private fun placesArray(places: List<RecPlace>): JSONArray = JSONArray().apply {
        places.forEach { place ->
            put(JSONObject().apply {
                put("name", place.name)
                put("kind", place.kind)
                put("source", place.source)
                place.distanceMiles?.let { put("miles", it) }
                put("summary", place.summary)
                put("url", place.url)
                put("parkCode", place.parkCode)
                put("reservable", place.reservable)
            })
        }
    }

    private fun readCache(): RecInfoSnapshot? {
        if (!cacheFile.exists()) return null
        return runCatching {
            val json = JSONObject(cacheFile.readText())
            RecInfoSnapshot(
                latitude = json.optDouble("lat"),
                longitude = json.optDouble("lon"),
                alerts = json.optJSONArray("alerts")?.let { arr ->
                    List(arr.length()) { i ->
                        val o = arr.getJSONObject(i)
                        RecAlert(
                            title = o.optString("title"),
                            category = o.optString("category"),
                            parkName = o.optString("parkName"),
                            parkCode = o.optString("parkCode"),
                            description = o.optString("description"),
                            url = o.optString("url")
                        )
                    }
                }.orEmpty(),
                campsites = readPlaces(json.optJSONArray("campsites")),
                facilities = readPlaces(json.optJSONArray("facilities")),
                cached = true,
                loadedAt = json.optLong("loadedAt"),
                error = if (json.optJSONArray("campsites") == null) "Cached NPS/RIDB list." else null
            )
        }.getOrNull()
    }

    private fun readPlaces(arr: JSONArray?): List<RecPlace> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RecPlace(
                name = o.optString("name"),
                kind = o.optString("kind"),
                source = o.optString("source"),
                distanceMiles = if (o.has("miles")) o.optDouble("miles") else null,
                summary = o.optString("summary"),
                url = o.optString("url"),
                parkCode = o.optString("parkCode"),
                reservable = o.optBoolean("reservable")
            )
        }
    }
}
