package com.bitse.geotreasure.feature.map3d

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.AppTab
import com.bitse.geotreasure.core.ui.components.AppTabBar
import com.bitse.geotreasure.core.ui.components.LandmarkImage
import com.bitse.geotreasure.core.ui.components.PageHeader
import com.bitse.geotreasure.core.ui.theme.GoldText
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.HairLine
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.PrimaryContainer
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.RewardGold
import com.bitse.geotreasure.core.ui.theme.RewardGoldSurface
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import kotlin.math.roundToInt

/**
 * P07 · 3D 校园地图（斜轴测沙盘 + 云层迷雾）。
 *
 * 与原型的关系：
 *  - 保留：等轴测视角、一片云 = 一个地标、打卡后该地标头顶的云散开、片区 4/4 整片散开、
 *    顶卡计数（X/12 片 · Y/3 校区）、校区聚焦、底部 sheet 的文案与入口。
 *  - 简化：原型的矢量沙盘是 1000+ 行手绘 SVG（教学楼、树、湖、路面），这里用 Canvas 画出
 *    地块与路网骨架。正式版接入高德 3D 地图后，把 drawCampusBase() 换成 MapView 即可，
 *    云层与 pin 的定位逻辑（project()）不用改。
 */
@Composable
fun Map3DRoute(
    onBack: () -> Unit,
    onExplore: () -> Unit,
    onOpenPuzzle: () -> Unit,
    onStartCheckIn: (landmarkId: String) -> Unit,
    viewModel: Map3DViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Map3DScreen(
        state = state,
        onBack = onBack,
        onExplore = onExplore,
        onOpenPuzzle = onOpenPuzzle,
        onStartCheckIn = onStartCheckIn,
        onFocusCampus = viewModel::focus,
        onSpotlight = viewModel::spotlight
    )
}

@Composable
fun Map3DScreen(
    state: Map3DUiState,
    onBack: () -> Unit,
    onExplore: () -> Unit,
    onOpenPuzzle: () -> Unit,
    onStartCheckIn: (String) -> Unit,
    onFocusCampus: (String?) -> Unit,
    onSpotlight: (String?) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(com.bitse.geotreasure.ui.theme.Background).statusBarsPadding()) {
        PageHeader(
            title = "3D 校园地图",
            subtitle = "良乡 · 云层已散开 ${state.fogOpened}/${state.totalShards} 片 · ${state.completedCampuses}/${state.layers.size} 校区",
            onBack = onBack,
            actionIcon = Icons.Outlined.Map,
            actionDescription = "一片云对应一个地标",
            // 原型 hdr(act:'toast:一片云对应一个地标（原型示意）')
            onAction = {
                Toast.makeText(context, "一片云对应一个地标（原型示意）", Toast.LENGTH_SHORT).show()
            }
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 原型 .m3wrap 底色是径向渐变，不是纯色 —— 纯色会让沙盘看起来像浮在页面上
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFFF2F7EC),
                            0.42f to Color(0xFFE7F0E3),
                            0.72f to Color(0xFFDCE9DC),
                            1f to Color(0xFFD3E2D6)
                        )
                    )
                )
        ) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val density = LocalDensity.current
            val pinHalfW = with(density) { (MAP_PIN_W / 2).toPx() }
            val pinBaseY = with(density) { (MAP_PIN_H - 2.dp).toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCampusBase(w, h, density.density)
                drawFog(state, w, h, density.density)
            }

            // 标记：原型 pins() 只画「已打卡」和「下一个目标」两种，未解锁的位置一个点都不画
            // —— 云层底下要保持未知，所以不是 12 个问号。
            val nextId = state.nextTarget.id
            state.layers.forEachIndexed { moduleIndex, layer ->
                layer.module.landmarks.forEach { landmark ->
                    val owned = landmark.slot in layer.ownedSlots
                    val isNextPin = !owned && landmark.id == nextId
                    if (!owned && !isNextPin) return@forEach

                    val (u, v) = pinPosition(moduleIndex, landmark.slot)
                    val p = project(u, v, w, h)
                    Box(
                        modifier = Modifier.offset {
                            IntOffset(
                                (p.x - pinHalfW).roundToInt(),
                                (p.y - pinBaseY).roundToInt()
                            )
                        }
                    ) {
                        Map3DPin(
                            kind = if (owned) PinKind.Done else PinKind.Next,
                            label = if (isNextPin) "下一个目标" else null,
                            onClick = {
                                // 已打卡 → 看这一处的说明卡（原型 sheet 的 partial / campus 两态）
                                // 下一个目标 → 直接进拍照打卡
                                if (owned) onSpotlight(landmark.id) else onStartCheckIn(landmark.id)
                            }
                        )
                    }
                }
            }

            // 顶部覆盖层：3D/2D 切换 → 迷雾卡 → 校区聚焦 → 提示条（原型自 62px 起依次下排）
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MapModeToggle(onExplore = onExplore)
                }
                Spacer(Modifier.height(5.dp))
                FogCard(state = state, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                FocusPills(state = state, onFocusCampus = onFocusCampus)
                Spacer(Modifier.height(6.dp))
                MapTip(modifier = Modifier.fillMaxWidth())
            }

            // 底部：说明卡
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MapSheet(
                    state = state,
                    onStartCheckIn = onStartCheckIn,
                    onOpenPuzzle = onOpenPuzzle,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        AppTabBar(
            current = AppTab.Explore,
            shards = state.shards,
            totalShards = state.totalShards,
            onSelect = { tab ->
                when (tab) {
                    AppTab.Explore -> onExplore()
                    AppTab.Puzzle -> onOpenPuzzle()
                    AppTab.Records -> Unit
                }
            }
        )
    }
}

