package com.bitse.geotreasure.feature.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bitse.geotreasure.core.location.localOffsetMeters
import com.bitse.geotreasure.core.model.CampusFences
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * P01 探索页 · 迷雾叠加层 v7.0（国风淡彩云朵实例层，2026-11-24 五点定案落地）。
 *
 * 视觉（离屏渲染原型 dist/迷雾方案-国风淡彩云朵实例 已核对认可，Kotlin 端逐参数复刻）：
 * 1) 蓬松层叠云朵：每朵云 = 预生成 sprite（底层橄榄灰影斑 ×n + 中层淡米白/浅绿体斑 ×6~8
 *    + 顶部奶白光斑 ×2~3，blob 径向渐变 smoothstep 软化无硬边），大小错落、互相叠加；
 * 2) 淡米白 + 浅橄榄低饱和（#A9B49C / #F5EFDE / #E3EAD1 / #FDFBF0），底图从云隙隐约透出；
 * 3) 校内薄雾 / 校外浓雾按「围界距离」软混合（180m 过渡带，无分区线）：
 *    校内云 alpha 0.55（毯 ×0.45）、校外 0.92（毯 ×0.32），只遮挡不破坏底图；
 * 4) 局部擦除：已打卡地标（180m 中心全清）+ 历史探索带 / 临时视野（60m）对每朵云做
 *    260m 软边消散——云朵由近及远渐隐露底图，不是生硬圆形蒙版；
 * 5) 分片独立控制：[fogSliceVisible] 按 CampusZone 逐区乘系数（默认全 1 不干预）。
 *
 * 结构：启动后台生成 sprite 位图池（24 云形 + 8 雾毯形，mulberry32 确定性参数）→
 * 围界包络外扩 2200m 的米制网格确定性布点（雾毯 912m 格 / 云 403m 格，相对锚点固定）→
 * 揭雾状态变化时重算每实例有效 alpha → 绘制时在 rotate(bearing) 变换内逐实例 drawImage
 * （与遮罩时代同款地理锚定帧，随地图平移/缩放/旋转；无平移无拼接，云场即世界）。
 * 极远 zoom（云 <16px）回退平涂雾色防斑点；打卡飘散小云与 marker 小孔机制保留 v3.2 原样。
 */

// —— 布局/动画常量（对齐原型与 v3.2 保留部分）——
private const val REVEAL_DURATION_MS = 1200
private const val REVEAL_DELAY_MS = 60
private const val PUFF_DURATION_MS = 1900
private const val PUFF_COUNT = 5
private const val PUFF_RISE_M = 140f          // 飘散上升高度（地面米）
private const val PUFF_SPREAD_M = 260f        // 飘散横向距离（地面米）
private const val PUFF_CLOUD_M = 90f          // 飘散小云地面尺寸（米）
private const val MARKER_HOLE_DP = 30f        // 高德路径：marker 透出小孔半径（dp）

// —— 云实例层参数（与离屏原型 render_fog_slice.py 逐值对齐）——
private const val SCALE_M = 380f              // 云朵地面尺寸（米）；sprite 视觉核约 60%
private const val CAMPUS_A = 0.55f            // 校内云不透明度（薄雾，底图隐约透出）
private const val FOG_A = 0.92f               // 校外云不透明度（浓雾）
private const val BLANKET_CAMPUS_K = 0.45f    // 校内雾毯系数
private const val BLANKET_FOG_K = 0.32f       // 校外雾毯系数
private const val FIELD_PAD_M = 2200.0        // 云场在围界包络外扩（米）
private const val BOUNDARY_BLEND_M = 180f     // 校界内外浓度软混合带宽（米）
private const val REVEAL_SOFT_M = 260f        // 揭雾软边带宽（米）：r 内全消 → r..r+soft 线性恢复
private const val MIN_CLOUD_PX = 16f          // 云朵最小屏幕像素：低于此回退平涂防斑点
private const val WASH_MAX_A = 0.55f          // 远 zoom 平涂雾色不透明度
private const val CLOUD_SPRITE_PX = 512
private const val BLANKET_SPRITE_PX = 384
private const val CLOUD_POOL = 24
private const val BLANKET_POOL = 8
private const val FIELD_SEED = 20261124

// 配色参照 E《fog-cloud-texture.png》（打卡飘散小云沿用）：
private val FogShadow = Color(0xFF7C9068)
private val FogBody = Color(0xFFC2CFA9)
private val FogHighlight = Color(0xFFF4EFDC)

// 国风淡彩云朵调色板（离屏原型 PAL，已获认可）：影 / 体×2 / 光
private val CloudShadow = Color(0xFFA9B49C)
private val CloudBody1 = Color(0xFFF5EFDE)
private val CloudBody2 = Color(0xFFE3EAD1)
private val CloudLight = Color(0xFFFDFBF0)

