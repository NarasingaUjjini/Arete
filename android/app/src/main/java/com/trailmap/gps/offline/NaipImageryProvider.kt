package com.trailmap.gps.offline

import com.trailmap.gps.data.providers.BoundingBox
import com.trailmap.gps.data.providers.CoverageInfo
import com.trailmap.gps.data.providers.ImageryResolution
import com.trailmap.gps.data.providers.OfflineImageryProvider
import com.trailmap.gps.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class NaipImageryProvider : OfflineImageryProvider {
    override val id: String = "usgs-naip"
    override val name: String = "USGS NAIP"
    override val attribution: String = "USGS NAIP orthoimagery. Public domain."

    override fun coverage(area: BoundingBox): CoverageInfo {
        val conus = area.minLat in 24.0..50.0 && area.minLon in -125.0..-66.0
        return CoverageInfo(
            available = conus,
            notes = if (conus) "CONUS NAIP" else "NAIP is U.S. public-domain imagery",
            maxResolutionMeters = 1.0
        )
    }

    override suspend fun download(
        area: BoundingBox,
        resolution: ImageryResolution,
        dest: File
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (!NetworkGate.allowNetwork()) return@withContext Result.failure(IllegalStateException("Network disabled"))
        if (!coverage(area).available) {
            return@withContext Result.failure(IllegalStateException("NAIP coverage is U.S. only"))
        }
        val size = if (resolution == ImageryResolution.OVERVIEW) 768 else 1280
        val url = buildString {
            append(IMAGE_SERVER)
            append("/exportImage?bbox=${area.minLon},${area.minLat},${area.maxLon},${area.maxLat}")
            append("&bboxSR=4326&imageSR=3857&size=$size,$size&format=jpg&f=image")
        }
        runCatching {
            dest.parentFile?.mkdirs()
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 25_000
                readTimeout = 45_000
                setRequestProperty("User-Agent", OfflineTileManager.USER_AGENT)
            }
            try {
                if (connection.responseCode !in 200..299) {
                    error("NAIP export HTTP ${connection.responseCode}")
                }
                val tmp = File(dest.parentFile, "${dest.name}.tmp")
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                dest.length()
            } finally {
                connection.disconnect()
            }
        }.onFailure { AppLog.w("pack", "NAIP download failed", it) }
    }

    companion object {
        const val IMAGE_SERVER =
            "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPImagery/ImageServer"
    }
}
