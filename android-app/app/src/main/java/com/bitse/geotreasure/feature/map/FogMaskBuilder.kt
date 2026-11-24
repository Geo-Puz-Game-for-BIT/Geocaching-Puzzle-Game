package com.bitse.geotreasure.feature.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.bitse.geotreasure.core.location.localOffsetMeters
import com.bitse.geotreasure.core.model.CampusFences
import com.bitse.geotreasure.core.model.GeoPoint

/**
 * 迷雾 v6 调参常量（2026-11-24 按用户要求：「三块校区整体朦胧半透明、无分区感；
 * 校外恒不透明不分区的雾；打卡后仅对应地标上方散开」）。
 */
object FogMaskParams {
    /** 校内未探索雾不透明度（全校区统一朦胧半透明，底图隐约可辨） */
    const val UNEXPLORED_ALPHA = 0.50f

    /** 校界多边形填充的模糊半径（米）：界内外在此宽度内渐变过渡，不出现任何线/带状分区 */
    const val BOUNDARY_BLUR_M = 180f

    /** 打卡后该地标上方迷雾散开的半径（米）：中心全清 → 边缘回落雾浓度 */
    const val COMPLETED_REVEAL_RADIUS_M = 180.0

    /** 临时视野（实时定位）揭雾半径（米） */
    const val TEMP_VIEW_RADIUS_M = 60.0

    /** 单个历史探索点揭雾半径（米）；记录间隔由 FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS 提供 */
    const val EXPLORED_RADIUS_M = 60.0

    /** 遮罩地面分辨率（像素/米） */
    const val MASK_PX_PER_M = 0.6

    /** 遮罩地面范围在围界包络外扩（米）；之外遮罩不覆盖 = 云纹保持全不透明（校外恒雾） */
    const val MASK_PAD_M = 400.0
}

/** 遮罩地面框：以迷雾锚点为原点的局部米制坐标范围（东/北为正） */
data class FogMaskBounds(
    val minEastM: Double,
    val minNorthM: Double,
    val maxEastM: Double,
    val maxNorthM: Double,
)

/**
 * 迷雾 v6 遮罩构建器：在地面米制坐标系渲染一张 ALPHA_8 位图，
 * 像素值 = 该地面点的雾不透明度。渲染管线（自下而上）：
 * 1) 全图填 1（校外恒全遮挡优先）；
 * 2) 三区围界多边形「带大半径模糊」的 DST_OUT 衰减填充到未探索浓度（0.50）——
 *    界内外在同一blur过渡带内渐变，不出现任何线/带状分区（v5 的不透明羽化描边会
 *    在校内沿边界形成一圈不透明带，即用户反馈的「分区感」，已废弃该做法）；
 * 3) 已打卡地标：以地标为圆心径向散开（中心全清 → 边缘回落雾浓度），只动该地标上方；
 * 4) 历史探索点 / 临时视野（DST_OUT + 径向渐变）中心清晰露图、边缘回落雾浓度。
 *
 * 权重语义：DST_OUT 为乘法衰减（dst *= 1-src），探索点按 10m 记录间隔密集重叠后
 * 步行带中心自然趋近全清、边缘自然过渡，符合「取最大权重混合」的视觉效果。
 */
object FogMaskBuilder {

    fun build(
        anchor: GeoPoint,
        completedCenters: List<GeoPoint>,
        exploredCenters: List<GeoPoint>,
        liveScoutCenter: GeoPoint?,
    ): Pair<Bitmap, FogMaskBounds>? {
        val fences = CampusFences.allPolygons
        if (fences.isEmpty()) return null
        val ppm = FogMaskParams.MASK_PX_PER_M.toDouble()

        val fencePoints = fences.flatten().map { localOffsetMeters(anchor, it) }
        val pad = FogMaskParams.MASK_PAD_M
        val bounds = FogMaskBounds(
            minEastM = fencePoints.minOf { it.eastMeters } - pad,
            minNorthM = fencePoints.minOf { it.northMeters } - pad,
            maxEastM = fencePoints.maxOf { it.eastMeters } + pad,
            maxNorthM = fencePoints.maxOf { it.northMeters } + pad,
        )
        val widthPx = (((bounds.maxEastM - bounds.minEastM) * ppm).toInt()).coerceIn(64, 4096)
        val heightPx = (((bounds.maxNorthM - bounds.minNorthM) * ppm).toInt()).coerceIn(64, 4096)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)

        fun maskPx(point: GeoPoint): Pair<Float, Float> {
            val offset = localOffsetMeters(anchor, point)
            val x = ((offset.eastMeters - bounds.minEastM) * ppm).toFloat()
            val y = ((bounds.maxNorthM - offset.northMeters) * ppm).toFloat()
            return x to y
        }

        // 1) 校外恒 1：全图填满不透明
        val fill = Paint().apply { color = 0xFF000000.toInt() }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), fill)

        // 2) 校界内降雾到未探索浓度：带模糊的 DST_OUT 衰减填充（dst *= 1-src，衰减量 = 1-浓度）。
        //    模糊让界线两侧在 BOUNDARY_BLUR_M 宽度内平滑渐变——校外保持近 1、校内趋近浓度，
        //    全程无可见的线或带。不能用半透明色 SRC_OVER 直接填——位图已是全不透明，
        //    SRC_OVER 叠半透明色结果恒为 1，校内迷雾会完全挡死底图（2026-11-23 已验证）。
        val baseAlpha = (FogMaskParams.UNEXPLORED_ALPHA * 255).toInt().coerceIn(0, 255)
        val fenceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ((255 - baseAlpha) shl 24)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(
                (FogMaskParams.BOUNDARY_BLUR_M * ppm).toFloat(),
                BlurMaskFilter.Blur.NORMAL,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        fences.map { polygon ->
            Path().apply {
                polygon.forEachIndexed { index, point ->
                    val (x, y) = maskPx(point)
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }.forEach { canvas.drawPath(it, fenceFill) }

        // 3) 径向揭雾工具：中心全清 → 边缘回落雾浓度（DST_OUT 径向渐变）
        val revealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        fun reveal(center: GeoPoint, radiusM: Double) {
            val (cx, cy) = maskPx(center)
            val r = (radiusM * ppm).toFloat()
            if (r < 2f) return
            revealPaint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(0xFF000000.toInt(), 0x00000000),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, r, revealPaint)
        }

        // 4) 已打卡：仅该地标上方迷雾散开（径向全清 → 边缘回落雾浓度）
        completedCenters.forEach { reveal(it, FogMaskParams.COMPLETED_REVEAL_RADIUS_M) }

        // 5) 探索带 + 临时视野：径向渐变揭雾
        exploredCenters.forEach { reveal(it, FogMaskParams.EXPLORED_RADIUS_M) }
        liveScoutCenter?.let { reveal(it, FogMaskParams.TEMP_VIEW_RADIUS_M) }

        return bitmap to bounds
    }
}
