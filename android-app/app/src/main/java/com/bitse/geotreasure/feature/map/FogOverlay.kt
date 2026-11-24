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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor

/**
 * P01 探索页 · 迷雾叠加层 v6.7（程序化积云雾面：域扭曲 fBm + 密度梯度光照，参照 E 云层参考图）。
 *
 * 设计（真机迭代定案：连续无分块 + 有积云层次感）：
 * 1) 视觉 = 程序化「积云」雾面，不使用任何贴图、不平移、不拼接：
 *    - Android 13+（API 33）：AGSL RuntimeShader 在 GPU 上逐像素按「地理米制坐标」计算
 *      域扭曲 fBm（大尺度噪声场卷曲细节噪声 → 云块自然成团、缝隙蜿蜒），再做
 *      密度梯度光照（沿光照方向再采样一次密度差 → 云顶受光高光、云隙深影），
 *      颜色三段映射（深影 → 雾体 → 奶白高光）对齐 E 参考图的层次感；
 *      定义域无穷大、数学上零重复，任意缩放/平移/旋转都是一片连续整体雾；
 *    - 更早版本回退：启动时后台程序化生成一张 12km 噪声图（同款数学与配色），
 *      单次地理锚定放置，无平移无拼接；
 * 2) 透明度由软件计算的 ALPHA_8 遮罩逐点决定（见 [FogMaskBuilder]）：
 *    校外恒 1 → 校界带大半径模糊渐变（无分区线）→ 校内统一朦胧半透明 0.50 →
 *    已打卡地标/临时视野/历史探索带按揭雾权重径向减淡；
 * 3) 层级：底图 < 云层 < 现有地标及定位标记 < 界面控件（AMap 原生 marker 在本层之下，
 *    可见 marker 由本层开 30dp 小孔透出，演示路径 marker 在本层之上无需开孔）；
 * 4) 校外无底图/业务标记漏出（雾在校外恒不透明）；云上无「未开放区域」文字。
 *
 * 实现要点：噪声雾面与遮罩共用同一地面坐标系（锚点 + 每米像素），遮罩按状态变化
 * 在后台线程重建（打卡/探索记录/定位更新时）；着色器路径每帧仅一次全屏 shader 矩形
 * 与一次 DstIn 遮罩（每次密度采样 = 2 次 5 倍频 fBm，主采样+光照共 4 次 fBm）。
 * 打卡反馈：整块点亮由遮罩重建完成，另有 5 朵小云飘散动画保留打卡瞬间反馈。
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
private const val NOISE_BASE_WAVE_M = 480f   // 噪声基频波长（米），细节逐倍频到 ~30m
private const val NOISE_SHEET_M = 12288f     // 回退噪声图地面边长（米，单次放置不拼接）
private const val NOISE_SHEET_PX = 1024      // 回退噪声图边长像素（~12m/px）

/**
 * AGSL 逐像素积云雾面着色器（API 33+），参照 E《fog-cloud-texture.png》视觉特征：
 * ①域扭曲：w = fbm(低频场)，base = fbm(p + 1.6·(w-½)) → 云块自然成团；
 * ②穹顶单元：billow = 1-|2·fbm₃-1|（低频、共享扭曲场）与 base 按 0.58/0.42 混合 →
 *   圆润积云穹顶 + 云隙深缝网络（参数经 Python 离屏渲染对照 E 图校准：
 *   密度分布 p15/p50/p85 ≈ 0.52/0.63/0.72 即三段颜色阈值）；
 * ③密度梯度光照：沿光照方向（地理西北）偏移 ~70m 再采样密度，差值 → 云顶受光高光。
 * fragCoord = 旋转后局部屏幕坐标（与 uOrigin/uPpm 同一坐标系），故随地图平移/缩放/旋转。
 * 定义域无穷大、无周期——彻底无分块；每次密度采样 = 2×fbm₅ + 1×fbm₃，主采样+光照共 26 次 vnoise。
 */