private data class FogEllipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val alpha: Float)
private data class FogSpark(val cx: Float, val cy: Float, val r: Float, val alpha: Float)
private data class FogShape(
    val shadow: List<FogEllipse>,
    val body: List<FogEllipse>,
    val highlight: List<FogEllipse>,
    val sparks: List<FogSpark>,
    val curl: Boolean = false,
)

/** 5 种云形（原型 220×160 空间），仅用于打卡飘散动画 */
private val FOG_SHAPES = listOf(
    // 0 蓬松团云
    FogShape(
        shadow = listOf(FogEllipse(112f, 124f, 86f, 15f, .32f)),
        body = listOf(
            FogEllipse(64f, 98f, 33f, 33f, .86f), FogEllipse(100f, 80f, 44f, 44f, .86f),
            FogEllipse(140f, 86f, 37f, 37f, .86f), FogEllipse(172f, 100f, 26f, 26f, .86f),
            FogEllipse(110f, 104f, 88f, 26f, .86f),
        ),
        highlight = listOf(
            FogEllipse(88f, 70f, 24f, 24f, .80f), FogEllipse(120f, 62f, 27f, 27f, .80f),
            FogEllipse(150f, 78f, 19f, 19f, .80f), FogEllipse(116f, 86f, 64f, 13f, .80f),
        ),
        sparks = listOf(FogSpark(106f, 54f, 12f, .55f), FogSpark(136f, 62f, 8f, .38f)),
    ),
    // 1 扁宽长云
    FogShape(
        shadow = listOf(FogEllipse(110f, 112f, 94f, 12f, .30f)),
        body = listOf(
            FogEllipse(52f, 90f, 25f, 25f, .86f), FogEllipse(90f, 80f, 33f, 33f, .86f),
            FogEllipse(126f, 82f, 30f, 30f, .86f), FogEllipse(160f, 92f, 23f, 23f, .86f),
            FogEllipse(108f, 98f, 92f, 22f, .86f),
        ),
        highlight = listOf(
            FogEllipse(78f, 70f, 18f, 18f, .78f), FogEllipse(110f, 64f, 21f, 21f, .78f),
            FogEllipse(140f, 74f, 15f, 15f, .78f), FogEllipse(110f, 80f, 70f, 10f, .78f),
        ),
        sparks = listOf(FogSpark(100f, 54f, 9f, .50f)),
    ),
    // 2 高积云
    FogShape(
        shadow = listOf(FogEllipse(106f, 126f, 70f, 14f, .30f)),
        body = listOf(
            FogEllipse(86f, 94f, 36f, 36f, .86f), FogEllipse(126f, 90f, 32f, 32f, .86f),
            FogEllipse(106f, 58f, 29f, 29f, .86f), FogEllipse(104f, 104f, 68f, 20f, .86f),
        ),
        highlight = listOf(
            FogEllipse(92f, 82f, 20f, 20f, .80f), FogEllipse(126f, 80f, 17f, 17f, .80f),
            FogEllipse(106f, 46f, 18f, 18f, .80f), FogEllipse(106f, 62f, 34f, 9f, .80f),
        ),
        sparks = listOf(FogSpark(100f, 36f, 10f, .55f)),
    ),
    // 3 小卷云
    FogShape(
        shadow = listOf(FogEllipse(116f, 106f, 62f, 10f, .28f)),
        body = listOf(
            FogEllipse(84f, 86f, 22f, 22f, .86f), FogEllipse(116f, 78f, 27f, 27f, .86f),
            FogEllipse(146f, 88f, 19f, 19f, .86f), FogEllipse(116f, 94f, 60f, 17f, .86f),
        ),
        highlight = listOf(
            FogEllipse(106f, 70f, 16f, 16f, .78f), FogEllipse(134f, 80f, 12f, 12f, .78f),
            FogEllipse(120f, 80f, 42f, 8f, .78f),
        ),
        sparks = listOf(FogSpark(102f, 60f, 8f, .50f)),
    ),
    // 4 叠云
    FogShape(
        shadow = listOf(FogEllipse(112f, 128f, 82f, 13f, .30f)),
        body = listOf(
            FogEllipse(60f, 100f, 28f, 28f, .86f), FogEllipse(96f, 88f, 38f, 38f, .86f),
            FogEllipse(136f, 92f, 32f, 32f, .86f), FogEllipse(166f, 102f, 22f, 22f, .86f),
            FogEllipse(110f, 108f, 86f, 23f, .86f),
            FogEllipse(126f, 58f, 21f, 21f, .86f), FogEllipse(152f, 70f, 14f, 14f, .86f),
        ),
        highlight = listOf(
            FogEllipse(86f, 78f, 21f, 21f, .80f), FogEllipse(116f, 72f, 23f, 23f, .80f),
            FogEllipse(124f, 48f, 13f, 13f, .80f), FogEllipse(112f, 86f, 58f, 11f, .80f),
        ),
        sparks = listOf(FogSpark(104f, 62f, 10f, .55f), FogSpark(126f, 42f, 7f, .45f)),
    ),
)

