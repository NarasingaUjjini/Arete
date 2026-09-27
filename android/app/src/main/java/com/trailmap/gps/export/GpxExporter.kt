package com.trailmap.gps.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.trailmap.gps.data.TrackPoint
import com.trailmap.gps.data.Waypoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {
    fun write(
        context: Context,
        name: String,
        points: List<TrackPoint>,
        waypoints: List<Waypoint> = emptyList()
    ): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "route" }
        val file = File(dir, "$safe.gpx")
        file.writeText(toGpx(name, points, waypoints))
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share GPX").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun toGpx(name: String, points: List<TrackPoint>, waypoints: List<Waypoint>): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("""<gpx version="1.1" creator="Arete" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.append("<metadata><name>${escape(name)}</name></metadata>")
        waypoints.forEach { w ->
            sb.append("""<wpt lat="${w.lat}" lon="${w.lon}">""")
            sb.append("<ele>${w.elevation}</ele>")
            sb.append("<name>${escape(w.name)}</name>")
            if (w.notes.isNotBlank()) sb.append("<desc>${escape(w.notes)}</desc>")
            sb.append("</wpt>")
        }
        sb.append("<trk><name>${escape(name)}</name><trkseg>")
        points.forEach { p ->
            sb.append("""<trkpt lat="${p.lat}" lon="${p.lon}">""")
            sb.append("<ele>${p.elevation}</ele>")
            p.time?.let { sb.append("<time>${iso.format(Date(it))}</time>") }
            sb.append("</trkpt>")
        }
        sb.append("</trkseg></trk></gpx>")
        return sb.toString()
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
