package com.trailmap.gps.map

import com.trailmap.gps.data.MapLayer

object MapStyles {
    const val USGS_TOPO_URL =
        "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}"
    const val USGS_SHADE_URL =
        "https://basemap.nationalmap.gov/arcgis/rest/services/USGSShadedReliefOnly/MapServer/tile/{z}/{y}/{x}"
    const val USGS_IMAGERY_URL =
        "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}"
    const val USGS_HISTORICAL_URL =
        "https://ngmdb.usgs.gov/arcgis/rest/services/topoview/ustOverlayAuto/MapServer/tile/{z}/{y}/{x}"

    fun styleJson(layer: MapLayer, hillshade: Boolean = false): String {
        return when (layer) {
            MapLayer.ARETE_TOPO -> areteTopo(hillshade)
            MapLayer.USGS_TOPO -> usgsRaster("usgs-topo", USGS_TOPO_URL, 16, "USGS The National Map")
            MapLayer.IMAGERY -> usgsImagery(hillshade)
            MapLayer.HISTORICAL -> usgsRaster("usgs-historical", USGS_HISTORICAL_URL, 16, "USGS Historical Topographic Map Collection")
            MapLayer.OPENTOPO -> if (hillshade) OPENTOPO_WITH_HILLSHADE else OPENTOPO_STYLE
            MapLayer.SATELLITE -> SATELLITE_STYLE
            MapLayer.OSM -> OSM_STYLE
        }
    }

    /**
     * Terrain-first: USGS relief dominates at low zoom, USGS Topo labels/contours
     * come forward at medium/high zoom. Architecture is not a separate OSM stack.
     */
    private fun areteTopo(strongShade: Boolean): String {
        val shadeHi = if (strongShade) 0.62 else 0.50
        val shadeMid = if (strongShade) 0.38 else 0.26
        val shadeLo = if (strongShade) 0.24 else 0.16
        return """
{
  "version": 8,
  "sources": {
    "usgs-shade": {
      "type": "raster",
      "tiles": ["$USGS_SHADE_URL"],
      "tileSize": 256,
      "maxzoom": 15,
      "attribution": "USGS Shaded Relief"
    },
    "usgs-topo": {
      "type": "raster",
      "tiles": ["$USGS_TOPO_URL"],
      "tileSize": 256,
      "maxzoom": 16,
      "attribution": "USGS The National Map"
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#0b1210" } },
    {
      "id": "arete-shade",
      "type": "raster",
      "source": "usgs-shade",
      "paint": {
        "raster-opacity": ["interpolate", ["linear"], ["zoom"], 4, $shadeHi, 11, $shadeMid, 16, $shadeLo]
      }
    },
    {
      "id": "arete-topo",
      "type": "raster",
      "source": "usgs-topo",
      "paint": {
        "raster-opacity": ["interpolate", ["linear"], ["zoom"], 4, 0.70, 10, 0.88, 14, 1.0]
      }
    }
  ]
}
"""
    }

    private fun usgsImagery(hillshade: Boolean): String {
        val shadeLayer = if (hillshade) """
    ,{
      "id": "usgs-shade-over-imagery",
      "type": "raster",
      "source": "usgs-shade",
      "paint": { "raster-opacity": 0.28 }
    }""" else ""
        val shadeSource = if (hillshade) """
    ,"usgs-shade": {
      "type": "raster",
      "tiles": ["$USGS_SHADE_URL"],
      "tileSize": 256,
      "maxzoom": 15,
      "attribution": "USGS Shaded Relief"
    }""" else ""
        return """
{
  "version": 8,
  "sources": {
    "usgs-imagery": {
      "type": "raster",
      "tiles": ["$USGS_IMAGERY_URL"],
      "tileSize": 256,
      "maxzoom": 16,
      "attribution": "USGS Imagery"
    }$shadeSource
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#0b1210" } },
    { "id": "usgs-imagery-layer", "type": "raster", "source": "usgs-imagery" }
    $shadeLayer
  ]
}
"""
    }

    private fun usgsRaster(id: String, url: String, maxZoom: Int, attribution: String): String = """
{
  "version": 8,
  "sources": {
    "$id": {
      "type": "raster",
      "tiles": ["$url"],
      "tileSize": 256,
      "maxzoom": $maxZoom,
      "attribution": "$attribution"
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#0b1210" } },
    { "id": "$id-layer", "type": "raster", "source": "$id" }
  ]
}
"""

    private const val OPENTOPO_STYLE = """
{
  "version": 8,
  "sources": {
    "opentopo": {
      "type": "raster",
      "tiles": [
        "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
        "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
        "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "maxzoom": 17,
      "attribution": "OpenTopoMap"
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#0b1210" } },
    { "id": "opentopo-layer", "type": "raster", "source": "opentopo" }
  ]
}
"""

    private const val OPENTOPO_WITH_HILLSHADE = """
{
  "version": 8,
  "sources": {
    "opentopo": {
      "type": "raster",
      "tiles": [
        "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
        "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
        "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "maxzoom": 17,
      "attribution": "OpenTopoMap"
    },
    "hillshade": {
      "type": "raster",
      "tiles": ["https://basemap.nationalmap.gov/arcgis/rest/services/USGSShadedReliefOnly/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256,
      "maxzoom": 15,
      "attribution": "USGS Shaded Relief"
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#0b1210" } },
    { "id": "hillshade-layer", "type": "raster", "source": "hillshade", "paint": { "raster-opacity": 0.30 } },
    { "id": "opentopo-layer", "type": "raster", "source": "opentopo" }
  ]
}
"""

    private const val SATELLITE_STYLE = """
{
  "version": 8,
  "sources": {
    "esri-satellite": {
      "type": "raster",
      "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "Esri"
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#0b1210" } },
    { "id": "esri-satellite-layer", "type": "raster", "source": "esri-satellite" }
  ]
}
"""

    private const val OSM_STYLE = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "OpenStreetMap"
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#0b1210" } },
    { "id": "osm-layer", "type": "raster", "source": "osm" }
  ]
}
"""

    const val BATTERY_SAVER_STYLE = """
{
  "version": 8,
  "sources": {},
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#000000" } }
  ]
}
"""
}
