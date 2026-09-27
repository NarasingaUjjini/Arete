package com.trailmap.gps.conditions

import com.trailmap.gps.geo.GeoMath

data class AvalancheCenter(
    val name: String,
    val region: String,
    val url: String,
    val lat: Double,
    val lon: Double
)

object AvalancheDirectory {
    val centers = listOf(
        AvalancheCenter("Northwest Avalanche Center", "WA / OR / N. ID", "https://nwac.us/", 47.6, -121.4),
        AvalancheCenter("Sierra Avalanche Center", "N. Sierra", "https://www.sierraavalanchecenter.org/", 39.3, -120.3),
        AvalancheCenter("Bridgeport Avalanche Center", "E. Sierra", "https://www.bridgeportavalanchecenter.org/", 38.3, -119.5),
        AvalancheCenter("Eastern Sierra Avalanche Center", "E. Sierra", "https://esavalanche.org/", 37.6, -118.9),
        AvalancheCenter("Colorado Avalanche Information Center", "Colorado", "https://avalanche.state.co.us/", 39.2, -106.3),
        AvalancheCenter("Utah Avalanche Center", "Utah", "https://utahavalanchecenter.org/", 40.6, -111.6),
        AvalancheCenter("Bridger-Teton Avalanche Center", "WY / Tetons", "https://jhavalanche.org/", 43.5, -110.8),
        AvalancheCenter("Gallatin National Forest Avalanche Center", "SW Montana", "https://www.mtavalanche.com/", 45.3, -111.0),
        AvalancheCenter("Sawtooth Avalanche Center", "Idaho", "https://www.sawtoothavalanche.com/", 44.0, -114.9),
        AvalancheCenter("Mount Shasta Avalanche Center", "N. California", "https://www.shastaavalanche.org/", 41.4, -122.2),
        AvalancheCenter("Forecasts of the Canadian Avalanche Centre", "Canada", "https://avalanche.ca/", 51.0, -118.0)
    )

    fun nearest(lat: Double, lon: Double): AvalancheSource {
        val center = centers.minBy { GeoMath.haversineMeters(lat, lon, it.lat, it.lon) }
        return AvalancheSource(
            centerName = center.name,
            region = center.region,
            url = center.url
        )
    }

    const val INDEX_URL = "https://avalanche.org/us-avalanche-centers/"
}
