package com.bitse.geotreasure.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * P01 地标水滴图标（Compose 版，演示地图用）——严格对齐 design/prototype/index.html 的 dropPin()。
 *
 * - 倒立水滴（PIN_D，34×34 视口），尖端朝下、锚点在坐标点上；
 * - 深绿色底 #00634D、描边 #004B3A；
 * - 打卡后白色对勾；打卡前白色问号（按用户口径取深绿底，与高德路径 LandmarkPinBitmaps 同规格）。
 *
 * 与 core/map/LandmarkPinBitmaps 保持同一几何/配色，改动需两处同步。
 */
private val PIN_GREEN = Color(0xFF00634D)
private val PIN_GREEN_DARK = Color(0xFF004B3A)
private val PIN_WHITE = Color(0xFFFFFFFF)

/** 水滴轮廓（对应原型 PIN_D：左曲线 → 上半圆弧 → 右曲线，尖端 (17,32.4)） */
private fun DrawScope.dropPath(s: Float): Path = Path().apply {
    moveTo(17f * s, 32.4f * s)
    cubicTo(17f * s, 32.4f * s, 6.6f * s, 21.6f * s, 6.6f * s, 14.2f * s)
    arcTo(Rect(6.6f * s, 3.8f * s, 27.4f * s, 24.6f * s), 180f, 180f, false)
    cubicTo(27.4f * s, 21.6f * s, 17f * s, 32.4f * s, 17f * s, 32.4f * s)
    close()
}

@Composable
fun LandmarkPin(
    isDone: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember {
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PIN_WHITE)
    }
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 34f
        val drop = dropPath(s)
        // 原型 filter: drop-shadow(0 3px 6px rgba(32,41,35,.18)) 的简化版（无模糊，偏移 2px）
        translate(0f, 2f * s) {
            drawPath(drop, Color(0x2E202923))
        }
        drawPath(drop, PIN_GREEN)
        drawPath(drop, PIN_GREEN_DARK, style = Stroke(width = 1.2f * s))
        if (isDone) {
            // 白色对勾：M11.7 14.4 l3.6 3.6 l7.1 -7.8
            val check = Path().apply {
                moveTo(11.7f * s, 14.4f * s)
                lineTo(15.3f * s, 18f * s)
                lineTo(22.4f * s, 10.2f * s)
            }
            drawPath(
                check,
                PIN_WHITE,
                style = Stroke(width = 2.7f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
        } else {
            // 白色问号：原型 text x=17 y=19 font-size=13（这里以圆头中心垂直居中，视觉一致）
            val layout = textMeasurer.measure("?", textStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(17f * s - layout.size.width / 2f, 14.6f * s - layout.size.height / 2f),
            )
        }
    }
}
