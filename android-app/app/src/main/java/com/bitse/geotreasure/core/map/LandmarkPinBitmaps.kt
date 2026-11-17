package com.bitse.geotreasure.core.map

import androidx.core.graphics.createBitmap
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * P01 地标水滴图标的位图工厂（高德 marker 用）。
 *
 * 严格对齐 design/prototype/index.html 的 dropPin()（PIN_D，34×34 视口）：
 * - 形状：倒立水滴（尖端朝下为锚点），上半圆 + 两条对称曲线收拢到尖端；
 * - 底色：深绿 #00634D，描边 #004B3A（两种状态一致，按用户口径）；
 * - 打卡后：白色对勾（原型勾线 M11.7 14.4 l3.6 3.6 l7.1 -7.8，白 2.7 宽圆头圆角）；
 * - 打卡前：白色问号（用户口径：深绿底 + 白问号；原型 todo 态为白底绿问号，此处按用户要求取深绿底）。
 */
object LandmarkPinBitmaps {

    /** 原型 34×34 视口下图标标准显示宽度（dp） */
    const val PIN_SIZE_DP = 34f

    private const val BODY_FILL = 0xFF00634D.toInt()
    private const val BODY_STROKE = 0xFF004B3A.toInt()
    private const val MARK_COLOR = 0xFFFFFFFF.toInt()

    /**
     * 水滴轮廓。对应原型 PIN_D = 'M17 32.4C17 32.4 6.6 21.6 6.6 14.2a10.4 10.4 0 1 1 20.8 0C27.4 21.6 17 32.4 17 32.4Z'：
     * 左曲线收拢到尖端 → 上半圆弧（左端点经顶部到右端点）→ 右曲线回尖端。
     */
    fun buildDropPath(s: Float): Path = Path().apply {
        moveTo(17f * s, 32.4f * s)
        cubicTo(17f * s, 32.4f * s, 6.6f * s, 21.6f * s, 6.6f * s, 14.2f * s)
        // a10.4 10.4 0 1 1 20.8 0：圆心 (17,14.2) 半径 10.4，180°→0°（经顶部）
        arcTo(6.6f * s, 3.8f * s, 27.4f * s, 24.6f * s, 180f, 180f, false)
        cubicTo(27.4f * s, 21.6f * s, 17f * s, 32.4f * s, 17f * s, 32.4f * s)
        close()
    }

    /** 生成一张水滴位图，[sizePx] 为正方形边长（像素） */
    fun create(isDone: Boolean, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = sizePx / 34f

        val bodyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BODY_FILL
            style = Paint.Style.FILL
        }
        val bodyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BODY_STROKE
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * s
        }
        val dropPath = buildDropPath(s)
        canvas.drawPath(dropPath, bodyFill)
        canvas.drawPath(dropPath, bodyStroke)

        if (isDone) {
            // 白色对勾：M11.7 14.4 l3.6 3.6 l7.1 -7.8
            val check = Path().apply {
                moveTo(11.7f * s, 14.4f * s)
                lineTo(15.3f * s, 18f * s)
                lineTo(22.4f * s, 10.2f * s)
            }
            val checkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MARK_COLOR
                style = Paint.Style.STROKE
                strokeWidth = 2.7f * s
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(check, checkStroke)
        } else {
            // 白色问号：原型 <text x="17" y="19" font-size="13" font-weight="800">
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MARK_COLOR
                textSize = 13f * s
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("?", 17f * s, 19f * s, text)
        }
        return bitmap
    }

    /** 按 34dp 生成两张状态位图的边长（像素），供 BitmapDescriptorFactory 使用 */
    fun pinSizePx(): Int {
        val density = Resources.getSystem().displayMetrics.density
        return (PIN_SIZE_DP * density).toInt().coerceAtLeast(24)
    }
}
