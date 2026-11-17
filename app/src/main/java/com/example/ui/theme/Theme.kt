package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CarbonAmberColorScheme = darkColorScheme(
    primary = SignalAmber,
    onPrimary = OnAmber,
    primaryContainer = DeepAmberContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = WarmAmberGlow,
    onSecondary = OnAmber,
    secondaryContainer = ElevatedSlate,
    onSecondaryContainer = IvoryText,
    tertiary = EmeraldAvailable,
    onTertiary = OnAmber,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldAvailable,
    background = CarbonBackground,
    onBackground = IvoryText,
    surface = GraphiteSurface,
    onSurface = IvoryText,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = MutedSteel,
    surfaceContainerLowest = CarbonBackground,
    surfaceContainerLow = GraphiteSurface,
    surfaceContainer = ElevatedSlate,
    surfaceContainerHigh = HighestSlate,
    outline = HairlineOutline,
    outlineVariant = SubtleOutline,
    error = CrimsonError,
    onError = IvoryText,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonError
)

private val LotPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun LotPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CarbonAmberColorScheme,
        typography = LotPulseTypography,
        shapes = LotPulseShapes,
        content = content
    )
}
