package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AmpSyncDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = CyanMuted,
    onPrimaryContainer = TextPrimary,
    secondary = PartyMagenta,
    onSecondary = TextPrimary,
    secondaryContainer = MagentaMuted,
    onSecondaryContainer = TextPrimary,
    tertiary = SyncMint,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceHighest,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle.copy(alpha = 0.5f),
    error = CoralError,
    onError = TextPrimary
)

val AmpSyncShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AmpSyncTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AmpSyncDarkColorScheme,
        typography = AmpSyncTypography,
        shapes = AmpSyncShapes,
        content = content
    )
}
