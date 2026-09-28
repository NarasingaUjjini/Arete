package com.trailmap.gps.data

enum class RouteSource {
    IMPORTED,
    RECORDED,
    DRAWN
}

enum class MapLayer {
    ARETE_TOPO,
    USGS_TOPO,
    IMAGERY,
    HISTORICAL,
    OPENTOPO,
    SATELLITE,
    OSM;

    val label: String
        get() = when (this) {
            ARETE_TOPO -> "Arete Topo (USGS)"
            USGS_TOPO -> "USGS Topo"
            IMAGERY -> "USGS Imagery"
            HISTORICAL -> "Historical / classic USGS"
            OPENTOPO -> "OpenTopoMap (online)"
            SATELLITE -> "Esri satellite (online)"
            OSM -> "OpenStreetMap (online)"
        }

    val onlineOnly: Boolean
        get() = this == OPENTOPO || this == SATELLITE || this == OSM

    companion object {
        fun fromStored(raw: String?): MapLayer {
            if (raw.isNullOrBlank()) return ARETE_TOPO
            if (raw == "TOPO") return ARETE_TOPO
            return runCatching { valueOf(raw) }.getOrDefault(ARETE_TOPO)
        }
    }
}

enum class PowerProfile {
    BATTERY,
    BALANCED,
    ACCURACY
}

enum class CoordinateFormat {
    DECIMAL,
    DMS,
    DDM,
    UTM,
    MGRS
}

enum class OffRouteCorridorSetting {
    NARROW,
    NORMAL,
    WIDE
}

enum class DistanceUnit {
    MILES,
    KILOMETERS
}

enum class ElevationUnit {
    FEET,
    METERS
}