private val PUFF_DIRS = floatArrayOf(-1.15f, -.6f, .05f, .62f, 1.1f)

private class HoleAnim {
    val reveal = Animatable(0f)
    val puff = Animatable(0f)
}

/**
 * 迷雾地理锚定帧：锚点屏幕坐标 + 当前缩放下的每米像素数（纵向可单独指定，演示路径各向异性投影用）。
 * bearingDegrees = 地理东向在屏幕上的方向角（度，顺时针；地图未旋转时为 0）。
 * 高德路径实测自「锚点→正东参考点」的屏幕向量，随旋转手势实时更新；
 * FogOverlay 用它把云实例场整体绕锚点旋转，使迷雾始终「套在地图上」。
 */
data class FogGeoFrame(
    val originScreen: Offset,
    val pxPerMeter: Float,
    val pxPerMeterY: Float = pxPerMeter,
    val bearingDegrees: Float = 0f,
)

/** 迷雾地理锚点：全部地标的包围盒中心；空表时回退良乡校区近似中心 */
fun fogAnchorPoint(landmarks: List<Landmark>): GeoPoint {
    if (landmarks.isEmpty()) return GeoPoint(latitude = 39.734, longitude = 116.172)
    val lats = landmarks.map { it.point.latitude }
    val lngs = landmarks.map { it.point.longitude }
    return GeoPoint(
        latitude = (lats.min() + lats.max()) / 2.0,
        longitude = (lngs.min() + lngs.max()) / 2.0,
    )
}

// —— 确定性随机（mulberry32，与离屏原型逐位对齐）——

private fun imul32(a: Long, b: Long): Long {
    val al = a and 0xFFFFL
    val ah = (a ushr 16) and 0xFFFFL
    val bl = b and 0xFFFFL
    val bh = (b ushr 16) and 0xFFFFL
    val r = al * bl + (((ah * bl + al * bh) and 0xFFFFL) shl 16)
    return r and 0xFFFFFFFFL
}

/** mulberry32 PRNG：布点与 sprite 形态的确定性种子源 */
private class Mulberry32(seed: Int) {
    private var state = seed.toLong() and 0xFFFFFFFFL

    /** 返回 [0,1) 均匀随机数 */
    fun next(): Float {
        state = (state + 0x6D2B79F5L) and 0xFFFFFFFFL
        var t = state
        t = t xor (t ushr 15)
        t = imul32(t, t or 1L)
        val u = t xor (t ushr 7)
        val sum = (t + imul32(u, u or 61L)) and 0xFFFFFFFFL
        t = t xor sum
        t = (t xor (t ushr 14)) and 0xFFFFFFFFL
        return t.toFloat() / 4294967296f
    }
}

