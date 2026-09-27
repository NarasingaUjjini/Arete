package com.trailmap.gps.conditions

import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HydrographyProvider {
    suspend fun nearby(lat: Double, lon: Double, radiusMeters: Int = 400): List<WaterFeature> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildString {
                    append(QUERY)
                    append("?geometry=$lon,$lat&geometryType=esriGeometryPoint&inSR=4326")
                    append("&distance=$radiusMeters&units=esriSRUnit_Meter")
                    append("&spatialRel=esriSpatialRelIntersects")
                    append("&outFields=GNIS_NAME,FTYPE,GNIS_ID&returnGeometry=false&f=json")
                }
                val json = HttpJson.get(url, accept = "application/json")
                val features = json.optJSONArray("features") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until features.length().coerceAtMost(8)) {
                        val a = features.getJSONObject(i).optJSONObject("attributes") ?: continue
                        val name = a.optString("GNIS_NAME").ifBlank { "Unnamed water feature" }
                        add(
                            WaterFeature(
                                name = name,
                                type = a.optString("FTYPE", "water"),
                                source = "USGS NHD"
                            )
                        )
                    }
                }.distinctBy { it.name }
            }.onFailure { AppLog.w("conditions", "NHD query failed", it) }.getOrDefault(emptyList())
        }

    companion object {
        const val SOURCE = "https://www.usgs.gov/3d-hydrography-program/access-3dhp-data-products"
        const val QUERY =
            "https://hydro.nationalmap.gov/arcgis/rest/services/nhd/MapServer/2/query"
    }
}
