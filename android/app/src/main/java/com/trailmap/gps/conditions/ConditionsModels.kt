package com.trailmap.gps.conditions

import com.trailmap.gps.data.providers.DataFreshness

data class NwsPeriod(
    val name: String,
    val temperatureF: Int?,
    val wind: String,
    val gust: String,
    val shortForecast: String,
    val detailedForecast: String,
    val precipChance: Int?,
    val startMs: Long?
)

data class NwsHour(
    val startMs: Long,
    val temperatureF: Int?,
    val windMph: Double?,
    val gustMph: Double?,
    val precipChance: Int?,
    val shortForecast: String
)

data class NwsAlert(
    val event: String,
    val headline: String,
    val severity: String,
    val ends: String
)

data class NwsPointForecast(
    val label: String,
    val lat: Double,
    val lon: Double,
    val elevationMeters: Double?,
    val office: String,
    val gridId: String,
    val periods: List<NwsPeriod>,
    val hourly: List<NwsHour>,
    val alerts: List<NwsAlert>,
    val generatedAt: String,
    val updatedAt: String,
    val savedAt: Long,
    val sourceUrl: String
) {
    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - savedAt > 12 * 60 * 60 * 1000L
    fun freshness(): DataFreshness = DataFreshness(
        provider = "NWS",
        product = "Forecast",
        timestampMs = savedAt,
        sourceUrl = sourceUrl,
        offline = true,
        attribution = "National Weather Service"
    )
}

data class WildfireIncident(
    val name: String,
    val acres: Double?,
    val contained: Double?,
    val updated: String,
    val sourceUrl: String
)

data class LandUnit(
    val name: String,
    val manager: String,
    val designation: String,
    val source: String,
    val sourceUrl: String
)

data class WaterFeature(
    val name: String,
    val type: String,
    val source: String
)

data class AvalancheSource(
    val centerName: String,
    val region: String,
    val url: String,
    val note: String = "Official forecast only. Arete does not rate avalanche danger."
)

data class RecreationPlace(
    val name: String,
    val type: String,
    val url: String,
    val distanceNote: String = ""
)

data class RecreationLinks(
    val npsFindParkUrl: String = "https://www.nps.gov/findapark/index.htm",
    val recreationGovUrl: String = "https://www.recreation.gov/",
    val nearby: List<RecreationPlace> = emptyList(),
    val note: String = "Official park and recreation listings."
)

data class MountainConditions(
    val weather: List<NwsPointForecast> = emptyList(),
    val daylight: DaylightTimes? = null,
    val fires: List<WildfireIncident> = emptyList(),
    val land: LandUnit? = null,
    val water: List<WaterFeature> = emptyList(),
    val avalanche: AvalancheSource? = null,
    val recreation: RecreationLinks = RecreationLinks(),
    val cached: Boolean = false,
    val error: String? = null,
    val loadedAt: Long = System.currentTimeMillis()
)