private fun sstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpf(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// —— sprite 生成（影/体/光三层斑加权，与离屏原型 render_cloud_sprite 同款数学）——

/**
 * 单张 sprite 的逐像素累积器：img 累加 alpha 权重，wr/wg/wb 累加加权色；
 * 最终 alpha = min(img,1)、色 = 加权平均 → 预乘写入 ARGB_8888。
 * blob 只在其包围盒内循环（半径 ≤0.3·S，比全图循环快一个量级）。
 */
private class SpriteAccumulator(val size: Int) {
    private val img = FloatArray(size * size)
    private val wr = FloatArray(size * size)
    private val wg = FloatArray(size * size)
    private val wb = FloatArray(size * size)

    fun blob(cx: Float, cy: Float, r: Float, color: Color, alphaK: Float) {
        if (r < 0.5f || alphaK <= 0f) return
        val cr = color.red; val cg = color.green; val cb = color.blue
        val x0 = (cx - r).toInt().coerceAtLeast(0)
        val x1 = (cx + r).toInt().coerceAtMost(size - 1)
        val y0 = (cy - r).toInt().coerceAtLeast(0)
        val y1 = (cy + r).toInt().coerceAtMost(size - 1)
        for (py in y0..y1) {
            val dy = py - cy
            val dy2 = dy * dy
            val rowBase = py * size
            for (px in x0..x1) {
                val dx = px - cx
                val d = kotlin.math.sqrt(dx * dx + dy2) / r
                if (d >= 1f) continue
                // 分段径向渐变（0,1)(0.45,.75)(0.78,.28)(1,0) + smoothstep 软化折点（消圈痕）
                val lin = when {
                    d < 0.45f -> lerpf(1f, 0.75f, d / 0.45f)
                    d < 0.78f -> lerpf(0.75f, 0.28f, (d - 0.45f) / 0.33f)
                    else -> lerpf(0.28f, 0f, (d - 0.78f) / 0.22f)
                }
                val a = lin * lin * (3f - 2f * lin) * alphaK
                if (a <= 0f) continue
                val i = rowBase + px
                img[i] += a
                wr[i] += a * cr
                wg[i] += a * cg
                wb[i] += a * cb
            }
        }
    }

    fun toBitmap(): android.graphics.Bitmap {
        val px = size
        val pixels = IntArray(px * px)
        for (i in pixels.indices) {
            val w = img[i]
            if (w <= 1e-6f) continue
            val al = w.coerceAtMost(1f)
            val r = (wr[i] / w).coerceIn(0f, 1f)
            val g = (wg[i] / w).coerceIn(0f, 1f)
            val b = (wb[i] / w).coerceIn(0f, 1f)
            pixels[i] = ((al * 255f).toInt() shl 24) or   // 预乘：色 × alpha，避免缩放透明黑渗色
                ((r * al * 255f).toInt().coerceIn(0, 255) shl 16) or
                ((g * al * 255f).toInt().coerceIn(0, 255) shl 8) or
                (b * al * 255f).toInt().coerceIn(0, 255)
        }
        val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, px, 0, 0, px, px)
        return bmp
    }
}

/** 一朵积云 sprite：n=6~8 体斑随机成团 → 底影斑 ×n + 体斑 ×n（交替双色）+ 顶光斑 ×2~3 */
private fun renderCloudSpriteBitmap(seed: Int): android.graphics.Bitmap {
    val rnd = Mulberry32(seed)
    val s = CLOUD_SPRITE_PX
    val r = s * 0.30f
    val n = 6 + (rnd.next() * 3f).toInt()
    val bx = FloatArray(n)
    val by = FloatArray(n)
    val br = FloatArray(n)
    for (i in 0 until n) {
        val ang = rnd.next() * 2f * kotlin.math.PI.toFloat()
        val rr = kotlin.math.sqrt(rnd.next())
        bx[i] = s / 2f + kotlin.math.cos(ang) * r * rr
        by[i] = s / 2f + kotlin.math.sin(ang) * r * rr * 0.72f
        br[i] = r * (0.34f + rnd.next() * 0.24f)
    }
    val acc = SpriteAccumulator(s)
    for (i in 0 until n) {                                     // 影：整体下坠偏移的橄榄灰
        acc.blob(bx[i] * 1.02f, by[i] * 1.02f + r * 0.30f, br[i] * 1.02f, CloudShadow, 0.14f)
    }
    for (i in 0 until n) {                                     // 体：交替淡米白 / 浅绿
        acc.blob(bx[i], by[i], br[i], if (i % 2 == 0) CloudBody1 else CloudBody2, 0.50f)
    }
    val lights = 2 + (rnd.next() * 2f).toInt()                 // 光：偏西北的奶白顶光
    repeat(lights) {
        val k = (rnd.next() * n).toInt().coerceIn(0, n - 1)
        acc.blob(bx[k] - r * 0.18f, by[k] - r * 0.26f, br[k] * 0.78f, CloudLight, 0.55f)
    }
    return acc.toBitmap()
}

/** 雾毯 sprite：4 团低浓度体斑 + 中央极淡影斑，铺出连续雾海基底 */
private fun renderBlanketSpriteBitmap(seed: Int): android.graphics.Bitmap {
    val rnd = Mulberry32(seed)
    val s = BLANKET_SPRITE_PX
    val r = s * 0.42f
    val acc = SpriteAccumulator(s)
    for (i in 0 until 4) {
        val ang = rnd.next() * 2f * kotlin.math.PI.toFloat()
        val rr = kotlin.math.sqrt(rnd.next()) * 0.6f
        val rad = r * (0.5f + rnd.next() * 0.2f)
        acc.blob(s / 2f + kotlin.math.cos(ang) * r * rr, s / 2f + kotlin.math.sin(ang) * r * rr, rad,
            if (i % 2 == 0) CloudBody1 else CloudBody2, 0.26f)
    }
    acc.blob(s / 2f, s / 2f, r, CloudShadow, 0.06f)
    return acc.toBitmap()
}

