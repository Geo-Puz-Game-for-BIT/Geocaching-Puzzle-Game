package com.bitse.geotreasure.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * P01 地标水滴图标（Compose 版，演示地图用）。
 *
 * 视觉口径（2026-11-19 用户给图）：深绿水滴 + 白描边 + 头部白色内圆，
 * 圆内深绿问号（未打卡）/ 深绿对勾（已打卡）+ 尖端下方柔和投影；
 * 几何沿用原型 PIN_D，画布 34×38（多 3.6 给投影），尖端在 32.4 处即锚点。
 *
 * 与 core/map/LandmarkPinBitmaps 同几何同配色，改动需两处同步。
 */
private val PIN_GREEN = Color(0xFF00634D)
private val PIN_WHITE = Color(0xFFFFFFFF)

private const val CANVAS_W = 34f
private const val CENTER_X = 17f
private const val CENTER_Y = 14.2f
private const val TIP_Y = 32.4f
private const val DISC_R = 7.6f
private const val OUTLINE_W = 2.8f

/** 水滴轮廓（对应原型 PIN_D：左曲线 → 上半圆弧 → 右曲线，尖端 (17,32.4)） */
private fun dropPath(s: Float): Path = Path().apply {
    moveTo(CENTER_X * s, TIP_Y * s)
    cubicTo(CENTER_X * s, TIP_Y * s, 6.6f * s, 21.6f * s, 6.6f * s, CENTER_Y * s)
    arcTo(Rect(6.6f * s, 3.8f * s, 27.4f * s, 24.6f * s), 180f, 180f, false)
    cubicTo(27.4f * s, 21.6f * s, CENTER_X * s, TIP_Y * s, CENTER_X * s, TIP_Y * s)
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
        TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PIN_GREEN)
    }
    Canvas(modifier = modifier.size(width = size, height = size * (38f / CANVAS_W))) {
        val s = this.size.width / CANVAS_W
        val drop = dropPath(s)

        // 1) 地面投影：两层椭圆近似柔边（外浅内深）
        drawOval(
            color = Color(0x1F202923),
            topLeft = Offset(8.4f * s, 32.6f * s),
            size = Size(17.2f * s, 4.6f * s),
        )
        drawOval(
            color = Color(0x2E202923),
            topLeft = Offset(9.6f * s, 33.2f * s),
            size = Size(14.8f * s, 3.4f * s),
        )
        // 2) 白色描边（宽描边在下、绿色填充在上，露出外圈白边）
        drawPath(drop, PIN_WHITE, style = Stroke(width = OUTLINE_W * s, join = StrokeJoin.Round))
        // 3) 深绿主体
        drawPath(drop, PIN_GREEN)
        // 4) 头部白色内圆
        drawCircle(PIN_WHITE, radius = DISC_R * s, center = Offset(CENTER_X * s, CENTER_Y * s))

        // 5) 圆内符号（深绿）
        if (isDone) {
            val k = 0.82f
            fun gx(x: Float) = (CENTER_X + (x - CENTER_X) * k) * s
            fun gy(y: Float) = (CENTER_Y + (y - CENTER_Y) * k) * s
            val check = Path().apply {
                moveTo(gx(11.7f), gy(14.4f))
                lineTo(gx(15.3f), gy(18f))
                lineTo(gx(22.4f), gy(10.2f))
            }
            drawPath(check, PIN_GREEN, style = Stroke(width = 2.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            val layout = textMeasurer.measure("?", textStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(CENTER_X * s - layout.size.width / 2f, CENTER_Y * s - layout.size.height / 2f),
            )
        }
    }
}
