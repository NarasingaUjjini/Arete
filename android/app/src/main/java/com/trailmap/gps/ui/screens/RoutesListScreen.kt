package com.trailmap.gps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.data.RouteEntity
import com.trailmap.gps.data.RouteLibraryFilter
import com.trailmap.gps.data.RouteSource
import com.trailmap.gps.data.TripPackStatus
import com.trailmap.gps.ui.theme.Black
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.Gutter
import com.trailmap.gps.ui.theme.MarginEdge
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.SurfaceContainer
import com.trailmap.gps.ui.theme.TrailGreen
import com.trailmap.gps.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesListScreen(
    routes: List<RouteEntity>,
    settings: AppSettings,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onRouteClick: (Long) -> Unit,
    onImportClick: () -> Unit,
    onBack: () -> Unit,
    packStatusByRoute: Map<Long, TripPackStatus> = emptyMap(),
    demReadyIds: Set<Long> = emptySet(),
    onFilterChange: (RouteLibraryFilter) -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text("ROUTE LIBRARY", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 1.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
            )
        },
        containerColor = Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .background(Black)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MarginEdge, vertical = Gutter),
                color = com.trailmap.gps.ui.theme.SurfaceContainerLow,
                shape = com.trailmap.gps.ui.theme.AlpineShape,
                border = BorderStroke(1.dp, OutlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search routes", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = OnSurface, fontSize = 14.sp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Search routes…", color = OnSurfaceVariant, fontSize = 14.sp)
                            inner()
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MarginEdge, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteLibraryFilter.entries.forEach { filter ->
                    val selected = settings.libraryFilter == filter
                    Surface(
                        onClick = { onFilterChange(filter) },
                        color = if (selected) LocalAccent.current else SurfaceContainer,
                        shape = com.trailmap.gps.ui.theme.AlpineShape,
                        border = BorderStroke(1.dp, if (selected) LocalAccent.current else OutlineVariant)
                    ) {
                        Text(
                            filter.label.uppercase(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (selected) com.trailmap.gps.ui.theme.Canvas else OnSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (routes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("NO ROUTES YET", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Import a GPX or draw a line on the map.", color = OnSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onImportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAccent.current, contentColor = com.trailmap.gps.ui.theme.Canvas),
                        shape = com.trailmap.gps.ui.theme.AlpineShape
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Text("IMPORT", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = MarginEdge),
                    verticalArrangement = Arrangement.spacedBy(Gutter)
                ) {
                    items(routes, key = { it.id }) { route ->
                        RouteCard(
                            route = route,
                            settings = settings,
                            packStatus = packStatusByRoute[route.id],
                            demReady = demReadyIds.contains(route.id) || route.offlineDownloaded,
                            onClick = { onRouteClick(route.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: RouteEntity,
    settings: AppSettings,
    packStatus: TripPackStatus?,
    demReady: Boolean,
    onClick: () -> Unit
) {
    val role = when (route.source) {
        RouteSource.IMPORTED -> "PRIMARY ROUTE"
        RouteSource.RECORDED -> "RECORDED TRACK"
        RouteSource.DRAWN -> "DRAWN ROUTE"
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${route.name}, $role, ${FormatUtils.formatDistance(route.distanceMeters, settings.distanceUnit)}"
        },
        color = SurfaceContainer,
        shape = com.trailmap.gps.ui.theme.AlpineShape,
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(route.name.uppercase(), color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(role, color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 2.dp))
            Text(
                "${FormatUtils.formatDistance(route.distanceMeters, settings.distanceUnit)}  ·  +" +
                    "${FormatUtils.formatElevation(route.elevationGainMeters, settings.elevationUnit)}  ·  " +
                    "${FormatUtils.formatElevation(route.maxElevationMeters, settings.elevationUnit)} max",
                color = OnSurface,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(
                    when (route.source) {
                        RouteSource.IMPORTED -> "IMPORTED"
                        RouteSource.RECORDED -> "RECORDING"
                        RouteSource.DRAWN -> "DRAWN"
                    }
                )
                if (packStatus == TripPackStatus.READY || route.offlineDownloaded) Badge("OFFLINE READY")
                if (demReady) Badge("3D READY")
            }
            Text(
                SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(route.importedAt)),
                color = OnSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(color = SurfaceContainer, shape = com.trailmap.gps.ui.theme.AlpineShape, border = BorderStroke(1.dp, OutlineVariant)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            color = OnSurface,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