/** sprite 位图池：启动后台生成一次（24 云形 + 8 毯形，约 30MB ARGB，与回退噪声图时代同级开销） */
private class CloudSpritePool {
    val clouds = ArrayList<ImageBitmap>(CLOUD_POOL)
    val blankets = ArrayList<ImageBitmap>(BLANKET_POOL)
}

private fun buildSpritePool(): CloudSpritePool {
    val pool = CloudSpritePool()
    repeat(CLOUD_POOL) { k -> pool.clouds.add(renderCloudSpriteBitmap(1000 + k * 7717).asImageBitmap()) }
    repeat(BLANKET_POOL) { k -> pool.blankets.add(renderBlanketSpriteBitmap(5000 + k).asImageBitmap()) }
    return pool
}

// —— 云场：米制确定性网格布点 + 围界浓度分类 ——

/**
 * 云实例：相对迷雾锚点的米制坐标（东/北为正）。
 * campusFactor ∈ [0,1]：0=校内薄雾 1=校外浓雾（按到围界距离在 180m 带内软混合，无分区线）。
 * zone = 所属校区（界内为所在区、界外为最近区），供分片显隐乘系数。
 */
private class CloudInstance(
    val east: Double,
    val north: Double,
    val sizeM: Float,
    val spriteIndex: Int,
    val blanket: Boolean,
    val zone: CampusZone?,
    val campusFactor: Float,
    val baseAlpha: Float,
)

private class CloudField(val blankets: List<CloudInstance>, val clouds: List<CloudInstance>)

private class FenceGeometry(
    val zones: List<CampusZone>,
    val polys: List<List<Pair<Double, Double>>>,
) {
    /** 返回 (所在/最近区, 校外程度 0..1)；界内 0，界外按距离 180m 软混合 */
    fun classify(x: Double, y: Double): Pair<CampusZone?, Float> {
        for (k in polys.indices) {
            if (pointInPolygon(x, y, polys[k])) return zones[k] to 0f
        }
        var best = Double.MAX_VALUE
        var bestZone: CampusZone? = null
        for (k in polys.indices) {
            val d = distanceToPolygon(x, y, polys[k])
            if (d < best) {
                best = d
                bestZone = zones[k]
            }
        }
        return bestZone to sstep((best / BOUNDARY_BLEND_M).toFloat())
    }

    fun pointInPolygon(x: Double, y: Double, poly: List<Pair<Double, Double>>): Boolean {
        var inside = false
        for (i in poly.indices) {
            val (x1, y1) = poly[i]
            val (x2, y2) = poly[(i + 1) % poly.size]
            if ((y1 > y) != (y2 > y) && x < (x2 - x1) * (y - y1) / (y2 - y1) + x1) inside = !inside
        }
        return inside
    }

    fun distanceToPolygon(x: Double, y: Double, poly: List<Pair<Double, Double>>): Double {
        var best = Double.MAX_VALUE
        for (i in poly.indices) {
            val (x1, y1) = poly[i]
            val (x2, y2) = poly[(i + 1) % poly.size]
            val vx = x2 - x1
            val vy = y2 - y1
            val len2 = vx * vx + vy * vy
            var t = if (len2 <= 1e-9) 0.0 else ((x - x1) * vx + (y - y1) * vy) / len2
            t = t.coerceIn(0.0, 1.0)
            val dx = x - (x1 + t * vx)
            val dy = y - (y1 + t * vy)
            val d = hypot(dx, dy)
            if (d < best) best = d
        }
        return best
    }
}