/* ---------------- 投影：世界坐标 (u,v) ∈ [0,1]² → 屏幕像素 ---------------- */

/**
 * 原型里沙盘是「一块比取景窗更大的地块」，边缘直接铺出屏幕；
 * 这里把 x 系数放大到地块左右两侧出血，避免四角露出底色、看起来像浮在页面上的方块。
 * y 只占 0.08~0.76：下沿要留给说明卡，上沿要留抬头部覆盖层（迷雾卡 + 聚焦 + 提示条）。
 */
private fun project(u: Float, v: Float, w: Float, h: Float): Offset =
    Offset(
        x = w * 0.5f + (u - v) * w * 0.62f,
        y = h * 0.08f + (u + v) * h * 0.34f
    )

/**
 * 三个校区在沙盘上的锚点（等轴测的 u/v 坐标，不是屏幕比例）。
 * 关键约束：屏幕 x 只由 (u - v) 决定 —— 所以南/北不能都取在 u≈v 的对角线上，
 * 否则两片区会重叠成一根竖线（曾经就是这么错位的）。
 * 现在：北在上中，南在左下，东在右下。
 */
private val CAMPUS_ANCHORS = listOf(
    0.52f to 0.90f,   // 南校区 MP-A
    0.46f to 0.54f,   // 北校区 MP-B
    0.90f to 0.52f    // 东校区 MP-C
)

/** 一个校区内的 2×2：±0.10 的 u/v 偏移，在等轴测下自然呈现成一个菱形四格 */
private fun pinPosition(moduleIndex: Int, slot: Int): Pair<Float, Float> {
    val (cu, cv) = CAMPUS_ANCHORS[moduleIndex.coerceIn(0, 2)]
    val du = if (slot % 2 == 0) -0.10f else 0.10f
    val dv = if (slot < 2) -0.10f else 0.10f
    return (cu + du) to (cv + dv)
}

/* ---------------- 沙盘底图 ---------------- */

