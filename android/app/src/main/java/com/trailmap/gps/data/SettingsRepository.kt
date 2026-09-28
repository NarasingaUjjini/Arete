package com.trailmap.gps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val ELEVATION_UNIT = stringPreferencesKey("elevation_unit")
        val COORDINATE_FORMAT = stringPreferencesKey("coordinate_format")
        val POWER_PROFILE = stringPreferencesKey("power_profile")
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val HILLSHADE = booleanPreferencesKey("hillshade")
        val CONTOURS = booleanPreferencesKey("contours")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val NORTH_UP = booleanPreferencesKey("north_up")
        val GNSS_ASSISTANCE_UPDATED_AT = longPreferencesKey("gnss_assistance_updated_at")
        val OFF_ROUTE_CORRIDOR = stringPreferencesKey("off_route_corridor")
        val DATA_BAR_PROFILE = stringPreferencesKey("data_bar_profile")
        val DATA_BAR_SLOT1 = stringPreferencesKey("data_bar_slot1")
        val DATA_BAR_SLOT2 = stringPreferencesKey("data_bar_slot2")
        val DATA_BAR_SLOT3 = stringPreferencesKey("data_bar_slot3")
        val ACCENT = stringPreferencesKey("accent_theme")
        val OVERLAY_STRENGTH = stringPreferencesKey("overlay_strength")
        val LARGE_NUMBERS = booleanPreferencesKey("large_numbers")
        val LIBRARY_FILTER = stringPreferencesKey("library_filter")
        val MAP_CHROME = stringPreferencesKey("map_chrome_layout")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            distanceUnit = prefs[Keys.DISTANCE_UNIT]?.let { DistanceUnit.valueOf(it) }
                ?: DistanceUnit.MILES,
            elevationUnit = prefs[Keys.ELEVATION_UNIT]?.let { ElevationUnit.valueOf(it) }
                ?: ElevationUnit.FEET,
            coordinateFormat = prefs[Keys.COORDINATE_FORMAT]?.let { CoordinateFormat.valueOf(it) }
                ?: CoordinateFormat.DECIMAL,
            powerProfile = prefs[Keys.POWER_PROFILE]?.let { PowerProfile.valueOf(it) }
                ?: PowerProfile.BALANCED,
            mapLayer = MapLayer.fromStored(prefs[Keys.MAP_LAYER]),
            hillshadeEnabled = prefs[Keys.HILLSHADE] ?: true,
            contoursEnabled = prefs[Keys.CONTOURS] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            northUp = prefs[Keys.NORTH_UP] ?: true,
            gnssAssistanceUpdatedAt = prefs[Keys.GNSS_ASSISTANCE_UPDATED_AT] ?: 0L,
            offRouteCorridor = prefs[Keys.OFF_ROUTE_CORRIDOR]?.let {
                runCatching { OffRouteCorridorSetting.valueOf(it) }.getOrDefault(OffRouteCorridorSetting.NORMAL)
            } ?: OffRouteCorridorSetting.NORMAL,
            dataBarProfile = prefs[Keys.DATA_BAR_PROFILE]?.let {
                runCatching { DataBarProfile.valueOf(it) }.getOrDefault(DataBarProfile.MOUNTAINEERING)
            } ?: DataBarProfile.MOUNTAINEERING,
            dataBarSlot1 = prefs[Keys.DATA_BAR_SLOT1]?.let {
                runCatching { DataBarMetric.valueOf(it) }.getOrDefault(DataBarMetric.ELEVATION)
            } ?: DataBarMetric.ELEVATION,
            dataBarSlot2 = prefs[Keys.DATA_BAR_SLOT2]?.let {
                runCatching { DataBarMetric.valueOf(it) }.getOrDefault(DataBarMetric.ASCENT)
            } ?: DataBarMetric.ASCENT,
            dataBarSlot3 = prefs[Keys.DATA_BAR_SLOT3]?.let {
                runCatching { DataBarMetric.valueOf(it) }.getOrDefault(DataBarMetric.DAYLIGHT)
            } ?: DataBarMetric.DAYLIGHT,
            accentTheme = AccentTheme.fromStored(prefs[Keys.ACCENT]),
            overlayStrength = prefs[Keys.OVERLAY_STRENGTH]?.let {
                runCatching { OverlayStrength.valueOf(it) }.getOrDefault(OverlayStrength.MEDIUM)
            } ?: OverlayStrength.MEDIUM,
            largeNumbers = prefs[Keys.LARGE_NUMBERS] ?: false,
            libraryFilter = prefs[Keys.LIBRARY_FILTER]?.let {
                runCatching { RouteLibraryFilter.valueOf(it) }.getOrDefault(RouteLibraryFilter.ALL)
            } ?: RouteLibraryFilter.ALL,
            mapChrome = MapChromeLayout.fromStored(prefs[Keys.MAP_CHROME])
        )
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) = edit(Keys.DISTANCE_UNIT, unit.name)
    suspend fun setElevationUnit(unit: ElevationUnit) = edit(Keys.ELEVATION_UNIT, unit.name)
    suspend fun setCoordinateFormat(format: CoordinateFormat) = edit(Keys.COORDINATE_FORMAT, format.name)
    suspend fun setPowerProfile(profile: PowerProfile) = edit(Keys.POWER_PROFILE, profile.name)
    suspend fun setMapLayer(layer: MapLayer) = edit(Keys.MAP_LAYER, layer.name)
    suspend fun setHillshade(enabled: Boolean) = edit(Keys.HILLSHADE, enabled)
    suspend fun setContours(enabled: Boolean) = edit(Keys.CONTOURS, enabled)
    suspend fun setKeepScreenOn(enabled: Boolean) = edit(Keys.KEEP_SCREEN_ON, enabled)
    suspend fun setNorthUp(enabled: Boolean) = edit(Keys.NORTH_UP, enabled)
    suspend fun setGnssAssistanceUpdatedAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.GNSS_ASSISTANCE_UPDATED_AT] = timestamp }
    }
    suspend fun setOffRouteCorridor(value: OffRouteCorridorSetting) = edit(Keys.OFF_ROUTE_CORRIDOR, value.name)
    suspend fun setDataBarProfile(value: DataBarProfile) = edit(Keys.DATA_BAR_PROFILE, value.name)
    suspend fun setDataBarSlots(a: DataBarMetric, b: DataBarMetric, c: DataBarMetric) {
        val unique = DataBarMetric.uniqueSlots(a, b, c)
        context.dataStore.edit {
            it[Keys.DATA_BAR_SLOT1] = unique.first.name
            it[Keys.DATA_BAR_SLOT2] = unique.second.name
            it[Keys.DATA_BAR_SLOT3] = unique.third.name
            it[Keys.DATA_BAR_PROFILE] = DataBarProfile.CUSTOM.name
        }
    }
    suspend fun setAccentTheme(value: AccentTheme) = edit(Keys.ACCENT, value.name)
    suspend fun setOverlayStrength(value: OverlayStrength) = edit(Keys.OVERLAY_STRENGTH, value.name)
    suspend fun setLargeNumbers(enabled: Boolean) = edit(Keys.LARGE_NUMBERS, enabled)
    suspend fun setLibraryFilter(value: RouteLibraryFilter) = edit(Keys.LIBRARY_FILTER, value.name)
    suspend fun setMapChrome(value: MapChromeLayout) = edit(Keys.MAP_CHROME, value.name)

    private suspend fun edit(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}

