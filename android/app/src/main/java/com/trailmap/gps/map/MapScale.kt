package com.trailmap.gps.map

import kotlin.math.cos
import kotlin.math.pow

data class MapScaleReading(
    val metersPerPixel: Double,
    val barMeters: Double,
    val label: String
)

object MapScale {
    fun metersPerPixel(latitude: Double, zoom: Double): Double {
        val lat = latitude.coerceIn(-85.0, 85.0)
        val z = zoom.coerceIn(0.0, 22.0)
        return 156543.03392 * cos(Math.toRadians(lat)) / 2.0.pow(z)
    }

    fun reading(latitude: Double, zoom: Double, targetPixels: Double, metric: Boolean): MapScaleReading {
        val mpp = metersPerPixel(latitude, zoom).coerceAtLeast(0.01)
        val raw = (mpp * targetPixels).coerceAtLeast(1.0)
        val barMeters = niceDistance(raw, metric)
        return MapScaleReading(mpp, barMeters, label(barMeters, metric))
    }

    fun niceDistance(meters: Double, metric: Boolean): Double {
        val steps = if (metric) {
            doubleArrayOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0)
        } else {
            val foot = 0.3048
            val mile = 1609.34
            doubleArrayOf(
                20 * foot, 50 * foot, 100 * foot, 200 * foot, 500 * foot,
                0.25 * mile, 0.5 * mile, mile, 2 * mile, 5 * mile, 10 * mile, 25 * mile
            )
        }
        return steps.firstOrNull { it >= meters } ?: steps.last()
    }

    fun label(meters: Double, metric: Boolean): String {
        return if (metric) {
            if (meters >= 1000.0) "${(meters / 1000.0).let { if (it >= 10) it.toInt().toString() else trim(it) }} km"
            else "${meters.toInt()} m"
        } else {
            val feet = meters / 0.3048
            val miles = meters / 1609.34
            when {
                miles >= 0.25 -> "${trim(miles)} mi"
                else -> "${feet.toInt()} ft"
            }
        }
    }

    private fun trim(value: Double): String {
        val rounded = if (value >= 10) value.toInt().toDouble() else (Math.round(value * 10.0) / 10.0)
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
