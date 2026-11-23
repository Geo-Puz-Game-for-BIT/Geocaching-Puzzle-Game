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
import com.bitse.geotreasure.core.model.CampusRevealBlock
import com.bitse.geotreasure.core.model.GeoPoint

/**
 * 迷雾 v4（E「连续云层·步行开雾」第二版）调参常量。
 * 依据《README-接入说明.md》：校外雾 alpha 恒 1；校内未探索 0.30–0.40 起调；
 * 校界内侧羽化；临时/历史/已完成分别计算揭雾权重取最大混合；校外全遮挡优先。
 */
object FogMaskParams {
    /** 校内未探索雾不透明度（E 建议 0.30–0.40；验收=能连续辨认主路/路口/湖岸/建筑轮廓） */
    const val UNEXPLORED_ALPHA = 0.35f

    /** 校界内侧羽化宽度（米）：界内侧雾从 1 渐落到未探索浓度，避免露出校外底图 */
    const val BOUNDARY_FEATHER_M = 45f

    /** 已打卡整块开雾的边缘过渡宽度（米） */
    const val COMPLETE_FEATHER_M = 30f

    /** 临时视野（实时定位）揭雾半径（米） */
    const val TEMP_VIEW_RADIUS_M = 60.0

    /** 单个历史探索点揭雾半径（米）；记录间隔由 FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS 提供 */
    const val EXPLORED_RADIUS_M = 60.0

    /** 遮罩地面分辨率（像素/米） */
    const val MASK_PX_PER_M = 0.6

    /** 遮罩地面范围在围界包络外扩（米）；之外按校外恒 1 处理（遮罩不覆盖即不降低 alpha） */
    const val MASK_PAD_M = 250.0
}

/** 遮罩地面框：以迷雾锚点为原点的局部米制坐标范围（东/北为正） */
data class FogMaskBounds(
    val minEastM: Double,
    val minNorthM: Double,
    val maxEastM: Double,
    val maxNorthM: Double,
)

/**
 * 迷雾 v4 遮罩构建器：在地面米制坐标系渲染一张 ALPHA_8 位图，
 * 像素值 = 该地面点的雾不透明度。渲染管线（自下而上）：
 * 1) 全图填 1（校外恒全遮挡优先）；
 * 2) 三区围界多边形内填未探索浓度（0.35）；
 * 3) 围界描边 + 模糊 = 校界内侧羽化（界内从 1 渐落回 0.35，界外保持 1）；
 * 4) 已打卡 reveal block（DST_OUT + 模糊）整块点亮，边缘保留过渡；
 * 5) 历史探索点 / 临时视野（DST_OUT + 径向渐变）中心清晰露图、边缘回落雾浓度。
 *
 * 权重语义：DST_OUT 为乘法衰减（dst *= 1-src），探索点按 10m 记录间隔密集重叠后
 * 步行带中心自然趋近全清、边缘自然过渡，符合「取最大权重混合」的视觉效果。
 */
object FogMaskBuilder {

    fun build(
        anchor: GeoPoint,
        completedBlocks: List<CampusRevealBlock>,
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

        // 2) 校界内填未探索浓度
        val baseAlpha = (FogMaskParams.UNEXPLORED_ALPHA * 255).toInt().coerceIn(0, 255)
        val fenceFill = Paint().apply {
            color = (baseAlpha shl 24)
            style = Paint.Style.FILL
        }
        val fencePaths = fences.map { polygon ->
            Path().apply {
                polygon.forEachIndexed { index, point ->
                    val (x, y) = maskPx(point)
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
        fencePaths.forEach { canvas.drawPath(it, fenceFill) }

        // 3) 校界内侧羽化：界线描边（半宽 = 羽化宽）+ 模糊，界外已是 1 不会变淡
        val feather = FogMaskParams.BOUNDARY_FEATHER_M
        val featherStroke = Paint().apply {
            color = 0xFF000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = (2.0 * feather * ppm).toFloat()
            maskFilter = BlurMaskFilter((0.45 * feather * ppm).toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
        fencePaths.forEach { canvas.drawPath(it, featherStroke) }

        // 4) 已打卡整块开雾（DST_OUT + 模糊软边）
        val completeBlur = BlurMaskFilter(
            (0.5 * FogMaskParams.COMPLETE_FEATHER_M * ppm).toFloat(),
            BlurMaskFilter.Blur.NORMAL,
        )
        val completePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            maskFilter = completeBlur
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        completedBlocks.forEach { block ->
            if (block.vertices.isEmpty()) return@forEach
            val path = Path().apply {
                block.vertices.forEachIndexed { index, point ->
                    val (x, y) = maskPx(point)
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            canvas.drawPath(path, completePaint)
        }

        // 5) 探索带 + 临时视野：径向渐变揭雾（中心全清 → 边缘回落雾浓度）
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
        exploredCenters.forEach { reveal(it, FogMaskParams.EXPLORED_RADIUS_M) }
        liveScoutCenter?.let { reveal(it, FogMaskParams.TEMP_VIEW_RADIUS_M) }

        return bitmap to bounds
    }
}
