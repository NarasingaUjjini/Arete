package com.trailmap.gps.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/** Stitch Instrumental Brutalism — DESIGN.md + functional jobs. */

val Canvas = Color(0xFF0B0D0F)
val InstrumentBay = Color(0xFF121518)
val InstrumentPod = Color(0xFF181C20)
val Hairline = Color(0xFF262C34)
val PodStroke = Color(0xFF2F3742)
val DataReadout = Color(0xFFF2F4F7)
val TelemetryLabel = Color(0xFF8B939E)
val TelemetryMuted = Color(0xFF5C6470)

val Amber = Color(0xFFFF6B00)
val AmberPulse = Color(0xFFFF7A1A)
val Cyan = Color(0xFF38BDF8)
val GpsGreen = Color(0xFF22C55E)
val HazardRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFD4A017)

val Black = Canvas
val TrailGreen = GpsGreen
val TrailGreenBright = Color(0xFF4AE176)
val OnPrimary = Canvas
val OnPrimaryContainer = Color(0xFF351000)

val SurfaceContainerLowest = Color(0xFF070F17)
val SurfaceContainerLow = Color(0xFF141C25)
val SurfaceContainer = InstrumentPod
val SurfaceContainerHigh = Color(0xFF232B34)
val SurfaceContainerHighest = Color(0xFF2E353F)
val SurfaceVariant = Color(0xFF2E353F)

val OnSurface = DataReadout
val OnSurfaceVariant = TelemetryLabel
val InverseSurface = DataReadout
val OutlineVariant = Hairline
val Outline = Color(0xFFA98A7D)
val GrayMuted = TelemetryLabel
val GrayLabel = TelemetryMuted
val RedAlert = HazardRed
val DividerColor = Color(0xFF1F242B)

fun accentColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Amber)

val TouchTarget = 48.dp
val MarginEdge = 12.dp
val Gutter = 12.dp
val SectionGap = 24.dp
val AlpineRadius = 0.dp
val AlpineShape = RectangleShape
val ChipShape = RoundedCornerShape(0.dp)

private val AreteColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnPrimary,
    primaryContainer = Amber,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Cyan,
    onSecondary = Color(0xFF00354A),
    tertiary = GpsGreen,
    onTertiary = Color(0xFF003915),
    background = Canvas,
    onBackground = OnSurface,
    surface = Canvas,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = HazardRed,
    onError = DataReadout,
    errorContainer = Color(0xFF93000A)
)

@Composable
fun TrailMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AreteColorScheme,
        typography = AreteTypography,
        content = content
    )
}
