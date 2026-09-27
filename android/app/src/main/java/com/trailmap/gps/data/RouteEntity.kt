package com.trailmap.gps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: RouteSource,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val maxElevationMeters: Double,
    val estimatedTimeSeconds: Long,
    val pointsJson: String,
    val waypointsJson: String = "[]",
    val importedAt: Long = System.currentTimeMillis(),
    val offlineDownloaded: Boolean = false,
    val offlineSizeBytes: Long = 0,
    val notes: String = ""
)

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val elevation: Double = 0.0,
    val time: Long? = null,
    val cumulativeDistanceMeters: Double = 0.0,
    val cumulativeElevationGainMeters: Double = 0.0,
    val cumulativeElevationLossMeters: Double = 0.0
)

data class Waypoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevation: Double = 0.0,
    val type: String = "Custom",
    val notes: String = ""
)

data class ParsedRoute(
    val name: String,
    val points: List<TrackPoint>,
    val waypoints: List<Waypoint> = emptyList(),
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val maxElevationMeters: Double,
    val estimatedTimeSeconds: Long
)

data class RouteStats(
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val maxElevationMeters: Double,
    val minElevationMeters: Double = 0.0,
    val maxGradePercent: Double = 0.0,
    val averageGradePercent: Double = 0.0,
    val estimatedTimeSeconds: Long = 0
)