private object CloudFieldBuilder {
    /** 围界包络外扩 2200m 的双层确定性布点：雾毯（912m 格）铺底 + 云朵（403m 格）层叠 */
    fun build(anchor: GeoPoint): CloudField? {
        if (CampusFences.zones.isEmpty()) return null
        val geo = FenceGeometry(
            zones = CampusFences.zones.keys.toList(),
            polys = CampusFences.zones.values.map { poly ->
                poly.map {
                    val o = localOffsetMeters(anchor, it)
                    o.eastMeters to o.northMeters
                }
            },
        )
        val minE = geo.polys.minOf { l -> l.minOf { it.first } } - FIELD_PAD_M
        val maxE = geo.polys.maxOf { l -> l.maxOf { it.first } } + FIELD_PAD_M
        val minN = geo.polys.minOf { l -> l.minOf { it.second } } - FIELD_PAD_M
        val maxN = geo.polys.maxOf { l -> l.maxOf { it.second } } + FIELD_PAD_M

        val blankets = ArrayList<CloudInstance>()
        val clouds = ArrayList<CloudInstance>()
        val bcell = SCALE_M * 2.4f
        val cell = SCALE_M * 1.06f

        var idx = 0
        var gy = minN
        while (gy < maxN) {
            var gx = minE
            while (gx < maxE) {
                val rnd = Mulberry32(FIELD_SEED + idx * 7919)
                idx++
                val x = gx + (rnd.next() - 0.5) * bcell * 0.7
                val y = gy + (rnd.next() - 0.5) * bcell * 0.7
                val (zone, cf) = geo.classify(x, y)
                val seed = (rnd.next() * 1e9).toInt()
                blankets.add(
                    CloudInstance(
                        east = x, north = y, sizeM = bcell * 1.15f,
                        spriteIndex = seed % BLANKET_POOL, blanket = true,
                        zone = zone, campusFactor = cf,
                        baseAlpha = lerpf(CAMPUS_A * BLANKET_CAMPUS_K, FOG_A * BLANKET_FOG_K, cf),
                    ),
                )
                gx += bcell
            }
            gy += bcell
        }

        idx = 0
        gy = minN
        while (gy < maxN) {
            var gx = minE
            while (gx < maxE) {
                val rnd = Mulberry32(FIELD_SEED + 100000 + idx * 104729)
                idx++
                val x = gx + (rnd.next() - 0.5) * cell * 0.85
                val y = gy + (rnd.next() - 0.5) * cell * 0.85
                val (zone, cf) = geo.classify(x, y)
                val seed = (rnd.next() * 1e9).toInt()
                clouds.add(
                    CloudInstance(
                        east = x, north = y, sizeM = SCALE_M,
                        spriteIndex = seed % CLOUD_POOL, blanket = false,
                        zone = zone, campusFactor = cf,
                        baseAlpha = lerpf(CAMPUS_A, FOG_A, cf),
                    ),
                )
                gx += cell
            }
            gy += cell
        }
        return CloudField(blankets, clouds)
    }
}

// —— 揭雾：每实例软边消散因子 ——

private class RevealCenter(val east: Double, val north: Double, val radiusM: Float)

/**
 * 揭雾状态 → 每实例有效 alpha 数组（base × 揭雾软边因子；260m 带内由近及远渐隐，
 * 中心全清 → 边缘回落雾浓度，与 v6 遮罩时代同一语义、以「云朵散开」的形态呈现）。
 */
private fun computeEffectiveAlphas(field: CloudField, centers: List<RevealCenter>): FloatArray {
    val all = field.blankets + field.clouds
    return FloatArray(all.size) { i ->
        val inst = all[i]
        var f = 1f
        for (c in centers) {
            val d = hypot(inst.east - c.east, inst.north - c.north)
            val t = ((d - c.radiusM) / REVEAL_SOFT_M).toFloat().coerceIn(0f, 1f)
            if (t < f) f = t
            if (f <= 0f) break
        }
        f
    }
}

