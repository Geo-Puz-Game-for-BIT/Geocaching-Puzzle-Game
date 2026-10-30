package com.bitse.geotreasure.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B49),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F4E5),
    onPrimaryContainer = Color(0xFF082A1D),
    secondary = Color(0xFFD66A1F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1CC),
    tertiary = Color(0xFF176C86),
    background = Color(0xFFF7F8F3),
    surface = Color(0xFFFDFDF8),
    surfaceVariant = Color(0xFFE5E9E1),
    outline = Color(0xFF737972),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF83D5AF),
    onPrimary = Color(0xFF003825),
    secondary = Color(0xFFFFB685),
    tertiary = Color(0xFF81CEE9),
    background = Color(0xFF111512),
    surface = Color(0xFF181C19),
)

@Composable
fun GeoTreasureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