private fun DrawScope.drawCampusBase(w: Float, h: Float, density: Float) {
    val top = project(0f, 0f, w, h)
    val right = project(1f, 0f, w, h)
    val bottom = project(1f, 1f, w, h)
    val left = project(0f, 1f, w, h)

    val thickness = 14f * density

    // 侧壁（立体感主要靠这一层）
    val side = Path().apply {
        moveTo(left.x, left.y)
        lineTo(bottom.x, bottom.y)
        lineTo(right.x, right.y)
        lineTo(right.x, right.y + thickness)
        lineTo(bottom.x, bottom.y + thickness)
        lineTo(left.x, left.y + thickness)
        close()
    }
    drawPath(side, color = Color(0xFFC4D2BC))

    // 台面
    val plate = Path().apply {
        moveTo(top.x, top.y)
        lineTo(right.x, right.y)
        lineTo(bottom.x, bottom.y)
        lineTo(left.x, left.y)
        close()
    }
    drawPath(plate, color = Color(0xFFDCE8CE))

    // 路面：两条对角主线
    val roadColor = Color(0xFFE3DDCE)
    drawLine(
        roadColor,
        project(0.02f, 0.55f, w, h),
        project(0.98f, 0.55f, w, h),
        strokeWidth = 12f * density
    )
    drawLine(
        roadColor,
        project(0.5f, 0.02f, w, h),
        project(0.5f, 0.98f, w, h),
        strokeWidth = 10f * density
    )

    // 北湖：水面
    val lake = project(0.36f, 0.30f, w, h)
    drawOval(
        color = Color(0xFFCFE3D3),
        topLeft = Offset(lake.x - 34f * density, lake.y - 16f * density),
        size = Size(68f * density, 32f * density)
    )

    // 每个校区一小块绿地，暗示四个地标的范围（用和 pin 完全同一套锚点，不许另算）
    val quadColor = Color(0x33274A3B)
    for ((u, v) in CAMPUS_ANCHORS) {
        drawCircle(quadColor, radius = 52f * density, center = project(u, v, w, h))
    }
}

/** 云层：未解锁的地标头顶三团云；已解锁的位置留干净孔洞 */
private fun DrawScope.drawFog(state: Map3DUiState, w: Float, h: Float, density: Float) {
    state.layers.forEachIndexed { moduleIndex, layer ->
        layer.module.landmarks.forEach { landmark ->
            if (landmark.slot in layer.ownedSlots) return@forEach
            val (u, v) = pinPosition(moduleIndex, landmark.slot)
            val p = project(u, v, w, h)
            val r = 24f * density

            // 云团：径向渐变铺出柔边，硬边实心圆会看成白盘子（原型云是三层柔和过渡）
            drawCircle(
                Color(0x2EA9BFA6),
                radius = r * 1.35f,
                center = Offset(p.x + 3f * density, p.y + 7f * density)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xF2FFFFFF),
                        0.55f to Color(0xE0F7FAF4),
                        1f to Color(0x00F7FAF4)
                    ),
                    center = p,
                    radius = r * 1.5f
                ),
                radius = r * 1.5f,
                center = p
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFFFFFF),
                        1f to Color(0x00FFFFFF)
                    ),
                    center = Offset(p.x - r * 0.28f, p.y - r * 0.30f),
                    radius = r * 0.95f
                ),
                radius = r * 0.95f,
                center = Offset(p.x - r * 0.28f, p.y - r * 0.30f)
            )
        }
    }
}

/* ---------------- 覆盖控件 ---------------- */