@Composable
internal fun FogOverlay(
    landmarks: List<Landmark>,
    completedLandmarkIds: Set<String>,
    exploredCenters: List<GeoPoint>,
    liveScoutCenter: GeoPoint?,
    holeScreenPos: (Landmark) -> Offset?,
    markerScreenPos: Map<String, Offset> = emptyMap(),
    geoFrame: FogGeoFrame? = null,
    fogSliceVisible: Map<CampusZone, Float> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    // sprite 位图池：启动后台生成一次（影/体/光三层斑程序化绘制，非贴图）
    val spritePool by produceState<CloudSpritePool?>(initialValue = null) {
        value = withContext(Dispatchers.Default) {
            runCatching { buildSpritePool() }.getOrNull()
        }
    }

    // 云场：锚点（地标包围盒中心）变化时后台重建
    val anchor = remember(landmarks) { fogAnchorPoint(landmarks) }
    val field by produceState<CloudField?>(initialValue = null, anchor) {
        value = withContext(Dispatchers.Default) {
            runCatching { CloudFieldBuilder.build(anchor) }.getOrNull()
        }
    }

    // 揭雾中心：已打卡（180m 中心全清）/ 历史探索带与临时视野（60m）
    val completedCenters = remember(landmarks, completedLandmarkIds) {
        landmarks.filter { it.id in completedLandmarkIds }.map { it.point }
    }
    val revealCenters = remember(anchor, completedCenters, exploredCenters, liveScoutCenter) {
        buildList {
            completedCenters.forEach {
                val o = localOffsetMeters(anchor, it)
                add(RevealCenter(o.eastMeters, o.northMeters, FogMaskParams.COMPLETED_REVEAL_RADIUS_M.toFloat()))
            }
            exploredCenters.forEach {
                val o = localOffsetMeters(anchor, it)
                add(RevealCenter(o.eastMeters, o.northMeters, FogMaskParams.EXPLORED_RADIUS_M.toFloat()))
            }
            liveScoutCenter?.let {
                val o = localOffsetMeters(anchor, it)
                add(RevealCenter(o.eastMeters, o.northMeters, FogMaskParams.TEMP_VIEW_RADIUS_M.toFloat()))
            }
        }
    }
    val effectiveAlphas = remember(field, revealCenters) {
        field?.let { computeEffectiveAlphas(it, revealCenters) }
    }

    // 新打卡飘散动画（云朵散开之外保留的瞬间反馈）
    val holeAnims = remember { mutableStateMapOf<String, HoleAnim>() }
    var seenIds by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(completedLandmarkIds) {
        val current = completedLandmarkIds
        val previous = seenIds
        seenIds = current
        holeAnims.keys.toList().forEach { id -> if (id !in current) holeAnims.remove(id) }
        if (previous == null) return@LaunchedEffect
        (current - previous).forEach { id ->
            val anim = HoleAnim()
            holeAnims[id] = anim
            launch {
                anim.reveal.animateTo(
                    1f,
                    tween(REVEAL_DURATION_MS, delayMillis = REVEAL_DELAY_MS, easing = CubicBezierEasing(0.22f, 0.7f, 0.3f, 1f)),
                )
            }
            launch {
                anim.puff.animateTo(1f, tween(PUFF_DURATION_MS + PUFF_COUNT * 120, easing = LinearEasing))
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val frame = geoFrame ?: return@Canvas
        val ppmX = frame.pxPerMeter.coerceAtLeast(1e-4f)
        val ppmY = frame.pxPerMeterY.coerceAtLeast(1e-4f)
        val pool = spritePool
        val f = field
        val alphas = effectiveAlphas

        // 云朵屏幕尺寸 → 远 zoom 平涂回退（云 <16px 时逐渐转平涂，防斑点噪声）
        val cloudPx = SCALE_M * ppmX
        var washAlpha = WASH_MAX_A * (1f - ((cloudPx - MIN_CLOUD_PX) / 80f).coerceIn(0f, 1f))
        if (pool == null || f == null || alphas == null) {
            // sprite 池/云场尚未就绪：先铺一层薄雾色，避免裸地图闪现
            washAlpha = WASH_MAX_A
        }
        if (washAlpha > 0.004f) {
            drawRect(color = FogBody.copy(alpha = washAlpha), topLeft = Offset.Zero, size = size)
        }

        if (pool != null && f != null && alphas != null) {
            withTransform({ rotate(frame.bearingDegrees, pivot = frame.originScreen) }) {
                // 1) 雾毯层（连续雾海基底）→ 2) 云朵层（蓬松层叠主体）
                drawInstances(f.blankets, 0, f.blankets.size, alphas, pool.blankets, fogSliceVisible, frame, ppmX, ppmY)
                drawInstances(f.clouds, f.blankets.size, f.clouds.size, alphas, pool.clouds, fogSliceVisible, frame, ppmX, ppmY)
            }
        }

        // 3) 高德路径：marker 在本层之下，可见 marker 开小孔透出水滴
        val markerHoleRadius = MARKER_HOLE_DP.dp.toPx()
        markerScreenPos.forEach { (id, center) ->
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to Color.Black,
                    .8f to Color.Black,
                    1f to Color.Black.copy(alpha = 0f),
                    center = center,
                    radius = markerHoleRadius,
                ),
                radius = markerHoleRadius,
                center = center,
                blendMode = BlendMode.Clear,
            )
        }

        // 4) 新打卡飘散的小云（向左右上飘出淡出；地理锚定，尺寸/位移用地面米）
        val completed = landmarks.filter { it.id in completedLandmarkIds }
        completed.forEach { landmark ->
            val anim = holeAnims[landmark.id] ?: return@forEach
            val puffT = anim.puff.value
            val revealT = anim.reveal.value
            if (puffT <= 0f || puffT >= 1f) return@forEach
            val center = holeScreenPos(landmark) ?: return@forEach
            val rise = PUFF_RISE_M * ppmY * (0.25f + 0.75f * revealT)
            for (k in 0 until PUFF_COUNT) {
                val local = ((puffT - k * 120f / (PUFF_DURATION_MS + PUFF_COUNT * 120)) *
                    (PUFF_DURATION_MS + PUFF_COUNT * 120) / PUFF_DURATION_MS).coerceIn(0f, 1f)
                if (local <= 0f) continue
                val dir = PUFF_DIRS[k]
                val px = center.x + dir * PUFF_SPREAD_M * ppmX * local
                val py = center.y - rise * local - (k % 2) * 20f * ppmY
                val cloudScale = PUFF_CLOUD_M * ppmX / 220f * (1f + .3f * local)
                withTransform({
                    translate(px, py)
                    rotate(dir * 9f, pivot = Offset.Zero)
                    scale(cloudScale, cloudScale, pivot = Offset.Zero)
                }) {
                    drawCloudShape(FOG_SHAPES[(k + 2) % 5], .5f * (1f - local))
                }
            }
            if (revealT < 1f) {
                // 点亮瞬间：地标周围短促的柔光脉冲（云朵散开之外补充的即时反馈）
                val pulseRadius = FogMaskParams.TEMP_VIEW_RADIUS_M.toFloat() * ppmX * (1.2f + .8f * revealT)
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        0f to Color.White.copy(alpha = .35f * (1f - revealT)),
                        1f to Color.White.copy(alpha = 0f),
                        center = center,
                        radius = pulseRadius,
                    ),
                    radius = pulseRadius,
                    center = center,
                )
            }
        }
    }
}

