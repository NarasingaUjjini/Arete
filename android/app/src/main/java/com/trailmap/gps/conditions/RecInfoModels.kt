package com.trailmap.gps.conditions

data class RecAlert(
    val title: String,
    val category: String,
    val parkName: String,
    val parkCode: String,
    val description: String,
    val url: String
)

data class RecPlace(
    val name: String,
    val kind: String,
    val source: String,
    val distanceMiles: Double?,
    val summary: String,
    val url: String,
    val parkCode: String = "",
    val reservable: Boolean = false
)

data class NearbyPark(
    val name: String,
    val parkCode: String,
    val states: String,
    val distanceMiles: Double?,
    val url: String
)

data class RecInfoSnapshot(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMiles: Double = RecInfoParser.RADIUS_MILES,
    val parks: List<NearbyPark> = emptyList(),
    val alerts: List<RecAlert> = emptyList(),
    val campsites: List<RecPlace> = emptyList(),
    val facilities: List<RecPlace> = emptyList(),
    val error: String? = null,
    val cached: Boolean = false,
    val loadedAt: Long = System.currentTimeMillis()
)
