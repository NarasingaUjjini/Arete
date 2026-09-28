package com.trailmap.gps.conditions

import com.trailmap.gps.offline.NetworkGate
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object HttpJson {
    const val USER_AGENT = "Arete/0.1 (offline mountaineering navigation; personal project)"

    fun get(
        url: String,
        accept: String = "application/geo+json, application/json",
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        if (!NetworkGate.allowNetwork()) error("Network disabled")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode} for $url")
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