data class AppSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
    val elevationUnit: ElevationUnit = ElevationUnit.FEET,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val powerProfile: PowerProfile = PowerProfile.BALANCED,
    val mapLayer: MapLayer = MapLayer.ARETE_TOPO,
    val hillshadeEnabled: Boolean = true,
    val contoursEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val northUp: Boolean = true,
    val gnssAssistanceUpdatedAt: Long = 0L,
    val offRouteCorridor: OffRouteCorridorSetting = OffRouteCorridorSetting.NORMAL,
    val dataBarProfile: DataBarProfile = DataBarProfile.MOUNTAINEERING,
    val dataBarSlot1: DataBarMetric = DataBarMetric.ELEVATION,
    val dataBarSlot2: DataBarMetric = DataBarMetric.ASCENT,
    val dataBarSlot3: DataBarMetric = DataBarMetric.DAYLIGHT,
    val accentTheme: AccentTheme = AccentTheme.ALPINE,
    val overlayStrength: OverlayStrength = OverlayStrength.LOW,
    val largeNumbers: Boolean = false,
    val libraryFilter: RouteLibraryFilter = RouteLibraryFilter.ALL,
    val mapChrome: MapChromeLayout = MapChromeLayout.RAIL
) {
    val accentHex: String get() = accentTheme.hex
}
