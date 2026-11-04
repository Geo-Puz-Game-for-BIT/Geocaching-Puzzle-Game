package com.bitse.geotreasure.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 字号沿用 design/tokens/tokens.json 的 typography.scale（Material 3 语义命名），
 * 中文字重用系统 sans-serif（多数国产机即思源黑体/苹方，视觉与 Noto Sans SC 一致）。
 */

private val fontFamily = FontFamily.SansSerif

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp,
        lineHeightStyle = lineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 21.sp,
        lineHeightStyle = lineHeightStyle
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp
    )
)

/** 原型里反复出现的小字（11.5 / 12.5 / 13.5），单独给出避免到处写 sp */
object AppText {
    val tiny = TextStyle(fontFamily = fontFamily, fontSize = 11.5.sp, lineHeight = 17.sp)
    val small = TextStyle(fontFamily = fontFamily, fontSize = 12.5.sp, lineHeight = 19.sp)
    val body = TextStyle(fontFamily = fontFamily, fontSize = 13.5.sp, lineHeight = 21.sp)
    val caption = TextStyle(fontFamily = fontFamily, fontSize = 10.5.sp, lineHeight = 14.sp)
    val tab = TextStyle(fontFamily = fontFamily, fontSize = 11.sp, lineHeight = 14.sp)
    val badge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 13.sp)
}
