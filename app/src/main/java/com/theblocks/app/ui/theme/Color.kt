package com.theblocks.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Block colors are intentionally not derived from the dynamic scheme: the board needs
 * six clearly distinguishable hues even if the wallpaper is monochrome.
 * The tones are picked to stay legible on both light and dark surfaces.
 */
val BlockPalette: List<Color> = listOf(
    Color(0xFFF0857A), // coral
    Color(0xFFE7B23C), // amber
    Color(0xFF6DBF8A), // green
    Color(0xFF66A9DE), // blue
    Color(0xFFA78BE3), // violet
    Color(0xFFEE8CB8), // pink
)

val BlockHighlight: Color = Color(0x33FFFFFF)

// Fallback palette (Android 11 and below, where dynamic color is unavailable).
val LightColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF5F4B8B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DEFF),
    onPrimaryContainer = Color(0xFF1B0E3C),
    secondary = Color(0xFF5C6B7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE8F2),
    onSecondaryContainer = Color(0xFF19232E),
    tertiary = Color(0xFF7B5560),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF2F121B),
    background = Color(0xFFFDF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFDF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4CF),
)

val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFCDBDFF),
    onPrimary = Color(0xFF2F1D5C),
    primaryContainer = Color(0xFF463273),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFFC3CFDD),
    onSecondary = Color(0xFF2C3844),
    secondaryContainer = Color(0xFF43505D),
    onSecondaryContainer = Color(0xFFDFE8F2),
    tertiary = Color(0xFFEFB9C7),
    onTertiary = Color(0xFF48262F),
    tertiaryContainer = Color(0xFF613B45),
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4CF),
    surfaceContainerLowest = Color(0xFF0E0D11),
    surfaceContainerLow = Color(0xFF1C1A20),
    surfaceContainer = Color(0xFF201E24),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
)
