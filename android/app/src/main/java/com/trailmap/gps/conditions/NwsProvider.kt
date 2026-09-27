package com.trailmap.gps.conditions

import com.trailmap.gps.data.providers.ConditionsProvider
import com.trailmap.gps.data.providers.ConditionsResult
import com.trailmap.gps.data.providers.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NwsProvider : ConditionsProvider {
    override val id: String = "nws"
    override val name: String = "National Weather Service"

    override suspend fun getConditions(location: GeoPoint): ConditionsResult {
        val forecast = fetchPoint("POINT", location.lat, location.lon, location.elevation)
        return ConditionsResult(
            summary = forecast.periods.firstOrNull()?.shortForecast ?: "NWS",
            freshness = forecast.freshness()
        )
    }

    suspend fun fetchPoint(label: String, lat: Double, lon: Double, elevationMeters: Double?): NwsPointForecast =
        withContext(Dispatchers.IO) {
            val meta = HttpJson.get("https://api.weather.gov/points/$lat,$lon")
            val props = meta.getJSONObject("properties")
            val forecastUrl = props.getString("forecast")
            val hourlyUrl = props.optString("forecastHourly")
            val office = props.optString("cwa", props.optString("gridId"))
            val gridId = props.optString("gridId")
            val forecast = HttpJson.get(forecastUrl)
            val fProps = forecast.getJSONObject("properties")
            val periods = parsePeriods(fProps.optJSONArray("periods"))
            val hourly = if (hourlyUrl.isNotBlank()) {
                runCatching { parseHourly(HttpJson.get(hourlyUrl)) }.getOrDefault(emptyList())
            } else emptyList()
            val alerts = runCatching { fetchAlerts(lat, lon) }.getOrDefault(emptyList())
            NwsPointForecast(
                label = label,
                lat = lat,
                lon = lon,
                elevationMeters = elevationMeters,
                office = office,
                gridId = gridId,
                periods = periods,
                hourly = hourly,
                alerts = alerts,
                generatedAt = fProps.optString("generatedAt"),
                updatedAt = fProps.optString("updateTime", fProps.optString("generatedAt")),
                savedAt = System.currentTimeMillis(),
                sourceUrl = forecastUrl
            )
        }

    private fun fetchAlerts(lat: Double, lon: Double): List<NwsAlert> {
        val json = HttpJson.get("https://api.weather.gov/alerts/active?point=$lat,$lon")
        val features = json.optJSONArray("features") ?: return emptyList()
        return buildList {
            for (i in 0 until features.length().coerceAtMost(8)) {
                val p = features.getJSONObject(i).optJSONObject("properties") ?: continue
                add(
                    NwsAlert(
                        event = p.optString("event"),
                        headline = p.optString("headline"),
                        severity = p.optString("severity"),
                        ends = p.optString("ends", p.optString("expires"))
                    )
                )
            }
        }
    }

    private fun parsePeriods(arr: org.json.JSONArray?): List<NwsPeriod> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length().coerceAtMost(8)) {
                val o = arr.getJSONObject(i)
                add(
                    NwsPeriod(
                        name = o.optString("name"),
                        temperatureF = o.optInt("temperature").takeIf { o.has("temperature") },
                        wind = o.optString("windSpeed"),
                        gust = o.optString("windGust"),
                        shortForecast = o.optString("shortForecast"),
                        detailedForecast = o.optString("detailedForecast"),
                        precipChance = o.optJSONObject("probabilityOfPrecipitation")?.optInt("value"),
                        startMs = parseIso(o.optString("startTime"))
                    )
                )
            }
        }
    }

    private fun parseHourly(json: JSONObject): List<NwsHour> {
        val arr = json.getJSONObject("properties").optJSONArray("periods") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length().coerceAtMost(24)) {
                val o = arr.getJSONObject(i)
                add(
                    NwsHour(
                        startMs = parseIso(o.optString("startTime")) ?: 0L,
                        temperatureF = o.optInt("temperature").takeIf { o.has("temperature") },
                        windMph = parseMph(o.optString("windSpeed")),
                        gustMph = parseMph(o.optString("windGust")),
                        precipChance = o.optJSONObject("probabilityOfPrecipitation")?.optInt("value"),
                        shortForecast = o.optString("shortForecast")
                    )
                )
            }
        }
    }

    companion object {
        fun parseIso(text: String): Long? {
            if (text.isBlank()) return null
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
            )
            formats.forEach { pattern ->
                runCatching {
                    val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    return fmt.parse(text)?.time
                }
            }
            return null
        }

        fun parseMph(text: String): Double? {
            val nums = Regex("""(\d+(?:\.\d+)?)""").findAll(text).map { it.groupValues[1].toDouble() }.toList()
            return when {
                nums.isEmpty() -> null
                nums.size == 1 -> nums[0]
                else -> nums.average()
            }
        }
    }
}
