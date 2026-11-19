package com.bitse.geotreasure.core.map

import androidx.core.graphics.createBitmap
import android.content.res.Resources
import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * P01 地标水滴图标的位图工厂（高德 marker 用）。
 *
 * 视觉口径（2026-11-19 用户给图）：深绿水滴 + 白色描边 + 头部白色内圆，
 * 圆内深绿问号（未打卡）/ 深绿对勾（已打卡），尖端下方一道柔和地面投影。
 *
 * 几何沿用原型 PIN_D（34 宽视口，尖端 (17,32.4)、圆心 (17,14.2)、半径 10.4）；
 * 画布加高到 38 单位，多出的 3.6 单位放投影，锚点 = 尖端 32.4 / 38（[ANCHOR_Y]）。
 *
 * 与 feature/map/LandmarkPin（Compose 版）保持同一几何/配色，改动需两处同步。
 */
object LandmarkPinBitmaps {

    /** 图标标准显示宽度（dp） */
    const val PIN_SIZE_DP = 34f

    /** 画布宽高比（宽 34 / 高 38），供 Compose 版与 marker 尺寸换算共用 */
    const val HEIGHT_RATIO = 38f / 34f

    /** 尖端在画布中的归一化纵坐标，作为 marker 锚点 */
    const val ANCHOR_Y = 32.4f / 38f

    /** 画布单位（与原型 34 视口同标尺） */
    const val CANVAS_W = 34f
    const val CANVAS_H = 38f
    const val CENTER_X = 17f
    const val CENTER_Y = 14.2f
    const val TIP_Y = 32.4f

    private const val DISC_R = 7.6f
    private const val OUTLINE_W = 2.8f

    private val BODY_FILL = 0xFF00634D.toInt()
    private val OUTLINE = 0xFFFFFFFF.toInt()
    private val GLYPH = 0xFF00634D.toInt()
    private val SHADOW = 0x33202923

    /**
     * 水滴轮廓（原型 PIN_D）：左曲线收拢到尖端 → 上半圆弧 → 右曲线回尖端。
     * 注意描边在填充之前画且更宽，形成「白色外圈 + 绿色内芯」。
     */
    fun buildDropPath(s: Float): Path = Path().apply {
        moveTo(CENTER_X * s, TIP_Y * s)
        cubicTo(CENTER_X * s, TIP_Y * s, 6.6f * s, 21.6f * s, 6.6f * s, CENTER_Y * s)
        // a10.4 10.4 0 1 1 20.8 0：圆心 (17,14.2) 半径 10.4，180°→0°（经顶部）
        arcTo(6.6f * s, 3.8f * s, 27.4f * s, 24.6f * s, 180f, 180f, false)
        cubicTo(27.4f * s, 21.6f * s, CENTER_X * s, TIP_Y * s, CENTER_X * s, TIP_Y * s)
        close()
    }

    /** 生成水滴位图；[widthPx] 为图标宽度，高度按 [HEIGHT_RATIO] 换算 */
    fun create(isDone: Boolean, widthPx: Int): Bitmap {
        val heightPx = (widthPx * HEIGHT_RATIO).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = widthPx / CANVAS_W

        // 1) 地面投影（尖端下方椭圆，柔和模糊）
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SHADOW
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(1.6f * s, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawOval(
            RectF(9.4f * s, 33.2f * s, 24.6f * s, 36.6f * s),
            shadow,
        )

        val dropPath = buildDropPath(s)
        // 2) 白色描边（先画宽描边，再填充绿色，露出外圈白边约 1.4 单位）
        canvas.drawPath(
            dropPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = OUTLINE_W * s
                color = OUTLINE
                strokeJoin = Paint.Join.ROUND
            },
        )
        // 3) 深绿主体
        canvas.drawPath(
            dropPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = BODY_FILL
            },
        )
        // 4) 头部白色内圆
        canvas.drawCircle(
            CENTER_X * s,
            CENTER_Y * s,
            DISC_R * s,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = OUTLINE
            },
        )

        // 5) 圆内符号：打卡后勾 / 未打卡问号（均深绿）
        if (isDone) {
            // 原型勾线 M11.7 14.4 l3.6 3.6 l7.1 -7.8，按 0.82 收进白圆内
            val k = 0.82f
            fun gx(x: Float) = (CENTER_X + (x - CENTER_X) * k) * s
            fun gy(y: Float) = (CENTER_Y + (y - CENTER_Y) * k) * s
            val check = Path().apply {
                moveTo(gx(11.7f), gy(14.4f))
                lineTo(gx(15.3f), gy(18f))
                lineTo(gx(22.4f), gy(10.2f))
            }
            canvas.drawPath(
                check,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * s
                    color = GLYPH
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                },
            )
        } else {
            canvas.drawText(
                "?",
                CENTER_X * s,
                19.0f * s,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GLYPH
                    textSize = 13.5f * s
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                },
            )
        }
        return bitmap
    }

    /** 图标宽度像素（34dp × density），供 BitmapDescriptorFactory 使用 */
    fun pinSizePx(): Int {
        val density = Resources.getSystem().displayMetrics.density
        return (PIN_SIZE_DP * density).toInt().coerceAtLeast(24)
    }
}
