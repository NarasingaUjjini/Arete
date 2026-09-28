package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.ui.screens.BottomTab
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.HazardRed
import com.trailmap.gps.ui.theme.InstrumentBay
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TouchTarget

/** Height of the bottom nav icon row (not including system navigation bar inset). */
val AlpineBottomNavHeight = 56.dp

@Composable
fun AlpineBottomNav(
    selectedTab: BottomTab?,
    isRecording: Boolean,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = InstrumentBay,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AlpineBottomNavHeight)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlpineNavItem("ROUTES", Icons.Default.Map, selectedTab == BottomTab.ROUTES) {
                    onTabSelected(BottomTab.ROUTES)
                }
                AlpineNavItem(
                    label = if (isRecording) "REC" else "RECORD",
                    icon = Icons.Default.RadioButtonChecked,
                    selected = selectedTab == BottomTab.RECORD || isRecording,
                    tint = when {
                        isRecording -> HazardRed
                        selectedTab == BottomTab.RECORD -> LocalAccent.current
                        else -> OnSurfaceVariant
                    },
                    onClick = { onTabSelected(BottomTab.RECORD) }
                )
                AlpineNavItem("LAYERS", Icons.Default.Layers, selectedTab == BottomTab.LAYERS) {
                    onTabSelected(BottomTab.LAYERS)
                }
                AlpineNavItem("SETTINGS", Icons.Default.Settings, selectedTab == BottomTab.SETTINGS) {
                    onTabSelected(BottomTab.SETTINGS)
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun AlpineNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tint: Color = if (selected) LocalAccent.current else OnSurfaceVariant,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.size(TouchTarget)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                color = tint,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                lineHeight = 11.sp
            )
        }
    }
}
