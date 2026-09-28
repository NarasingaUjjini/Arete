package com.trailmap.gps.ui.maptools

enum class MapTool(val code: String, val label: String) {
    LOCATION("LOC", "Location"),
    COMPASS("CMP", "Compass"),
    ROUTE("RTE", "Route"),
    TERRAIN("TRN", "Terrain"),
    ELEVATION("ELV", "Elevation"),
    LAYERS("LYR", "Layers");

    companion object {
        val mapTools: List<MapTool> = entries
    }
}

enum class MapDestination(val label: String) {
    ROUTES("Routes"),
    RECORD("Record"),
    SETTINGS("Settings"),
    CONDITIONS("Conditions"),
    REC_INFO("NPS/Rec Info"),
    IMPORT("Import"),
    DOWNLOAD("Download area"),
    GPS("GPS diagnostics"),
    FULLSCREEN("Fullscreen")
}

object MapToolSession {
    fun next(current: MapTool?, tapped: MapTool): MapTool? =
        if (current == tapped) null else tapped
}