private const val FOG_NOISE_AGSL = """
uniform float2 uOrigin;
uniform float2 uPpm;
uniform float3 uShadow;
uniform float3 uBody;
uniform float3 uHighlight;

float hash21(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x),
               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm5(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 5; k++) {
        v += a * vnoise(p);
        p = p * 2.02 + float2(19.19, 7.33);
        a *= 0.5;
    }
    return v;
}

float fbm3(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 3; k++) {
        v += a * vnoise(p);
        p = p * 2.02 + float2(19.19, 7.33);
        a *= 0.5;
    }
    return v;
}

// 云密度场：低频噪声场做域扭曲，平滑 fBm 大结构 + 低频 billow 穹顶单元
float density(float2 p) {
    float w = fbm5(p * 0.55 + float2(31.7, 11.3));
    float dw = w - 0.5;
    float base = fbm5(p + 1.6 * dw);
    float domeN = fbm3(p * 0.9 + 1.1 * dw + float2(7.7, 3.1));
    return 0.58 * base + 0.42 * (1.0 - abs(2.0 * domeN - 1.0));
}

half4 main(float2 fragCoord) {
    float2 geo = (fragCoord - uOrigin) / uPpm;
    float2 p = geo / 480.0;
    float d = density(p);
    // 光照：地理西北方向采光（局部坐标系 y 向下=南），偏移量约 70m
    float dl = density(p + float2(-0.45, -0.60) * 0.14);
    float shade = clamp(0.5 + (d - dl) * 2.8, 0.0, 1.0);
    // 三段映射（阈值 = 密度分布 p15/p50/p85）：云隙深影 → 雾体 → 奶白高光
    float3 c = mix(uShadow, uBody, smoothstep(0.52, 0.63, d));
    c = mix(c, uHighlight, smoothstep(0.72, 0.84, d) * shade);
    c = mix(c, uShadow * 0.82, (1.0 - smoothstep(0.40, 0.52, d)) * 0.75);
    return half4(c.r, c.g, c.b, 1.0);
}
"""

// 配色参照 E《fog-cloud-texture.png》：云隙深鼠尾草绿阴影 / 雾体 / 云顶奶白高光
private val FogShadow = Color(0xFF7C9068)
private val FogBody = Color(0xFFC2CFA9)
private val FogHighlight = Color(0xFFF4EFDC)

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
 * FogOverlay 用它把云纹平铺与遮罩整体绕锚点旋转，使迷雾始终「套在地图上」。
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

// —— 程序化噪声（回退路径，API < 33）：与 AGSL 着色器同款 fBm 数学 —— //

/** 整数格点哈希 → [0,1)（fBm 的 value 噪声格点值） */
private fun hash2i(ix: Int, iy: Int): Float {
    var h = ix * 374761393 + iy * 668265263
    h = h xor (h ushr 13)
    h *= 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}

/** value-noise：格点哈希双线性插值 + smoothstep 缓动 */
private fun vnoise2(px: Float, py: Float): Float {
    val ix = floor(px).toInt()
    val iy = floor(py).toInt()
    val fx = px - ix
    val fy = py - iy
    val ux = fx * fx * (3f - 2f * fx)
    val uy = fy * fy * (3f - 2f * fy)
    val a = hash2i(ix, iy)
    val b = hash2i(ix + 1, iy)
    val c = hash2i(ix, iy + 1)
    val d = hash2i(ix + 1, iy + 1)
    return (a * (1f - ux) + b * ux) * (1f - uy) + (c * (1f - ux) + d * ux) * uy
}

/** 5 倍频 fBm（输入单位 = 基频波长个数；与 AGSL 同款倍频步进） */
private fun fbm2(px: Float, py: Float, octaves: Int = 5): Float {
    var v = 0f
    var amp = 0.5f
    var x = px
    var y = py
    repeat(octaves) {
        v += amp * vnoise2(x, y)
        x = x * 2.02f + 19.19f
        y = y * 2.02f + 7.33f
        amp *= 0.5f
    }
    return v
}

/**
 * 云密度场：低频噪声场做域扭曲，平滑 fBm 大结构 + 低频 billow 穹顶单元
 * （与 AGSL density() 完全同款；阈值 0.52/0.63/0.72 经离屏渲染对照 E 图校准）
 */
private fun density2(px: Float, py: Float): Float {
    val w = fbm2(px * 0.55f + 31.7f, py * 0.55f + 11.3f)
    val dw = w - 0.5f
    val base = fbm2(px + 1.6f * dw, py + 1.6f * dw)
    val domeN = fbm2(px * 0.9f + 1.1f * dw + 7.7f, py * 0.9f + 1.1f * dw + 3.1f, octaves = 3)
    return 0.58f * base + 0.42f * (1f - kotlin.math.abs(2f * domeN - 1f))
}

