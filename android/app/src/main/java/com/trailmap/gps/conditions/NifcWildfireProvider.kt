package com.trailmap.gps.conditions

import com.trailmap.gps.data.providers.ConditionsProvider
import com.trailmap.gps.data.providers.ConditionsResult
import com.trailmap.gps.data.providers.DataFreshness
import com.trailmap.gps.data.providers.GeoPoint
import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NifcWildfireProvider : ConditionsProvider {
    override val id: String = "nifc-wfigs"
    override val name: String = "NIFC WFIGS"

    override suspend fun getConditions(location: GeoPoint): ConditionsResult {
        val fires = query(location.lat, location.lon)
        return ConditionsResult(
            summary = if (fires.isEmpty()) "No current incidents within 80 km" else fires.joinToString { it.name },
            freshness = DataFreshness(
                provider = name,
                product = "Current incidents",
                timestampMs = System.currentTimeMillis(),
                sourceUrl = SOURCE,
                offline = false,
                attribution = "NIFC / WFIGS open data"
            )
        )
    }

    suspend fun query(lat: Double, lon: Double, radiusMeters: Int = 80_000): List<WildfireIncident> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildString {
                    append(QUERY)
                    append("?geometry=$lon,$lat")
                    append("&geometryType=esriGeometryPoint&inSR=4326")
                    append("&distance=$radiusMeters&units=esriSRUnit_Meter")
                    append("&spatialRel=esriSpatialRelIntersects")
                    append("&outFields=IncidentName,IncidentSize,PercentContained,ModifiedOnDateTime,POOState")
                    append("&returnGeometry=false&f=json")
                }
                val json = HttpJson.get(url, accept = "application/json")
                val features = json.optJSONArray("features") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until features.length().coerceAtMost(8)) {
                        val a = features.getJSONObject(i).optJSONObject("attributes") ?: continue
                        add(
                            WildfireIncident(
                                name = a.optString("IncidentName", "Unnamed incident"),
                                acres = a.optDouble("IncidentSize").takeIf { a.has("IncidentSize") },
                                contained = a.optDouble("PercentContained").takeIf { a.has("PercentContained") },
                                updated = a.opt("ModifiedOnDateTime")?.toString().orEmpty(),
                                sourceUrl = SOURCE
                            )
                        )
                    }
                }
            }.onFailure { AppLog.w("conditions", "NIFC query failed", it) }.getOrDefault(emptyList())
        }

    companion object {
        const val SOURCE = "https://data-nifc.opendata.arcgis.com/"
        const val QUERY =
            "https://services3.arcgis.com/T4QMspbfLg3qTGWY/arcgis/rest/services/WFIGS_Incident_Locations_Current/FeatureServer/0/query"
    }
}
