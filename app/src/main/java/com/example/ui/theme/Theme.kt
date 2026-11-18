package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AlifEditorialLightColorScheme = lightColorScheme(
    primary = SignalAmber,
    onPrimary = OnAmber,
    primaryContainer = DeepAmberContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = WarmAmberGlow,
    onSecondary = Color.White,
    secondaryContainer = ElevatedSlate,
    onSecondaryContainer = IvoryText,
    tertiary = EmeraldAvailable,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldAvailable,
    background = CarbonBackground,
    onBackground = IvoryText,
    surface = GraphiteSurface,
    onSurface = IvoryText,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = MutedSteel,
    surfaceContainerLowest = GraphiteSurface,
    surfaceContainerLow = Color(0xFFFAF9F7),
    surfaceContainer = GraphiteSurface,
    surfaceContainerHigh = HighestSlate,
    outline = HairlineOutline,
    outlineVariant = SubtleOutline,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonError
)

private val LotPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LotPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AlifEditorialLightColorScheme,
        typography = LotPulseTypography,
        shapes = LotPulseShapes,
        content = content
    )
}