/** 逐实例 drawImage：地理米制 → 锚定帧屏幕坐标（东 +x / 北 -y），alpha = 有效浓度 × 分片系数 */
private fun DrawScope.drawInstances(
    instances: List<CloudInstance>,
    offsetInAlphas: Int,
    count: Int,
    alphas: FloatArray,
    sprites: List<ImageBitmap>,
    sliceVisible: Map<CampusZone, Float>,
    frame: FogGeoFrame,
    ppmX: Float,
    ppmY: Float,
) {
    for (k in 0 until count) {
        val inst = instances[k]
        var a = alphas[offsetInAlphas + k] * inst.baseAlpha
        if (a <= 0.004f) continue
        inst.zone?.let { zone -> a *= sliceVisible[zone] ?: 1f }
        if (a <= 0.004f) continue
        val wPx = inst.sizeM * ppmX
        val hPx = inst.sizeM * ppmY
        if (wPx < 2f || hPx < 2f) continue
        val px = frame.originScreen.x + inst.east * ppmX
        val py = frame.originScreen.y - inst.north * ppmY
        // 离屏剔除：rotate 以锚点为轴，锚点距离在旋转前后不变 → 局部坐标直接判即可
        val cullRadius = hypot(size.width, size.height) * 0.5f + maxOf(wPx, hPx)
        val dx = px - frame.originScreen.x
        val dy = py - frame.originScreen.y
        if (dx * dx + dy * dy > cullRadius * cullRadius) return
        val sprite = sprites[inst.spriteIndex]
        drawImage(
            image = sprite,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(sprite.width, sprite.height),
            dstOffset = IntOffset((px - wPx / 2f).toInt(), (py - hPx / 2f).toInt()),
            dstSize = IntSize(wPx.toInt().coerceAtLeast(1), hPx.toInt().coerceAtLeast(1)),
            alpha = a.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** 在「云心已平移到局部原点」的坐标系里画一朵小云（坐标 = 原型 220×160 空间 - 云心偏移） */
private fun DrawScope.drawCloudShape(shape: FogShape, alphaMultiplier: Float) {
    shape.shadow.forEach { e ->
        drawOval(
            color = FogShadow.copy(alpha = e.alpha * alphaMultiplier),
            topLeft = Offset(e.cx - 110f - e.rx, e.cy - 82f - e.ry),
            size = Size(e.rx * 2f, e.ry * 2f),
        )
    }
    shape.body.forEach { e ->
        drawOval(
            color = FogBody.copy(alpha = e.alpha * alphaMultiplier),
            topLeft = Offset(e.cx - 110f - e.rx, e.cy - 82f - e.ry),
            size = Size(e.rx * 2f, e.ry * 2f),
        )
    }
    if (shape.curl) {
        val path = Path().apply {
            moveTo(62f - 110f, 94f - 82f)
            cubicTo(47f - 110f, 90f - 82f, 45f - 110f, 73f - 82f, 57f - 110f, 67f - 82f)
            cubicTo(63f - 110f, 64f - 82f, 70f - 110f, 66f - 82f, 73f - 110f, 71f - 82f)
        }
        drawPath(
            path = path,
            color = FogBody.copy(alpha = .86f * alphaMultiplier),
            style = Stroke(width = 11f, cap = StrokeCap.Round),
        )
    }
    shape.highlight.forEach { e ->
        drawOval(
            color = FogHighlight.copy(alpha = e.alpha * alphaMultiplier),
            topLeft = Offset(e.cx - 110f - e.rx, e.cy - 82f - e.ry),
            size = Size(e.rx * 2f, e.ry * 2f),
        )
    }
    shape.sparks.forEach { s ->
        drawCircle(
            color = Color.White.copy(alpha = s.alpha * alphaMultiplier),
            radius = s.r,
            center = Offset(s.cx - 110f, s.cy - 82f),
        )
    }
}
