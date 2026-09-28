package com.trailmap.gps.conditions

import com.trailmap.gps.geo.GeoMath
import org.json.JSONObject

object RecInfoParser {
    const val RADIUS_MILES = 40.0

    fun miles(userLat: Double, userLon: Double, lat: Double?, lon: Double?): Double? {
        if (lat == null || lon == null) return null
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (!GeoMath.isValidLatitude(lat) || !GeoMath.isValidLongitude(lon)) return null
        return GeoMath.haversineMeters(userLat, userLon, lat, lon) / 1609.34
    }

    fun withinRadius(distanceMiles: Double?, radiusMiles: Double = RADIUS_MILES): Boolean {
        if (distanceMiles == null) return true
        return distanceMiles <= radiusMiles
    }

    fun stripHtml(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun jsonDouble(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val number = obj.optDouble(key, Double.NaN)
            if (number.isFinite()) return number
            val text = obj.optString(key)
            val parsed = text.toDoubleOrNull()
            if (parsed != null && parsed.isFinite()) return parsed
        }
        return null
    }

    fun isCampFacility(type: String): Boolean {
        val t = type.lowercase()
        return t.contains("camp") || t == "camping"
    }

    fun parseNpsParks(json: JSONObject, userLat: Double, userLon: Double): List<NearbyPark> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val code = item.optString("parkCode").lowercase()
                val name = item.optString("fullName").ifBlank { item.optString("name") }
                if (code.isBlank() || name.isBlank()) continue
                val lat = jsonDouble(item, "latitude")
                val lon = jsonDouble(item, "longitude")
                add(
                    NearbyPark(
                        name = name,
                        parkCode = code,
                        states = item.optString("states"),
                        distanceMiles = miles(userLat, userLon, lat, lon),
                        url = item.optString("url").ifBlank { "https://www.nps.gov/$code/index.htm" }
                    )
                )
            }
        }.sortedBy { it.distanceMiles ?: Double.MAX_VALUE }
    }

    fun parseNpsAlerts(json: JSONObject, parkNames: Map<String, String>): List<RecAlert> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val title = item.optString("title").ifBlank { item.optString("category") }
                if (title.isBlank()) continue
                val code = item.optString("parkCode").lowercase()
                add(
                    RecAlert(
                        title = title,
                        category = item.optString("category").ifBlank { "Alert" },
                        parkName = parkNames[code].orEmpty().ifBlank { code.uppercase() },
                        parkCode = code,
                        description = stripHtml(item.optString("description")),
                        url = item.optString("url").ifBlank { "https://www.nps.gov/$code/planyourvisit/conditions.htm" }
                    )
                )
            }
        }
    }

    fun parseNpsCampgrounds(
        json: JSONObject,
        userLat: Double,
        userLon: Double,
        parkNames: Map<String, String>,
        radiusMiles: Double = RADIUS_MILES
    ): List<RecPlace> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (name.isBlank()) continue
                val lat = jsonDouble(item, "latitude")
                val lon = jsonDouble(item, "longitude")
                val distance = miles(userLat, userLon, lat, lon)
                if (!withinRadius(distance, radiusMiles)) continue
                val code = item.optString("parkCode").lowercase()
                val sites = item.optJSONObject("campsites")
                val total = sites?.optString("totalSites").orEmpty()
                val reserveUrl = item.optString("reservationUrl")
                val summary = listOfNotNull(
                    parkNames[code]?.takeIf { it.isNotBlank() },
                    total.takeIf { it.isNotBlank() && it != "0" }?.let { "$it sites" },
                    stripHtml(item.optString("description")).take(160).ifBlank { null }
                ).joinToString(" · ")
                add(
                    RecPlace(
                        name = name,
                        kind = "Campground",
                        source = "NPS",
                        distanceMiles = distance,
                        summary = summary,
                        url = item.optString("url").ifBlank {
                            reserveUrl.ifBlank { "https://www.nps.gov/$code/planyourvisit/camping.htm" }
                        },
                        parkCode = code,
                        reservable = reserveUrl.isNotBlank() || item.optString("numberOfSitesReservable").toIntOrNull()?.let { it > 0 } == true
                    )
                )
            }
        }.sortedBy { it.distanceMiles ?: Double.MAX_VALUE }
    }

    fun parseRidbFacilities(
        json: JSONObject,
        userLat: Double,
        userLon: Double,
        radiusMiles: Double = RADIUS_MILES
    ): List<RecPlace> {
        val data = json.optJSONArray("RECDATA") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val name = item.optString("FacilityName")
                if (name.isBlank()) continue
                val lat = jsonDouble(item, "FacilityLatitude")
                val lon = jsonDouble(item, "FacilityLongitude")
                val distance = miles(userLat, userLon, lat, lon)
                if (!withinRadius(distance, radiusMiles)) continue
                val type = item.optString("FacilityTypeDescription").ifBlank { "Facility" }
                val id = item.optString("FacilityID")
                val reserve = item.optString("FacilityReservationURL")
                add(
                    RecPlace(
                        name = name,
                        kind = type,
                        source = "RIDB",
                        distanceMiles = distance,
                        summary = stripHtml(item.optString("FacilityDescription")).take(160),
                        url = reserve.ifBlank {
                            if (id.isNotBlank()) "https://www.recreation.gov/camping/campgrounds/$id"
                            else "https://www.recreation.gov/"
                        },
                        reservable = item.optBoolean("Reservable") || reserve.isNotBlank()
                    )
                )
            }
        }.sortedBy { it.distanceMiles ?: Double.MAX_VALUE }
    }
}
