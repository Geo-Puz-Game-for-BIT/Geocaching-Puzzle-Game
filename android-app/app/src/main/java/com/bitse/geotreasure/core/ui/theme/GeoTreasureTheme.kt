package com.bitse.geotreasure.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 统一主题入口（唯一）。
 *
 * 原有函数签名 [GeoTreasureTheme(darkTheme, content)] 保持不变，MainActivity 调用处无需改动。
 * 色值改为引用 Color.kt 的设计 token（primary == #00634D，与 design/tokens/tokens.json 一致）；
 * 深色方案沿用原实现（本轮设计稿仅覆盖浅色，不新造深色 token）。
 *
 * 2026-11-12：默认值由 isSystemInDarkTheme() 改为 false——原型（.fig/PDF v3.1）仅提供浅色
 * 设计，系统深色模式会触发 DarkColors 导致真机背景发黑，与原型不符；强制浅色保持一致。
 */
private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF83D5AF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003825),
    secondary = androidx.compose.ui.graphics.Color(0xFFFFB685),
    tertiary = androidx.compose.ui.graphics.Color(0xFF81CEE9),
    background = androidx.compose.ui.graphics.Color(0xFF111512),
    surface = androidx.compose.ui.graphics.Color(0xFF181C19),
)

@Composable
fun GeoTreasureTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
