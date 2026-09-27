package com.trailmap.gps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.trailmap.gps.ui.TrailMapAppContent
import com.trailmap.gps.ui.theme.TrailMapTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    private var pendingImport by mutableStateOf<Pair<java.io.InputStream, String>?>(null)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled on next location request */ }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleImportUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        requestLocationPermissions()
        handleIntent(intent)

        setContent {
            TrailMapTheme {
                TrailMapAppContent(
                    onBrowseFiles = {
                        filePickerLauncher.launch(arrayOf(
                            "application/gpx+xml",
                            "application/xml",
                            "text/xml",
                            "application/vnd.google-earth.kml+xml",
                            "application/json",
                            "*/*"
                        ))
                    },
                    pendingImportStream = {
                        pendingImport.also { pendingImport = null }
                    }
                )
            }
        }

        // Screen-on is applied only during navigation/recording from the Compose tree.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                uri?.let { handleImportUri(it) }
            }
        }
    }

    private fun handleImportUri(uri: Uri) {
        try {
            val name = uri.lastPathSegment ?: "imported_route.gpx"
            val stream = contentResolver.openInputStream(uri) ?: return
            pendingImport = stream to name
        } catch (_: Exception) {
            // Import failed silently; user can retry via file picker
        }
    }

    private fun requestLocationPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            locationPermissionLauncher.launch(permissions)
        }
    }
}
