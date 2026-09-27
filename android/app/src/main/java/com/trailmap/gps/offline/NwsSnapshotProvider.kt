package com.trailmap.gps.offline

import com.trailmap.gps.conditions.HttpJson
import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class WeatherSnapshot(
    val source: String,
    val issuedAt: String,
    val updatedAt: String,
    val summary: String,
    val savedAt: Long,
    val staleAfterMs: Long = 18 * 60 * 60 * 1000L
) {
    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - savedAt > staleAfterMs
}

class NwsSnapshotProvider {
    suspend fun download(lat: Double, lon: Double, dest: File): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        if (!NetworkGate.allowNetwork()) return@withContext Result.failure(IllegalStateException("Network disabled"))
        runCatching {
            val points = HttpJson.get("https://api.weather.gov/points/$lat,$lon")
            val forecastUrl = points.getJSONObject("properties").getString("forecast")
            val forecast = HttpJson.get(forecastUrl)
            val props = forecast.getJSONObject("properties")
            val periods = props.optJSONArray("periods") ?: JSONArray()
            val first = periods.optJSONObject(0)
            val snapshot = WeatherSnapshot(
                source = "NWS",
                issuedAt = props.optString("generatedAt", ""),
                updatedAt = props.optString("updateTime", props.optString("generatedAt", "")),
                summary = first?.optString("detailedForecast").orEmpty().ifBlank {
                    first?.optString("shortForecast").orEmpty()
                },
                savedAt = System.currentTimeMillis()
            )
            dest.parentFile?.mkdirs()
            dest.writeText(
                JSONObject().apply {
                    put("source", snapshot.source)
                    put("issuedAt", snapshot.issuedAt)
                    put("updatedAt", snapshot.updatedAt)
                    put("summary", snapshot.summary)
                    put("savedAt", snapshot.savedAt)
                    put("offline", true)
                }.toString()
            )
            snapshot
        }.onFailure { AppLog.w("pack", "NWS snapshot failed", it) }
    }

    fun read(file: File): WeatherSnapshot? {
        if (!file.exists()) return null
        return runCatching {
            val o = JSONObject(file.readText())
            WeatherSnapshot(
                source = o.optString("source", "NWS"),
                issuedAt = o.optString("issuedAt"),
                updatedAt = o.optString("updatedAt"),
                summary = o.optString("summary"),
                savedAt = o.optLong("savedAt")
            )
        }.getOrNull()
    }

    companion object {
        const val USER_AGENT = HttpJson.USER_AGENT
    }
}
