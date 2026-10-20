package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DelivioYellow,
    onPrimary = TextOnYellow,
    primaryContainer = DelivioYellowDark,
    onPrimaryContainer = TextOnYellow,
    secondary = DelivioYellowContainer,
    onSecondary = TextOnYellow,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    error = DelivioRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = DelivioYellow,
    onPrimary = TextOnYellow,
    primaryContainer = DelivioYellowLight,
    onPrimaryContainer = TextOnYellow,
    secondary = DelivioYellowDark,
    onSecondary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    error = DelivioRed,
    onError = Color.White
)

@Composable
fun DelivioRiderTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
