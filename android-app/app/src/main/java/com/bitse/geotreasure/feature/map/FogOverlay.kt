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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.campusRevealBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor

/**
 * P01 探索页 · 迷雾叠加层 v4（E「连续云层·步行开雾」第二版，替代 v3.2 卡通云场）。
 *
 * 设计（依据 E《README-接入说明.md》）：
 * 1) 视觉 = 单张云纹（fog-cloud-texture.png）按固定地面尺寸平铺，随地图平移/缩放（地理锚定）；
 * 2) 透明度由软件计算的 ALPHA_8 遮罩逐点决定（见 [FogMaskBuilder]）：
 *    校外恒 1 → 校界内侧宽羽化 → 校内未探索 0.55 → 临时视野/历史探索带/已打卡整块按揭雾权重减淡；
 * 3) 层级：底图 < 云层 < 现有地标及定位标记 < 界面控件（AMap 原生 marker 在本层之下，
 *    可见 marker 由本层开 30dp 小孔透出，演示路径 marker 在本层之上无需开孔）；
 * 4) 校外无底图/业务标记漏出（雾在校外恒不透明）；云上无「未开放区域」文字。
 *
 * 实现要点：云纹平铺与遮罩共用同一地面坐标系（锚点 + 每米像素），遮罩按状态变化
 * 在后台线程重建（打卡/探索记录/定位更新时），每帧仅做两次 drawImage（纹样 + DstIn 遮罩）
 * 与少量小孔/飘散动画，开销远低于 v3.2 的逐帧云朵矢量绘制。
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
private const val TEXTURE_TILE_M = 512f       // 云纹单格地面尺寸（米，地理锚定平铺）

private val FogShadow = Color(0xFF89A687)
private val FogBody = Color(0xFFB9D4B5)
private val FogHighlight = Color(0xFFE6F1E2)

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

/** 迷雾地理锚定帧：锚点屏幕坐标 + 当前缩放下的每米像素数（纵向可单独指定，演示路径各向异性投影用） */
data class FogGeoFrame(
    val originScreen: Offset,
    val pxPerMeter: Float,
    val pxPerMeterY: Float = pxPerMeter,
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
    val context = LocalContext.current

    // 云纹：单张贴图，App 生命周期内解码一次
    val texture by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("fog-cloud-texture.png").use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    // 遮罩：状态变化（打卡/探索记录/定位/锚点）时后台重建
    val anchor = remember(landmarks) { fogAnchorPoint(landmarks) }
    val completedBlocks = remember(landmarks, completedLandmarkIds) {
        campusRevealBlocks(landmarks).filter { it.landmarkId in completedLandmarkIds }
    }
    val mask by produceState<Pair<ImageBitmap, FogMaskBounds>?>(
        initialValue = null,
        completedBlocks, exploredCenters, liveScoutCenter, anchor,
    ) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                FogMaskBuilder.build(anchor, completedBlocks, exploredCenters, liveScoutCenter)
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
        val tex = texture ?: return@Canvas
        val maskState = mask ?: return@Canvas
        val (maskImage, maskBounds) = maskState
        val ppmX = frame.pxPerMeter.coerceAtLeast(1e-4f)
        val ppmY = frame.pxPerMeterY.coerceAtLeast(1e-4f)

        // 1) 云纹平铺：格 (i,j) 对应地面固定位置，随地图平移/缩放（地理锚定）
        val leftM = -frame.originScreen.x / ppmX
        val rightM = (size.width - frame.originScreen.x) / ppmX
        val topM = frame.originScreen.y / ppmY
        val bottomM = (frame.originScreen.y - size.height) / ppmY
        val iMin = floor(leftM / TEXTURE_TILE_M).toInt()
        val iMax = floor(rightM / TEXTURE_TILE_M).toInt()
        val jMin = floor(bottomM / TEXTURE_TILE_M).toInt()
        val jMax = floor(topM / TEXTURE_TILE_M).toInt()
        for (i in iMin..iMax) {
            for (j in jMin..jMax) {
                val x = frame.originScreen.x + i * TEXTURE_TILE_M * ppmX
                val y = frame.originScreen.y - (j + 1) * TEXTURE_TILE_M * ppmY
                val w = TEXTURE_TILE_M * ppmX
                val h = TEXTURE_TILE_M * ppmY
                if (x > size.width || y > size.height || x + w < 0 || y + h < 0) continue
                // 镜像平铺：按格 (i,j) 奇偶翻转，相邻格边缘像素连续，消除 512m 网格接缝线
                val flipX = ((i % 2) + 2) % 2 != 0
                val flipY = ((j % 2) + 2) % 2 != 0
                if (flipX || flipY) {
                    withTransform({
                        translate(x + if (flipX) w else 0f, y + if (flipY) h else 0f)
                        scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f, pivot = Offset.Zero)
                    }) {
                        drawImage(
                            image = tex,
                            dstOffset = IntOffset(0, 0),
                            dstSize = IntSize(w.toInt() + 1, h.toInt() + 1),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                } else {
                    drawImage(
                        image = tex,
                        dstOffset = IntOffset(x.toInt(), y.toInt()),
                        dstSize = IntSize(w.toInt() + 1, h.toInt() + 1),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
        }

        // 2) 遮罩：校外恒 1 / 校内羽化与揭雾（DstIn 乘到云纹 alpha 上）
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
