package com.bitse.geotreasure.feature.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import com.bitse.geotreasure.core.model.Landmark
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * P01 探索页 · 迷雾叠加层 v3（对齐 design/prototype/index.html 的 P01 迷雾 v2/v3 设计）。
 *
 * 设计要点（与原型逐项对应）：
 * 1) 默认态：整张地图被 70+ 朵卡通云铺满（云层不透明度 --fogA≈.62，半透明），底图轮廓仍可辨认；
 * 2) 已打卡：以该地标为圆心开出半径 FOG_R 的软边空洞（径向渐变 Clear），孔外云雾保持原样；
 * 3) 新打卡：孔半径 0→FOG_R 生长 1.7s（缓出曲线 0.22,0.7,0.3,1），同时 5 朵小云向左右上飘散淡出 1.9s；
 * 4) 云形 5 种（蓬松团云/扁宽长云/高积云/小卷云/叠云）× 确定性缩放·翻转·旋转 × 三种漂移节奏。
 *
 * 层级：本层画在底图之上、地标水滴之下（原型 fog z5 < pins z7），pointer-events 全部穿透。
 * 范围：仅演示地图（DemoMapContent）；真实高德地图路径沿用 FogPolygonOverlay 逻辑。
 * 迷雾语义：孔 = 已打卡地标（对齐原型）；旧「侦查半径视觉开孔」退役，侦查圈调试环保留。
 */
private const val FOG_LAYER_ALPHA = 0.62f       // --fogA：整层不透明度（半透明，底图可辨）
private const val FOG_BASE_RADIUS_PX = 104f     // FOG_R @ 原型 390px 地图宽
private const val PROTO_MAP_WIDTH_PX = 390f
private const val REVEAL_DURATION_MS = 1700     // 孔生长 1.7s
private const val REVEAL_DELAY_MS = 120         // 先柔光再化开的起手延迟
private const val PUFF_DURATION_MS = 1900       // 单朵小云飘散 1.9s
private const val PUFF_COUNT = 5

private val FogShadow = Color(0xFF89A687)
private val FogBody = Color(0xFFB9D4B5)
private val FogHighlight = Color(0xFFE6F1E2)
private val FogWashTop = Color(0xFFEBF5E7)
private val FogWashMid = Color(0xFFDAEAD8)
private val FogWashBottom = Color(0xFFE4F0E0)

private data class FogEllipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val alpha: Float)
private data class FogSpark(val cx: Float, val cy: Float, val r: Float, val alpha: Float)
private data class FogShape(
    val shadow: List<FogEllipse>,
    val body: List<FogEllipse>,
    val highlight: List<FogEllipse>,
    val sparks: List<FogSpark>,
    val curl: Boolean = false,
)

/** 5 种云形，坐标基准 220×160，云心 (110,82)，三层色阶（暗部/云体/高光）与 P07 3D 迷雾同一套语言 */
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
    // 3 小卷云（带一道卷尾描边）
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
        curl = true,
    ),
    // 4 叠云（小云趴在大云上）
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

private class FogCloud(
    val xFrac: Float,
    val yFrac: Float,
    val kind: Int,
    val scale: Float,
    val rotation: Float,
    val flip: Float,
    val opacity: Float,
    val driftSpeed: Float,
    val driftPhase: Float,
)

/** 云场：9 列 × 9 行错位叠铺（超出画布防边缘漏底），确定性抖动，与原型 fogCloudField() 同一套公式 */
private fun buildCloudField(): List<FogCloud> {
    val xs = floatArrayOf(-12f, 4f, 20f, 36f, 52f, 68f, 84f, 100f, 116f)
    val ys = floatArrayOf(-8f, 6f, 20f, 34f, 48f, 62f, 76f, 90f, 104f)
    val scales = floatArrayOf(1.10f, 1.26f, .98f, 1.30f, 1.16f)
    val clouds = mutableListOf<FogCloud>()
    var n = 0
    for (row in ys.indices) {
        for (col in xs.indices) {
            if ((row * 5 + col * 3) % 11 == 0) continue // 打散网格感
            val jx = ((col * 37 + row * 19) % 11) - 5f
            val jy = ((col * 23 + row * 31) % 11) - 5f
            val kind = (row * 3 + col * 2) % 5
            val s = scales[(row + col * 2) % 5]
            val rot = ((col * 29 + row * 17) % 13) - 6f
            val flip = if ((col + row) % 3 == 0) -1f else 1f
            val drift = 1 + ((col + row) % 3)
            val speed = when (drift) { 1 -> 0.55f; 2 -> 0.38f; else -> 0.72f }
            val phase = (((n * 733) % 11000) / 11000f) * 2f * PI.toFloat()
            clouds += FogCloud(
                xFrac = (xs[col] + jx) / 100f,
                yFrac = (ys[row] + jy) / 100f,
                kind = kind,
                scale = s,
                rotation = rot,
                flip = flip,
                opacity = 0.78f + (n % 5) * 0.055f,
                driftSpeed = speed,
                driftPhase = phase,
            )
            n++
        }
    }
    return clouds
}

