package com.delminiusapps.rideforge.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val ForgeBackground = Color(0xFF0A0E1A)
val ForgeSurface = Color(0xFF131929)
val ForgeCard = Color(0xFF1A2035)
val ForgeSurfaceHigh = Color(0xFF222A42)
val ForgeBorder = Color(0xFF2A3250)
val ForgeBorderLight = Color(0xFF3A4565)
val ForgeBlue = Color(0xFF00D4FF)
val ForgeBlueDark = Color(0xFF0099CC)
val ForgeGreen = Color(0xFF00FF88)
val ForgeOrange = Color(0xFFFF8A00)
val ForgeYellow = Color(0xFFFFD600)
val ForgePurple = Color(0xFFA855F7)
val ForgeRed = Color(0xFFFF3B5C)
val ForgeText = Color(0xFFFFFFFF)
val ForgeMuted = Color(0xFF8A94A8)
val ForgeTabInactive = Color(0xFF4A5270)
val ForgeZone1 = Color(0xFF808080)
val ForgeZone2 = Color(0xFF3B82F6)
val ForgeZone3 = Color(0xFF22C55E)
val ForgeZone4 = Color(0xFFEAB308)
val ForgeZone5 = Color(0xFFF97316)
val ForgeZone6 = Color(0xFFEF4444)
val ForgeZone7 = Color(0xFFDC2626)

object RideForgeSpacing {
    val ScreenHorizontal = 16.dp
    val ScreenTop = 16.dp
    val ScreenBottom = 24.dp
    val Card = 16.dp
    val CardLarge = 20.dp
    val ItemGap = 14.dp
}

object RideForgeRadius {
    val Card = 16.dp
    val Button = 27.dp
    val Chip = 20.dp
    val Control = 12.dp
}

private val RideForgeColors: ColorScheme = darkColorScheme(
    primary = ForgeBlue,
    onPrimary = Color(0xFF001722),
    secondary = ForgeGreen,
    onSecondary = Color(0xFF001C0E),
    tertiary = ForgeOrange,
    background = ForgeBackground,
    onBackground = ForgeText,
    surface = ForgeSurface,
    onSurface = ForgeText,
    surfaceVariant = ForgeSurfaceHigh,
    onSurfaceVariant = ForgeMuted,
    outline = ForgeBorder,
    error = ForgeRed,
)

@Composable
fun RideForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RideForgeColors,
        content = content,
    )
}
