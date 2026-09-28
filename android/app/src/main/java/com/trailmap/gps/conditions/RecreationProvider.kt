package com.trailmap.gps.conditions

import com.trailmap.gps.BuildConfig
import com.trailmap.gps.geo.GeoMath
import org.json.JSONObject

class RecreationProvider {
    fun nearby(lat: Double, lon: Double): RecreationLinks {
        val parks = fetchNps(lat, lon)
        val rec = fetchRidb(lat, lon)
        val nearby = (parks + rec).distinctBy { it.name.lowercase() }.take(6)
        val hasKeys = BuildConfig.NPS_API_KEY.isNotBlank() || BuildConfig.RIDB_API_KEY.isNotBlank()
        return RecreationLinks(
            nearby = nearby,
            note = if (nearby.isNotEmpty()) {
                "Official park and recreation listings near this point."
            } else if (hasKeys) {
                "No nearby NPS or Recreation.gov units returned for this point."
            } else {
                "No NPS/RIDB key on this build. Use the official public pages."
            }
        )
    }

    private fun fetchNps(lat: Double, lon: Double): List<RecreationPlace> {
        val key = BuildConfig.NPS_API_KEY
        if (key.isBlank()) return emptyList()
        val url =
            "https://developer.nps.gov/api/v1/parks?latitude=$lat&longitude=$lon&limit=8&api_key=$key"
        return runCatching {
            val json = HttpJson.get(url)
            val data = json.optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val name = item.optString("fullName").ifBlank { item.optString("name") }
                    if (name.isBlank()) continue
                    add(
                        RecreationPlace(
                            name = name,
                            type = item.optString("designation").ifBlank { "National Park Service" },
                            url = item.optString("url").ifBlank { "https://www.nps.gov/findapark/index.htm" },
                            distanceNote = item.optString("states")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchRidb(lat: Double, lon: Double): List<RecreationPlace> {
        val key = BuildConfig.RIDB_API_KEY
        if (key.isBlank()) return emptyList()
        val url =
            "https://ridb.recreation.gov/api/v1/recareas?latitude=$lat&longitude=$lon&radius=50&limit=8"
        return runCatching {
            val json = HttpJson.get(url, extraHeaders = mapOf("apikey" to key))
            val rec = json.optJSONArray("RECDATA") ?: return emptyList()
            buildList {
                for (i in 0 until rec.length()) {
                    val item = rec.optJSONObject(i) ?: continue
                    val name = item.optString("RecAreaName")
                    if (name.isBlank()) continue
                    val recLat = item.optDouble("RecAreaLatitude", Double.NaN)
                    val recLon = item.optDouble("RecAreaLongitude", Double.NaN)
                    val miles = if (recLat.isFinite() && recLon.isFinite()) {
                        val m = GeoMath.haversineMeters(lat, lon, recLat, recLon)
                        String.format("%.0f mi", m / 1609.34)
                    } else {
                        ""
                    }
                    add(
                        RecreationPlace(
                            name = name,
                            type = "Recreation.gov",
                            url = item.optString("RecAreaURL").ifBlank { "https://www.recreation.gov/" },
                            distanceNote = miles
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
