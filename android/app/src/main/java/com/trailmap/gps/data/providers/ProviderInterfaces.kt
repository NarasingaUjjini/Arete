package com.trailmap.gps.data.providers

data class BoundingBox(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double
)

data class GeoPoint(val lat: Double, val lon: Double, val elevation: Double? = null)

data class Coverage(
    val available: Boolean,
    val notes: String = ""
)

data class DataFreshness(
    val provider: String,
    val product: String,
    val timestampMs: Long?,
    val sourceUrl: String? = null,
    val offline: Boolean = false,
    val attribution: String = ""
)

interface MapDataProvider {
    val id: String
    val name: String
    val attribution: String
    fun supportsOnline(): Boolean
    fun supportsOffline(): Boolean
    fun coverage(bounds: BoundingBox): Coverage
}

interface TerrainProvider {
    val id: String
    val name: String
    val attribution: String
    suspend fun getElevation(lat: Double, lon: Double): Double?
    suspend fun downloadDem(bounds: BoundingBox): Result<Unit>
}

data class ConditionsResult(
    val summary: String,
    val freshness: DataFreshness
)

interface ConditionsProvider {
    val id: String
    val name: String
    suspend fun getConditions(location: GeoPoint): ConditionsResult
}

data class CoverageInfo(
    val available: Boolean,
    val notes: String = "",
    val maxResolutionMeters: Double? = null
)

enum class ImageryResolution { OVERVIEW, STANDARD }

interface OfflineImageryProvider {
    val id: String
    val name: String
    val attribution: String
    fun coverage(area: BoundingBox): CoverageInfo
    suspend fun download(area: BoundingBox, resolution: ImageryResolution, dest: java.io.File): Result<Long>
}

private class UsgsBasemapProvider(
    override val id: String,
    override val name: String,
    override val attribution: String,
    private val offline: Boolean
) : MapDataProvider {
    override fun supportsOnline(): Boolean = true
    override fun supportsOffline(): Boolean = offline
    override fun coverage(bounds: BoundingBox): Coverage =
        Coverage(true, "USGS The National Map — public domain")
}

object ProviderRegistry {
    val mapProviders: List<MapDataProvider> = listOf(
        UsgsBasemapProvider("arete-topo", "Arete Topo", "USGS The National Map", offline = true),
        UsgsBasemapProvider("usgs-topo", "USGS Topo", "USGS The National Map", offline = true),
        UsgsBasemapProvider("usgs-imagery", "USGS Imagery", "USGS Imagery", offline = false),
        UsgsBasemapProvider("usgs-historical", "USGS Historical Topo", "USGS HTMC / topoView", offline = false)
    )
    val terrainProviders: List<TerrainProvider> = listOf(com.trailmap.gps.terrain.Usgs3depProvider())
    val conditionsProviders: List<ConditionsProvider> = listOf(
        com.trailmap.gps.conditions.NwsProvider(),
        com.trailmap.gps.conditions.NifcWildfireProvider()
    )
}
