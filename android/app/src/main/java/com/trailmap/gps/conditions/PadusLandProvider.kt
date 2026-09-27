package com.trailmap.gps.conditions

import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PadusLandProvider {
    suspend fun identify(lat: Double, lon: Double): LandUnit? = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(QUERY)
                append("?geometry=$lon,$lat&geometryType=esriGeometryPoint&inSR=4326")
                append("&spatialRel=esriSpatialRelIntersects&returnGeometry=false")
                append("&outFields=Unit_Nm,Mng_Name,Des_Tp,Loc_Nm&f=json")
            }
            val json = HttpJson.get(url, accept = "application/json")
            val features = json.optJSONArray("features") ?: return@runCatching null
            if (features.length() == 0) return@runCatching null
            val a = features.getJSONObject(0).optJSONObject("attributes") ?: return@runCatching null
            LandUnit(
                name = a.optString("Unit_Nm", a.optString("Loc_Nm", "Protected area")),
                manager = a.optString("Mng_Name"),
                designation = a.optString("Des_Tp"),
                source = "PAD-US",
                sourceUrl = SOURCE
            )
        }.onFailure { AppLog.w("conditions", "PAD-US query failed", it) }.getOrNull()
    }

    companion object {
        const val SOURCE = "https://www.usgs.gov/programs/gap-analysis-project/science/pad-us-data-overview"
        const val QUERY =
            "https://services.arcgis.com/v01gqwM5QqNysAAi/arcgis/rest/services/PADUS3_0Combined_Proclamation_Marine_Fee_Designation_Easement_PublicAccess/FeatureServer/0/query"
    }
}
