package com.trailmap.gps

import android.app.Application
import com.trailmap.gps.data.AppDatabase
import com.trailmap.gps.data.SettingsRepository
import com.trailmap.gps.location.LocationEngine
import com.trailmap.gps.data.RouteRepository
import com.trailmap.gps.offline.OfflineTileManager
import com.trailmap.gps.conditions.ConditionsRepository
import com.trailmap.gps.offline.TripPackManager
import com.trailmap.gps.terrain.DemRepository
import org.maplibre.android.MapLibre

class TrailMapApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val offlineTileManager by lazy { OfflineTileManager(this) }
    val locationEngine by lazy { LocationEngine(this) }
    val demRepository by lazy { DemRepository(this) }
    val conditionsRepository by lazy { ConditionsRepository(this) }
    val tripPackManager by lazy {
        TripPackManager(
            this,
            database.tripPackDao(),
            offlineTileManager,
            demRepository,
            RouteRepository(database.routeDao())
        )
    }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        offlineTileManager.installResourceTransform()
    }
}