private fun sstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpf(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * 回退路径（API < 33）：程序化生成一张 [NOISE_SHEET_PX]² 噪声图（非照片贴图），
 * 覆盖 [NOISE_SHEET_M] 米地面矩形，单次地理锚定放置、绝不平移拼接。
 * 域扭曲 fBm + billow 穹顶 + 密度梯度光照 + 三段颜色映射，与 AGSL 着色器同款积云视觉；
 * 边缘 22% 径向淡入雾体色，图外即均匀雾色，过渡无痕。
 */
private fun makeNoiseSheet(): ImageBitmap {
    val px = NOISE_SHEET_PX
    val metersPerPx = NOISE_SHEET_M / px
    val half = NOISE_SHEET_M / 2f
    val sr = FogShadow.red; val sg = FogShadow.green; val sb = FogShadow.blue
    val br = FogBody.red; val bg = FogBody.green; val bb = FogBody.blue
    val hr = FogHighlight.red; val hg = FogHighlight.green; val hb = FogHighlight.blue
    val dSrk = sr * 0.82f; val dSgk = sg * 0.82f; val dSbk = sb * 0.82f
    val pixels = IntArray(px * px)
    var idx = 0
    for (v in 0 until px) {
        val northW = (half - v * metersPerPx) / NOISE_BASE_WAVE_M
        val ny = v / (px - 1f) * 2f - 1f
        for (u in 0 until px) {
            val eastW = (-half + u * metersPerPx) / NOISE_BASE_WAVE_M
            val nx = u / (px - 1f) * 2f - 1f
            val d = density2(eastW, northW)
            // 光照：向地理西北（东 -0.063 / 北 +0.084 个波长单位）偏移采样，与 AGSL 一致
            val dl = density2(eastW - 0.45f * 0.14f, northW + 0.60f * 0.14f)
            val shade = (0.5f + (d - dl) * 2.8f).coerceIn(0f, 1f)
            val mass = sstep((d - 0.52f) / 0.11f)
            var r = lerpf(sr, br, mass)
            var g = lerpf(sg, bg, mass)
            var b = lerpf(sb, bb, mass)
            val top = sstep((d - 0.72f) / 0.12f) * shade
            r = lerpf(r, hr, top)
            g = lerpf(g, hg, top)
            b = lerpf(b, hb, top)
            val crev = (1f - sstep((d - 0.40f) / 0.12f)) * 0.75f
            r = lerpf(r, dSrk, crev)
            g = lerpf(g, dSgk, crev)
            b = lerpf(b, dSbk, crev)
            // 边缘径向淡入雾体色（图外=均匀雾色，无可见图界）
            val rr = kotlin.math.sqrt(nx * nx + ny * ny)
            val edge = sstep((rr - 0.78f) / 0.22f)
            r = lerpf(r, br, edge)
            g = lerpf(g, bg, edge)
            b = lerpf(b, bb, edge)
            pixels[idx++] = (255 shl 24) or
                ((r * 255f).toInt().coerceIn(0, 255) shl 16) or
                ((g * 255f).toInt().coerceIn(0, 255) shl 8) or
                (b * 255f).toInt().coerceIn(0, 255)
        }
    }
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, px, 0, 0, px, px)
    return bmp.asImageBitmap()
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
    modifier: Modifier = Modifier,
) {
    // 噪声着色器（API 33+）：GPU 逐像素按地理坐标计算 fBm，无贴图无重复；编译失败自动回退
    val noiseShader = remember {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                android.graphics.RuntimeShader(FOG_NOISE_AGSL)
            } else {
                null
            }
        }.getOrNull()
    }

    // 回退噪声图（API < 33）：启动时后台程序化生成一次（非贴图，无平移拼接）
    val noiseSheet by produceState<ImageBitmap?>(initialValue = null) {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            value = withContext(Dispatchers.Default) {
                runCatching { makeNoiseSheet() }.getOrNull()
            }
        }
    }

    // 遮罩：状态变化（打卡/探索记录/定位/锚点）时后台重建
    val anchor = remember(landmarks) { fogAnchorPoint(landmarks) }
    val completedCenters = remember(landmarks, completedLandmarkIds) {
        landmarks.filter { it.id in completedLandmarkIds }.map { it.point }
    }
    val mask by produceState<Pair<ImageBitmap, FogMaskBounds>?>(
        initialValue = null,
        completedCenters, exploredCenters, liveScoutCenter, anchor,
    ) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                FogMaskBuilder.build(anchor, completedCenters, exploredCenters, liveScoutCenter)
                    ?.let { (bitmap, bounds) -> bitmap.asImageBitmap() to bounds }
            }.getOrNull()
        }
    }

    // 新打卡飘散动画（整块点亮由遮罩重建完成，飘散保留瞬间反馈）
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
        val maskState = mask ?: return@Canvas
        val (maskImage, maskBounds) = maskState
        val ppmX = frame.pxPerMeter.coerceAtLeast(1e-4f)
        val ppmY = frame.pxPerMeterY.coerceAtLeast(1e-4f)

        // 1) 基底：全屏平涂雾色（校外恒不透明的连续雾面；颜色均匀，无需随地图旋转）
        drawRect(color = FogBody, topLeft = Offset.Zero, size = size)

        withTransform({ rotate(frame.bearingDegrees, pivot = frame.originScreen) }) {
            // 2) 连续噪声雾面：不使用任何贴图、不平移、不拼接。
            //    API 33+：AGSL 逐像素按地理米制坐标计算 fBm（定义域无穷大，数学上零重复）；
            //    更早版本：启动时程序化生成的单张噪声图（12km 地理锚定矩形，边缘融入雾色）。
            //    两者都以「锚点处地理东=局部屏幕 x / 北=局部屏幕 -y」为输入，随地图平移/缩放/旋转。
            if (noiseShader != null) {
                val s = noiseShader!!
                s.setFloatUniform("uOrigin", frame.originScreen.x, frame.originScreen.y)
                s.setFloatUniform("uPpm", ppmX, ppmY)
                s.setFloatUniform("uShadow", FogShadow.red, FogShadow.green, FogShadow.blue)
                s.setFloatUniform("uBody", FogBody.red, FogBody.green, FogBody.blue)
                s.setFloatUniform("uHighlight", FogHighlight.red, FogHighlight.green, FogHighlight.blue)
                val brush = object : ShaderBrush() {
                    override fun createShader(size: Size): android.graphics.Shader = s
                }
                drawRect(brush = brush, topLeft = Offset.Zero, size = size)
            } else if (noiseSheet != null) {
                val halfM = NOISE_SHEET_M / 2f
                drawImage(
                    image = noiseSheet!!,
                    dstOffset = IntOffset(
                        (frame.originScreen.x - halfM * ppmX).toInt(),
                        (frame.originScreen.y - halfM * ppmY).toInt(),
                    ),
                    dstSize = IntSize(
                        (NOISE_SHEET_M * ppmX).toInt(),
                        (NOISE_SHEET_M * ppmY).toInt(),
                    ),
                    filterQuality = FilterQuality.Medium,
                )
            }

            // 3) 遮罩：校外恒 1 / 校内羽化与揭雾（DstIn 乘到整层 alpha 上）
            val maskX = frame.originScreen.x + maskBounds.minEastM.toFloat() * ppmX
            val maskY = frame.originScreen.y - maskBounds.maxNorthM.toFloat() * ppmY
            val maskW = (maskBounds.maxEastM - maskBounds.minEastM).toFloat() * ppmX
            val maskH = (maskBounds.maxNorthM - maskBounds.minNorthM).toFloat() * ppmY
            drawImage(
                image = maskImage,
                dstOffset = IntOffset(maskX.toInt(), maskY.toInt()),
                dstSize = IntSize(maskW.toInt(), maskH.toInt()),
                filterQuality = FilterQuality.Low,
                blendMode = BlendMode.DstIn,
            )
            // 遮罩位图只覆盖地标包围盒+填充的范围，而 DstIn 会把图外区域乘 0（雾消失）。
            // 在遮罩矩形四周补画 ×1 区域，保证任意缩放级别下全屏校外雾恒不透明（全域覆盖）。
            val stripLen = size.width + size.height + maskW + maskH
            drawRect(
                color = Color.Black,
                topLeft = Offset(maskX - stripLen, maskY - stripLen),
                size = Size(maskW + 2 * stripLen, stripLen),
                blendMode = BlendMode.DstIn,
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(maskX - stripLen, maskY + maskH),
                size = Size(maskW + 2 * stripLen, stripLen),
                blendMode = BlendMode.DstIn,
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(maskX - stripLen, maskY),
                size = Size(stripLen, maskH),
                blendMode = BlendMode.DstIn,
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(maskX + maskW, maskY),
                size = Size(stripLen, maskH),
                blendMode = BlendMode.DstIn,
            )
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
                // 点亮瞬间：地标周围短促的柔光脉冲（遮罩整块点亮之外补充的即时反馈）
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
