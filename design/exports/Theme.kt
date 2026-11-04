package com.bitse.geotreasure.ui.theme

// 由 design/tokens/tokens.json 生成 —— 改 token 请改 JSON，不要只改这里
// 用法：把本文件放到 ui/theme/Theme.kt，MaterialTheme(colorScheme = …, typography = …) 即可

import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// ---------- 品牌色 ----------
val Primary = Color(0xFF146C5A)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFC6E9DC)
val OnPrimaryContainer = Color(0xFF002018)
val Secondary = Color(0xFF4F6279)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFD6E3F5)
val OnSecondaryContainer = Color(0xFF0B1E33)
val Tertiary = Color(0xFF8A5A2B)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFF2DCC2)
val OnTertiaryContainer = Color(0xFF2E1804)

// ---------- 表面 ----------
val Background = Color(0xFFFBFDFC)
val OnBackground = Color(0xFF191C1B)
val Surface = Color(0xFFFBFDFC)
val OnSurface = Color(0xFF191C1B)
val SurfaceVariant = Color(0xFFDCE5E1)
val OnSurfaceVariant = Color(0xFF404A46)
val Outline = Color(0xFF707A75)
val OutlineVariant = Color(0xFFBFCBC5)

// ---------- 语义色 ----------
val Success = Color(0xFF1F7A4C)
val SuccessContainer = Color(0xFFC7EBD5)
val Warning = Color(0xFF8A5A00)
val WarningContainer = Color(0xFFFFDDA6)
val Error = Color(0xFFB3261E)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFF9DEDC)
val OnErrorContainer = Color(0xFF410E0B)
/** 碎片、成就、剧情解锁的奖励色；不要用于普通按钮 */
val RewardGold = Color(0xFFC8A34A)
/** 地图底色与允许半径圈（fill 用 copy(alpha = 0.12f)） */
val MapCanvas = Color(0xFFEDF1EF)
val RouteLine = Color(0xFF146C5A)

val LightColors = lightColorScheme(
    primary = Primary, onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer, onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary, onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer, onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary, onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer, onTertiaryContainer = OnTertiaryContainer,
    background = Background, onBackground = OnBackground,
    surface = Surface, onSurface = OnSurface,
    surfaceVariant = SurfaceVariant, onSurfaceVariant = OnSurfaceVariant,
    outline = Outline, outlineVariant = OutlineVariant,
    error = Error, onError = OnError,
    errorContainer = ErrorContainer, onErrorContainer = OnErrorContainer
)

// 第一版只做浅色；教师要求深色时再补
val DarkColors = darkColorScheme(
    primary = Color(0xFF8FDCB8), onPrimary = Color(0xFF00362A),
    primaryContainer = Color(0xFF005042), onPrimaryContainer = Color(0xFFC6E9DC),
    secondary = Color(0xFFB9C8DE), onSecondary = Color(0xFF22344B),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF191C1B), onBackground = Color(0xFFE1E3E2),
    surface = Color(0xFF191C1B), onSurface = Color(0xFFE1E3E2),
    outline = Color(0xFF8B958F), outlineVariant = Color(0xFF404A46)
)

// ---------- 字号 ----------
val GeoTreasureTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

// ---------- 圆角 ----------
val GeoTreasureShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ---------- 间距：只用 4dp 网格 ----------
object Dimens {
    val SpaceXXS = 2.dp
    val SpaceXS = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXL = 24.dp
    val SpaceXXL = 32.dp
    val ScreenMargin = 16.dp
    val TouchTargetMin = 48.dp
}
