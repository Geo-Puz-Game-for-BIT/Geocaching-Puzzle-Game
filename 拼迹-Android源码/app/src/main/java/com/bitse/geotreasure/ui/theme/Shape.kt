package com.bitse.geotreasure.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角：原型 --radius 14px（控件）/ --radius-lg 18px（卡片与头图容器）。
 * 手机 412dp 宽 ≈ 1 CSS px = 1dp，直接沿用。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

object Radius {
    val pill = RoundedCornerShape(percent = 50)
    val card = RoundedCornerShape(14.dp)
    val cardLarge = RoundedCornerShape(18.dp)
    val sheet = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val button = RoundedCornerShape(12.dp)
    val tile = RoundedCornerShape(8.dp)
    val thumb = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(999.dp)
}

/** 尺寸与间距：全部取自原型实测值，避免“大概差不多”导致与设计稿错位 */
object Dimen {
    val screenPadding = 16.dp
    val gutter = 8.dp

    val appBarHeight = 52.dp
    val pageHeaderHeight = 60.dp
    val tabBarHeight = 64.dp
    val statusBarHeight = 30.dp

    val buttonHeight = 48.dp
    val buttonHeightSmall = 36.dp
    val iconButton = 44.dp
    val minTouch = 44.dp

    val heroHeight = 252.dp
    val thumb = 64.dp
    val mapPinSize = 34.dp
    val shutter = 72.dp
    val fab = 48.dp
}
