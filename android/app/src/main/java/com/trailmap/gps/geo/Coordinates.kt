package com.trailmap.gps.geo

import com.trailmap.gps.data.CoordinateFormat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Coordinates {
    fun format(lat: Double, lon: Double, format: CoordinateFormat): String = when (format) {
        CoordinateFormat.DECIMAL -> decimal(lat, lon)
        CoordinateFormat.DMS -> dms(lat, lon)
        CoordinateFormat.DDM -> ddm(lat, lon)
        CoordinateFormat.UTM -> utm(lat, lon)
        CoordinateFormat.MGRS -> mgrs(lat, lon)
    }

    fun decimal(lat: Double, lon: Double): String =
        String.format("%.6f, %.6f", lat, lon)

    fun dms(lat: Double, lon: Double): String =
        "${toDms(lat, true)}  ${toDms(lon, false)}"

    fun ddm(lat: Double, lon: Double): String =
        "${toDdm(lat, true)}  ${toDdm(lon, false)}"

    fun utm(lat: Double, lon: Double): String {
        val z = utmZone(lon)
        val (easting, northing) = toUtm(lat, lon, z)
        val hemi = if (lat >= 0) "N" else "S"
        return String.format("%d%s %.0fE %.0fN", z, hemi, easting, northing)
    }

    fun mgrs(lat: Double, lon: Double, precision: Int = 5): String {
        val zone = utmZone(lon)
        val (easting, northing) = toUtm(lat, lon, zone)
        val band = latitudeBand(lat)
        val (col, row) = hundredKmLetters(zone, easting, northing)
        val e = ((easting % 100000).toInt()).toString().padStart(5, '0').take(precision)
        val n = ((northing % 100000).toInt()).toString().padStart(5, '0').take(precision)
        return "$zone$band$col$row$e$n"
    }

    fun utmZone(lon: Double): Int = (((lon + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)

    fun toUtm(lat: Double, lon: Double, zone: Int = utmZone(lon)): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val lonOrigin = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)
        val a = 6378137.0
        val e = 0.081819191
        val k0 = 0.9996
        val e2 = e * e
        val n = a / sqrt(1 - e2 * sin(latRad) * sin(latRad))
        val t = tan(latRad) * tan(latRad)
        val c = e2 / (1 - e2) * cos(latRad) * cos(latRad)
        val aa = cos(latRad) * (lonRad - lonOrigin)
        val m = a * (
            (1 - e2 / 4 - 3 * e2 * e2 / 64) * latRad -
                (3 * e2 / 8 + 3 * e2 * e2 / 32) * sin(2 * latRad) +
                (15 * e2 * e2 / 256) * sin(4 * latRad)
            )
        var easting = k0 * n * (aa + (1 - t + c) * aa.pow(3) / 6) + 500000.0
        var northing = k0 * (m + n * tan(latRad) * (aa * aa / 2 + (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24))
        if (lat < 0) northing += 10_000_000.0
        return easting to northing
    }

    private fun toDms(value: Double, isLat: Boolean): String {
        val hemi = if (isLat) if (value >= 0) "N" else "S" else if (value >= 0) "E" else "W"
        val abs = abs(value)
        val deg = abs.toInt()
        val minFull = (abs - deg) * 60.0
        val min = minFull.toInt()
        val sec = (minFull - min) * 60.0
        return String.format("%d°%02d'%04.1f\"%s", deg, min, sec, hemi)
    }

    private fun toDdm(value: Double, isLat: Boolean): String {
        val hemi = if (isLat) if (value >= 0) "N" else "S" else if (value >= 0) "E" else "W"
        val abs = abs(value)
        val deg = abs.toInt()
        val min = (abs - deg) * 60.0
        return String.format("%d°%06.3f'%s", deg, min, hemi)
    }

    private fun latitudeBand(lat: Double): Char {
        val bands = "CDEFGHJKLMNPQRSTUVWX"
        val idx = floor((lat + 80.0) / 8.0).toInt().coerceIn(0, bands.lastIndex)
        return bands[idx]
    }

    private fun hundredKmLetters(zone: Int, easting: Double, northing: Double): Pair<Char, Char> {
        val cols = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val rows = "ABCDEFGHJKLMNPQRSTUV"
        val set = (zone - 1) % 3
        val colIdx = (easting / 100000.0).toInt() - 1
        val col = cols[(set * 8 + colIdx).mod(cols.length)]
        val rowOffset = if (zone % 2 == 0) 5 else 0
        val rowIdx = ((northing / 100000.0).toInt() + rowOffset).mod(rows.length)
        return col to rows[rowIdx]
    }
}