/** 原型 .m3toggle：3D 已选中，2D 回探索页 */
@Composable
private fun MapModeToggle(onExplore: () -> Unit) {
    Row(
        modifier = Modifier
            .background(BrandSurface.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .border(1.dp, HairLine, RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        Text(
            "3D",
            style = AppText.small.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier
                .background(Primary, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
        Text(
            "2D",
            style = AppText.small.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF63736C),
            modifier = Modifier
                .clickable { onExplore() }
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/**
 * 3D 地图上的标记，对应原型 pinSvg() 的两种形态 —— 和 P01 的水滴（LandmarkPin）不是同一套：
 *  - Done：白底小圆点 + 绿勾，直接坐在地面上，不抢建筑视线
 *  - Next：金色圆角方牌 + 深色针形，上方挂一枚深绿胶囊标签（「下一个目标」/「正在开云」）
 * 坐标沿用原型 SVG：1 单位 = 1dp，原点在落点（阴影中心）。
 */
@Composable
private fun Map3DPin(
    kind: PinKind,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(modifier = modifier.size(MAP_PIN_W, MAP_PIN_H)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val u = density.density
            val cx = size.width / 2f
            val baseY = size.height - 2f * u
            fun px(x: Float) = cx + x * u
            fun py(y: Float) = baseY + y * u

            // 地面阴影
            drawOval(
                color = Color(0x42203C30),
                topLeft = Offset(px(-6f), py(0f)),
                size = Size(12f * u, 4f * u)
            )

            when (kind) {
                PinKind.Done -> {
                    drawCircle(Color(0xF2FFFFFF), radius = 6.4f * u, center = Offset(cx, baseY))
                    drawCircle(Primary, radius = 6.4f * u, center = Offset(cx, baseY), style = Stroke(1.4f * u))
                    val check = Path().apply {
                        moveTo(px(-2.7f), py(-0.8f))
                        lineTo(px(-0.8f), py(1.1f))
                        lineTo(px(2.7f), py(-2.8f))
                    }
                    drawPath(
                        check,
                        Primary,
                        style = Stroke(1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                PinKind.Next -> {
                    val gold = Color(0xFFB58B45)
                    // 牌底朝向地面的小三角
                    drawPath(
                        Path().apply {
                            moveTo(px(0f), py(-1f))
                            lineTo(px(-3.6f), py(-5.5f))
                            lineTo(px(3.6f), py(-5.5f))
                            close()
                        },
                        gold
                    )
                    drawRoundRect(
                        color = gold,
                        topLeft = Offset(px(-9f), py(-23f)),
                        size = Size(18f * u, 18f * u),
                        cornerRadius = CornerRadius(6f * u, 6f * u)
                    )
                    drawRoundRect(
                        color = Color(0xEBFFFFFF),
                        topLeft = Offset(px(-9f), py(-23f)),
                        size = Size(18f * u, 18f * u),
                        cornerRadius = CornerRadius(6f * u, 6f * u),
                        style = Stroke(1.4f * u)
                    )
                    // 牌面上的针形：圆头 + 尖
                    val ink = Color(0xFF2B2000)
                    drawCircle(ink, radius = 3f * u, center = Offset(cx, py(-16.6f)))
                    drawPath(
                        Path().apply {
                            moveTo(px(-2.2f), py(-15.4f))
                            lineTo(px(2.2f), py(-15.4f))
                            lineTo(px(0f), py(-11.9f))
                            close()
                        },
                        ink
                    )

                    if (label != null) {
                        val chipW = (16f + label.length * 9.2f) * u
                        drawRoundRect(
                            color = Primary,
                            topLeft = Offset(cx - chipW / 2f, py(-56f)),
                            size = Size(chipW, 19f * u),
                            cornerRadius = CornerRadius(9.5f * u, 9.5f * u)
                        )
                        drawPath(
                            Path().apply {
                                moveTo(px(-4f), py(-37f))
                                lineTo(px(0f), py(-32f))
                                lineTo(px(4f), py(-37f))
                                close()
                            },
                            Primary
                        )
                    }
                }
            }
        }

        if (label != null) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter).height(19.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = AppText.badge.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                )
            }
        }

        // 命中区只覆盖牌面本体，避免相邻两个标记的点击区互相盖住
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = if (kind == PinKind.Next) 29.dp else 42.dp)
                .size(34.dp)
                .clickable(onClick = onClick)
        )
    }
}

private enum class PinKind { Done, Next }

private val MAP_PIN_W = 96.dp
private val MAP_PIN_H = 58.dp

@Composable
private fun FogCard(state: Map3DUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(BrandSurface.copy(alpha = 0.96f), Radius.card)
            .border(1.dp, HairLine, Radius.card)
            .padding(10.dp)
    ) {
        // 原型 .m3card .r1：图标 + 迷雾 + 已散开 N/12 片 + 右侧 N/3 校区
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).background(PrimaryContainer, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(7.dp))
            Text("迷雾", style = AppText.small.copy(fontWeight = FontWeight.Bold), color = OnSurface)
            Spacer(Modifier.width(7.dp))
            Text(
                "已散开 ${state.fogOpened}/${state.totalShards} 片",
                style = AppText.caption,
                color = Color(0xFF5C6B64)
            )
            Spacer(Modifier.weight(1f))
            // 原型 .pc：始终是绿色小徽标（12/12 时也不换金色）
            Text(
                "${state.completedCampuses}/${state.layers.size} 校区",
                style = AppText.badge.copy(fontWeight = FontWeight.Bold),
                color = Primary,
                modifier = Modifier
                    .background(Color(0xFFEAF5EF), Radius.chip)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.layers.forEach { layer ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(layer.module.total) { i ->
                                // 原型 .m3cells i：11×11、圆角 3，未点亮 #D8E2DD
                                Box(
                                    Modifier
                                        .size(11.dp)
                                        .background(
                                            if (i < layer.unlocked) Primary else Color(0xFFD8E2DD),
                                            RoundedCornerShape(3.dp)
                                        )
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${layer.shortName} ${layer.unlocked}",
                            style = AppText.caption,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // 原型 caption：「每片云 = 一个地标」
            Text(
                "每片云\n= 一个地标",
                style = AppText.badge,
                color = OnSurfaceVariant,
                textAlign = TextAlign.End,
                lineHeight = 12.sp
            )
        }
    }
}

/** 原型 .m3tip：卡片下方那条「点标记看任务 · 打卡后云会散开」 */
@Composable
private fun MapTip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            // 原型 .m3tip：圆角 10（不是胶囊）、白 94%、字号 10.5、字色 #4A5A53
            .background(BrandSurface.copy(alpha = 0.94f), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Layers, contentDescription = null, tint = Color(0xFF6E9A8C), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text("点标记看任务 · 打卡后云会散开", style = AppText.caption, color = Color(0xFF4A5A53))
    }
}

@Composable
private fun FocusPills(state: Map3DUiState, onFocusCampus: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .background(BrandSurface.copy(alpha = 0.94f), Radius.chip)
            .border(1.dp, HairLine, Radius.chip)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FocusPill(
            text = "全览",
            selected = state.focusedModuleId == null,
            onClick = { onFocusCampus(null) }
        )
        state.layers.forEach { layer ->
            FocusPill(
                text = layer.shortName,
                selected = state.focusedModuleId == layer.module.id,
                onClick = { onFocusCampus(layer.module.id) }
            )
        }
    }
}

@Composable
private fun FocusPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = AppText.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        color = if (selected) Color.White else OnSurfaceVariant,
        modifier = Modifier
            .background(if (selected) Primary else Color.Transparent, Radius.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

@Composable
private fun MapSheet(
    state: Map3DUiState,
    onStartCheckIn: (String) -> Unit,
    onOpenPuzzle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = BrandSurface,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFFDDE3D6), Radius.chip)
            )
            Spacer(Modifier.height(10.dp))

            when {
                // ① 原型 p07:all —— 12/12，给成就与拼图入口
                state.allOpen -> AllOpenSheet(onOpenPuzzle = onOpenPuzzle)

                // ② 原型 p07:fog —— 一片云都没散开
                state.fullyFogged -> FoggedSheet(
                    onStartCheckIn = onStartCheckIn,
                    nearest = state.nextTarget
                )

                // ③ 原型 p07:campus —— 点开的那一处所属片区已经 4/4
                state.spotlight != null && state.spotlightCampus?.complete == true ->
                    CampusOpenSheet(
                        layer = state.spotlightCampus,
                        landmark = state.spotlight,
                        onOpenPuzzle = onOpenPuzzle
                    )

                // ④ 原型 p07:partial —— 点开的那一处已经散开
                state.spotlight != null -> SpotlightSheet(
                    landmark = state.spotlight,
                    layer = state.spotlightCampus,
                    onStartCheckIn = onStartCheckIn
                )

                // ⑤ 原型 p07:live 的 nextOnly —— 默认口径就是「下一个目标」
                else -> NextTargetSheet(
                    campus = state.nextTargetCampus,
                    target = state.nextTarget,
                    onStartCheckIn = onStartCheckIn
                )
            }
        }
    }
}

