package com.bitse.geotreasure.ui.theme

// 由 design/tokens/tokens.json 实测值生成（v1.8 / PR#8 整改：主色由旧 #146C5A 更正为 #00634D）
// 改 token 请改 JSON，不要只改这里
// 用法：把本文件放到 ui/theme/Theme.kt，MaterialTheme(colorScheme = …, typography = …) 即可

import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// ---------- 品牌色（= tokens.json color.brand） ----------
val Primary = Color(0xFF00634D)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFE6EDE5)
val OnPrimaryContainer = Color(0xFF123A2E)
val Secondary = Color(0xFF4A5B54)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFE6EDE5)
val OnSecondaryContainer = Color(0xFF123A2E)
val Tertiary = Color(0xFF8A6A32)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFF5E6C8)
val OnTertiaryContainer = Color(0xFF33260B)

// ---------- 表面（= tokens.json color.surface） ----------
val Background = Color(0xFFF8F7F2)
val OnBackground = Color(0xFF202923)
val Surface = Color(0xFFFFFEFA)
val OnSurface = Color(0xFF202923)
val SurfaceVariant = Color(0xFFE6EDE5)
val OnSurfaceVariant = Color(0xFF5D6962)
val Outline = Color(0xFF8A938D)
val OutlineVariant = Color(0xFFCFD8CF)
val Scrim = Color(0xFF000000)

// ---------- 语义色（= tokens.json color.semantic） ----------
val Success = Color(0xFF1F7A4C)
val OnSuccess = Color(0xFFFFFFFF)
val SuccessContainer = Color(0xFFD6EBDD)
val Warning = Color(0xFF8A6A32)
val WarningContainer = Color(0xFFF5E6C8)
val Error = Color(0xFFB3261E)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFF9DEDC)
val OnErrorContainer = Color(0xFF410E0B)
/** 碎片、成就、剧情解锁的奖励色；不要用于普通按钮 */
val RewardGold = Color(0xFFB58B45)
/** 地图底色 */
val MapCanvas = Color(0xFFEDF1EF)
/** 地图连线与允许半径描边（fill 用 #146C5A1F） */
val RouteLine = Color(0xFF146C5A)
val AllowedRadiusStroke = Color(0xFF146C5A)

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

// Token 未定义深色模式；教师要求深色时再从 tokens.json 扩展，不要沿用旧示例色

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