private val PUFF_DIRS = floatArrayOf(-1.15f, -.6f, .05f, .62f, 1.1f)

private class HoleAnim {
    val reveal = Animatable(0f)
    val puff = Animatable(0f)
}

@Composable
internal fun FogOverlay(
    landmarks: List<Landmark>,
    completedLandmarkIds: Set<String>,
    projection: DemoMapProjection?,
    modifier: Modifier = Modifier,
) {
    val clouds = remember { buildCloudField() }
    val driftTransition = rememberInfiniteTransition(label = "fogDrift")
    val driftT by driftTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
        label = "fogDriftT",
    )

    // 新打卡差分：首帧已带进度的孔不重播（对应原型「静态孔」），之后新增的孔走生长动画
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
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = FOG_LAYER_ALPHA
            },
    ) {
        val unit = size.width / PROTO_MAP_WIDTH_PX
        // 云缝雾底（fogWash，opacity .26）
        drawRect(
            brush = Brush.verticalGradient(
                0f to FogWashTop.copy(alpha = .26f),
                .5f to FogWashMid.copy(alpha = .26f),
                1f to FogWashBottom.copy(alpha = .26f),
            ),
        )
        // 卡通云层（fogdriftA/B/C 三种漂移节奏）
        val twoPi = 2f * PI.toFloat()
        clouds.forEach { cloud ->
            val px = cloud.xFrac * size.width +
                sin(driftT * twoPi + cloud.driftPhase) * 7f * unit * cloud.driftSpeed
            val py = cloud.yFrac * size.height +
                cos(driftT * twoPi + cloud.driftPhase * .7f) * 4f * unit * cloud.driftSpeed
            withTransform({
                translate(px, py)
                rotate(cloud.rotation, pivot = Offset.Zero)
                scale(cloud.scale * cloud.flip * unit, cloud.scale * unit, pivot = Offset.Zero)
            }) {
                drawCloudShape(FOG_SHAPES[cloud.kind], cloud.opacity)
            }
        }
        if (projection == null) return@Canvas
        // 软边开孔：已打卡地标（Clear 混合擦出底图）
        val baseRadius = FOG_BASE_RADIUS_PX * unit
        val completed = landmarks.filter { it.id in completedLandmarkIds }
        completed.forEach { landmark ->
            val anim = holeAnims[landmark.id]
            val fraction = anim?.reveal?.value ?: 1f
            if (fraction <= 0f) return@forEach
            val position = projection.position(landmark.point)
            val center = Offset(position.xFraction * size.width, position.yFraction * size.height)
            val radius = baseRadius * fraction
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Black,
                    .86f to Color.Black,
                    1f to Color.Black.copy(alpha = 0f),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
                blendMode = BlendMode.Clear,
            )
        }
        // 新打卡飘散的小云（fogpuff：向左右上飘出淡出）
        completed.forEach { landmark ->
            val anim = holeAnims[landmark.id] ?: return@forEach
            val puffT = anim.puff.value
            if (puffT <= 0f || puffT >= 1f) return@forEach
            val position = projection.position(landmark.point)
            val cx = position.xFraction * size.width
            val cy = position.yFraction * size.height
            val window = PUFF_DURATION_MS.toFloat() / (PUFF_DURATION_MS + PUFF_COUNT * 120)
            for (k in 0 until PUFF_COUNT) {
                val start = k * 120f / (PUFF_DURATION_MS + PUFF_COUNT * 120)
                val local = ((puffT - start) / window).coerceIn(0f, 1f)
                if (local <= 0f) continue
                val dir = PUFF_DIRS[k]
                val px = cx + dir * .022f * size.width + dir * 96f * unit * local
                val py = cy - (k % 2) * .015f * size.height - (38f + (k % 3) * 16f) * unit * local
                val scaleF = (1.10f + (k % 2) * .16f) * (1f + .3f * local)
                withTransform({
                    translate(px, py)
                    rotate(dir * 9f, pivot = Offset.Zero)
                    scale(scaleF * unit, scaleF * unit, pivot = Offset.Zero)
                }) {
                    drawCloudShape(FOG_SHAPES[(k + 2) % 5], .5f * (1f - local))
                }
            }
        }
    }
}

/** 在「云心已平移到局部原点」的坐标系里画一朵云（坐标 = 原型 220×160 空间 - 云心偏移） */
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