@Composable
private fun SheetHead(
    thumbBackground: Color,
    thumbTint: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String? = null,
    landmarkId: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 原型 .thumb：64×64、圆角 14；partial 态里放的是该地标的图，其余态是暖金底图标
        Box(
            modifier = Modifier.size(64.dp).background(thumbBackground, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (landmarkId != null) {
                LandmarkImage(landmarkId = landmarkId, modifier = Modifier.size(58.dp))
            } else {
                Icon(icon, contentDescription = null, tint = thumbTint, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = AppText.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                if (tag != null) {
                    Spacer(Modifier.width(6.dp))
                    // 原型 .m3sheet .mtag：10px 粗体、#EAF5EF 底、圆角 6
                    Text(
                        tag,
                        style = AppText.badge.copy(fontWeight = FontWeight.Bold),
                        color = Primary,
                        modifier = Modifier
                            .background(Color(0xFFEAF5EF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = AppText.small, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun SheetLead(text: String) {
    Spacer(Modifier.height(9.dp))
    Text(text, style = AppText.small, color = OnSurfaceVariant, lineHeight = 19.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SheetCta(text: String, icon: ImageVector, onClick: () -> Unit) {
    AppButton(text = text, onClick = onClick, icon = icon)
}

/** 12/12：成就卡 */
@Composable
private fun AllOpenSheet(onOpenPuzzle: () -> Unit) {
    SheetHead(
        thumbBackground = WarmThumb,
        thumbTint = GoldText,
        icon = Icons.Outlined.MilitaryTech,
        title = "12 片云全部散开",
        subtitle = "三个校区都集齐了，3D 校园完整可见"
    )
    Spacer(Modifier.height(10.dp))
    SheetCta("查看拼图", Icons.Outlined.Extension, onOpenPuzzle)
}

/** 1 片都没散开：讲清规则 + 去最近的地标 */
@Composable
private fun FoggedSheet(onStartCheckIn: (String) -> Unit, nearest: Landmark) {
    SheetHead(
        thumbBackground = Color(0xFFEDF4F0),
        thumbTint = Primary,
        icon = Icons.Outlined.Cloud,
        title = "校园被云层盖住了",
        subtitle = "12 片云，一片对应一个地标"
    )
    SheetLead("到地标跟前拍一张并打卡成功，它头顶的云就会散开；一个校区 4 个地标都打卡，整片云一起散。")
    SheetCta("去最近的地标打卡", Icons.Outlined.PhotoCamera) { onStartCheckIn(nearest.id) }
}

/** 下一个目标（？不露名字） */
@Composable
private fun NextTargetSheet(campus: CampusLayer?, target: Landmark, onStartCheckIn: (String) -> Unit) {
    SheetHead(
        thumbBackground = WarmThumb,
        thumbTint = GoldText,
        icon = Icons.Outlined.Place,
        title = "？",
        tag = "下一个目标",
        subtitle = "${campus?.module?.name ?: ""} · 已解锁 ${campus?.unlocked ?: 0}/4 片云"
    )
    SheetLead("这一片云还没散开。走到这块地标跟前拍一张、打卡成功，它头顶的云就会散开。")
    SheetCta("去这个地标打卡", Icons.Outlined.PhotoCamera) { onStartCheckIn(target.id) }
}

/** 单个地标已散开：缩略图用这一处的实拍，标题下挂「云层已散开」 */
@Composable
private fun SpotlightSheet(landmark: Landmark, layer: CampusLayer?, onStartCheckIn: (String) -> Unit) {
    SheetHead(
        thumbBackground = Color(0xFFF1F4F0),
        thumbTint = Primary,
        icon = Icons.Outlined.Place,
        title = landmark.name,
        tag = "云层已散开",
        subtitle = "${layer?.module?.name ?: ""} · 已解锁 ${layer?.unlocked ?: 0}/4 片云",
        landmarkId = landmark.id
    )
    SheetLead("这一片云已经散开，${landmark.name}周围露出完整的 3D 地图。")
    SheetCta("去这个地标打卡", Icons.Outlined.Place) { onStartCheckIn(landmark.id) }
}

/** 所属片区整片散开 */
@Composable
private fun CampusOpenSheet(layer: CampusLayer?, landmark: Landmark, onOpenPuzzle: () -> Unit) {
    val campusName = layer?.module?.name ?: ""
    SheetHead(
        thumbBackground = WarmThumb,
        thumbTint = GoldText,
        icon = Icons.Outlined.AutoAwesome,
        title = "$campusName 云层整片散开",
        subtitle = "${landmark.name} 打卡完成 · 4/4 全部点亮"
    )
    SheetLead("最后一片云也在散开 —— 整片 $campusName 露出完整 3D 地图。")
    SheetCta("查看 $campusName" + "拼图", Icons.Outlined.Extension, onOpenPuzzle)
}

/** 原型里暖金缩略底 #FBF3DD / 图标色 #7A5B12 */
private val WarmThumb = Color(0xFFFBF3DD)

