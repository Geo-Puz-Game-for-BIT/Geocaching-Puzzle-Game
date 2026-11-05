package com.bitse.geotreasure.ui.pages.explore

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.model.LocationState
import com.bitse.geotreasure.domain.model.ModuleIds
import com.bitse.geotreasure.ui.components.AppButton
import com.bitse.geotreasure.ui.components.AppButtonStyle
import com.bitse.geotreasure.ui.components.AppTab
import com.bitse.geotreasure.ui.components.AppTabBar
import com.bitse.geotreasure.ui.components.BannerBar
import com.bitse.geotreasure.ui.components.BannerTone
import com.bitse.geotreasure.ui.components.BrandHeader
import com.bitse.geotreasure.ui.components.LandmarkImage
import com.bitse.geotreasure.ui.components.LandmarkPin
import com.bitse.geotreasure.ui.components.SkeletonBox
import com.bitse.geotreasure.ui.theme.AppText
import com.bitse.geotreasure.ui.theme.HairLine
import com.bitse.geotreasure.ui.theme.HairLineStrong
import com.bitse.geotreasure.ui.theme.MapCanvas
import com.bitse.geotreasure.ui.theme.MapRoad
import com.bitse.geotreasure.ui.theme.OnSurface
import com.bitse.geotreasure.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.ui.theme.Primary
import com.bitse.geotreasure.ui.theme.Radius
import com.bitse.geotreasure.ui.theme.Surface as BrandSurface
import com.bitse.geotreasure.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 地图上四个标记的位置（%），与原型 PIN_POS 完全一致；下标 = 片区内 slot */
private val PIN_POS: Map<String, List<Pair<Float, Float>>> = mapOf(
    ModuleIds.SOUTH to listOf(
        0.27f to 0.30f, 0.27f to 0.55f, 0.73f to 0.28f, 0.73f to 0.53f
    ),
    ModuleIds.NORTH to listOf(
        0.46f to 0.27f, 0.74f to 0.25f, 0.22f to 0.47f, 0.72f to 0.52f
    ),
    ModuleIds.EAST to listOf(
        0.26f to 0.28f, 0.73f to 0.26f, 0.26f to 0.54f, 0.74f to 0.51f
    )
)

@Composable
fun ExploreRoute(
    onOpenLandmark: (landmarkId: String, done: Boolean) -> Unit,
    onOpen3D: () -> Unit,
    onOpenPuzzle: () -> Unit,
    onOpenRecords: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onLocationPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onLocationPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    ExploreScreen(
        state = state,
        onSelectModule = viewModel::selectModule,
        onOpenLandmark = onOpenLandmark,
        onOpen3D = onOpen3D,
        onOpenPuzzle = onOpenPuzzle,
        onOpenRecords = onOpenRecords,
        onRequestLocation = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onRetryLoad = viewModel::retryLoad
    )
}

@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onSelectModule: (String) -> Unit,
    onOpenLandmark: (landmarkId: String, done: Boolean) -> Unit,
    onOpen3D: () -> Unit,
    onOpenPuzzle: () -> Unit,
    onOpenRecords: () -> Unit,
    onRequestLocation: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BrandHeader(onProfileClick = onOpenRecords)

            Box(Modifier.weight(1f)) {
                MapBackground()

                // 原型 p01：loading / error / denied 三种情况都不画水滴
                if (state.showPins) {
                    PinsLayer(
                        module = state.module,
                        ownedSlots = state.ownedSlots,
                        targetSlot = state.target.slot,
                        onPinClick = { landmark, owned, isTarget ->
                            when {
                                owned -> onOpenLandmark(landmark.id, true)
                                isTarget -> onOpenLandmark(landmark.id, false)
                                else -> scope.launch {
                                    snackbar.showSnackbar("先完成当前推荐任务，解锁后可查看")
                                }
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Spacer(Modifier.height(12.dp))
                    CampusSwitch(
                        modules = state.modules,
                        selectedId = state.selectedModuleId,
                        onSelect = onSelectModule
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressCard(
                        module = state.module,
                        ownedSlots = state.ownedSlots
                    )

                    when {
                        state.location == LocationState.PERMISSION_DENIED -> {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.clip(Radius.card)) {
                                BannerBar(
                                    text = "需要位置权限，才能找到附近地标",
                                    tone = BannerTone.Error,
                                    actionLabel = "去设置",
                                    onAction = onRequestLocation
                                )
                            }
                        }

                        !state.online -> {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.clip(Radius.card)) {
                                BannerBar(
                                    // 原型 netBar 的原文，全站统一这一句
                                    text = "网络中断，暂时拿不到数据",
                                    tone = BannerTone.Error,
                                    icon = Icons.Outlined.CloudOff,
                                    actionLabel = "重试",
                                    onAction = onRetryLoad
                                )
                            }
                        }

                        state.location == LocationState.WEAK_SIGNAL -> {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.clip(Radius.card)) {
                                BannerBar(
                                    text = "定位信号弱，位置可能不准",
                                    tone = BannerTone.Warn,
                                    icon = Icons.Outlined.SignalCellularAlt,
                                    actionLabel = "重新定位",
                                    onAction = onRequestLocation
                                )
                            }
                        }
                    }
                }

                // 2D / 3D 切换（原型 .mapmode：容器圆角 12、按钮 38×32 圆角 9、未选中字色 #63736C）
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 278.dp)
                        .background(Color(0xF5FFFEFA), RoundedCornerShape(12.dp))
                        .border(1.dp, HairLine, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 32.dp)
                            .background(Primary, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "2D",
                            style = AppText.tiny.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    // 3D 必须真的能点进 P07：之前只做了选中态样式、没绑点击，等于死按钮
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onOpen3D() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "3D",
                            style = AppText.tiny.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF63736C)
                        )
                    }
                }

                // 回到我的位置
                Surface(
                    onClick = { scope.launch { snackbar.showSnackbar("已回到你的位置") } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 224.dp)
                        .size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    contentColor = Primary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.MyLocation,
                            contentDescription = "回到我的位置",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = "地图为示意，不对应真实道路与坐标",
                    style = AppText.caption,
                    color = OnSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 222.dp)
                        .background(Color(0xEBFFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                TaskCard(
                    state = state,
                    onOpenLandmark = onOpenLandmark,
                    onRetryLoad = onRetryLoad,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }

            AppTabBar(
                current = AppTab.Explore,
                shards = state.shards,
                totalShards = state.totalShards,
                onSelect = { tab ->
                    when (tab) {
                        AppTab.Explore -> Unit
                        AppTab.Puzzle -> onOpenPuzzle()
                        AppTab.Records -> onOpenRecords()
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 96.dp)
        )
    }
}

/*
 * 这里原来有个 ownedSlotsFor() 补丁：只返回当前片区的已收格子，导致 Tab 角标数字偏小。
 * 现在 shards 直接来自 UserProgress.totalShards，与 P05 / P06 同一个口径，补丁已删。
 */

@Composable
private fun CampusSwitch(
    modules: List<CampusModule>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.pill)
            .background(BrandSurface)
            .border(1.dp, HairLine, Radius.pill)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modules.forEach { module ->
            val selected = module.id == selectedId
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(Radius.pill)
                    .background(if (selected) Primary else Color.Transparent)
                    // 整颗胶囊都是热区；已选中的再点一次也不报错，只是不变
                    .clickable { onSelect(module.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = module.name,
                    style = AppText.body.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (selected) Color.White else Color(0xFF5F6B64)
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(module: CampusModule, ownedSlots: List<Int>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BrandSurface)
            .border(1.dp, HairLine, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = module.name + "探索",
                style = AppText.small.copy(fontWeight = FontWeight.Bold),
                color = OnSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = ownedSlots.size.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary
            )
            Text(
                text = "/" + module.total,
                style = AppText.tiny.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF8A958D)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            module.landmarks.forEach { landmark ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (ownedSlots.contains(landmark.slot)) Primary else Color(0xFFDDE3D6))
                )
            }
        }
    }
}

/** 自绘示意底图：只给纹理与方向感，不画建筑方框（原型 MAPBASE 同一原则） */
@Composable
private fun MapBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // 示意底图：只为「这是校园」服务，不还原真实道路与坐标
        drawRect(MapCanvas)

        // 绿地：比底图亮一档，但饱和度压住，不抢水滴
        val lawn = Color(0xFFDCE8CE)
        drawOval(lawn, topLeft = Offset(w * 0.01f, h * 0.19f), size = Size(w * 0.40f, h * 0.18f))
        drawOval(lawn, topLeft = Offset(w * 0.70f, h * 0.14f), size = Size(w * 0.32f, h * 0.16f))
        drawOval(lawn, topLeft = Offset(w * 0.44f, h * 0.62f), size = Size(w * 0.38f, h * 0.16f))

        // 主路：米色，比底图更亮，形成路网骨架
        val road = Color(0xFFF5F1E5)
        drawRect(road, topLeft = Offset(0f, h * 0.155f), size = Size(w, h * 0.055f))
        drawRect(road, topLeft = Offset(0f, h * 0.60f), size = Size(w, h * 0.05f))
        drawRect(road, topLeft = Offset(w * 0.17f, 0f), size = Size(w * 0.075f, h))
        drawRect(road, topLeft = Offset(w * 0.68f, 0f), size = Size(w * 0.065f, h))

        // 路面中线
        val lane = Color(0xFFE7E1D1)
        drawLine(lane, Offset(0f, h * 0.1825f), Offset(w, h * 0.1825f), strokeWidth = 1.2.dp.toPx())
        drawLine(lane, Offset(0f, h * 0.625f), Offset(w, h * 0.625f), strokeWidth = 1.2.dp.toPx())

        // 水面（北湖的位置示意）
        drawOval(
            color = Color(0xFFCFE3D3),
            topLeft = Offset(w * 0.60f, h * 0.66f),
            size = Size(w * 0.30f, h * 0.10f)
        )

        // 建筑：米色圆角块 + 描边 + 底部投影线，避开四个水滴的位置
        // 之前用 0xFFF8F6EE 几乎等于背景色，看起来像"图没加载"的空白方块
        val buildingFill = Color(0xFFEDE7D8)
        val buildingLine = Color(0xFFD6CCB4)
        val buildingShade = Color(0xFFDCD3BE)
        val blocks = listOf(
            floatArrayOf(0.045f, 0.045f, 0.10f, 0.07f),
            floatArrayOf(0.31f, 0.035f, 0.13f, 0.08f),
            floatArrayOf(0.47f, 0.26f, 0.09f, 0.06f),
            floatArrayOf(0.045f, 0.36f, 0.09f, 0.06f),
            floatArrayOf(0.36f, 0.38f, 0.12f, 0.07f),
            floatArrayOf(0.79f, 0.36f, 0.13f, 0.07f),
            floatArrayOf(0.50f, 0.755f, 0.13f, 0.07f),
            floatArrayOf(0.06f, 0.755f, 0.10f, 0.06f)
        )
        val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        blocks.forEach { b ->
            val topLeft = Offset(w * b[0], h * b[1])
            val blockSize = Size(w * b[2], h * b[3])
            drawRoundRect(buildingFill, topLeft, blockSize, corner)
            // 底部一条窄投影带，让方块读起来像建筑体块而不是空占位
            val shadeH = blockSize.height * 0.28f
            drawRoundRect(
                buildingShade,
                Offset(topLeft.x, topLeft.y + blockSize.height - shadeH),
                Size(blockSize.width, shadeH),
                corner
            )
            drawRoundRect(buildingLine, topLeft, blockSize, corner, style = Stroke(1.2.dp.toPx()))
        }

        // 树：小圆点，沿绿地和路侧散布
        val treeColor = Color(0xFFC7D9B3)
        val trees = listOf(
            0.06f to 0.235f, 0.13f to 0.27f, 0.20f to 0.225f, 0.12f to 0.315f,
            0.74f to 0.18f, 0.80f to 0.215f, 0.88f to 0.19f, 0.94f to 0.23f,
            0.50f to 0.665f, 0.57f to 0.70f, 0.64f to 0.675f,
            0.30f to 0.55f, 0.44f to 0.53f, 0.60f to 0.50f
        )
        trees.forEach { (x, y) ->
            drawCircle(treeColor, radius = 5.5.dp.toPx(), center = Offset(w * x, h * y))
        }

        // 场地深处压一层极淡的雾，避免大面积纯色发空
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color(0x0AFFFFFF),
                1f to Color(0x12000000)
            )
        )
    }
}

@Composable
private fun PinsLayer(
    module: CampusModule,
    ownedSlots: List<Int>,
    targetSlot: Int,
    onPinClick: (Landmark, Boolean, Boolean) -> Unit
) {
    val positions = PIN_POS[module.id] ?: PIN_POS.getValue(ModuleIds.SOUTH)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        module.landmarks.forEach { landmark ->
            val pos = positions.getOrNull(landmark.slot) ?: return@forEach
            val owned = ownedSlots.contains(landmark.slot)
            val isTarget = landmark.slot == targetSlot

            LandmarkPin(
                done = owned,
                label = if (owned) landmark.name else null,
                onClick = { onPinClick(landmark, owned, isTarget) },
                modifier = Modifier.offset {
                    IntOffset(
                        (pos.first * widthPx).roundToInt() - 17.dp.roundToPx(),
                        (pos.second * heightPx).roundToInt() - 34.dp.roundToPx()
                    )
                }
            )
        }
    }
}

@Composable
private fun TaskCard(
    state: ExploreUiState,
    onOpenLandmark: (String, Boolean) -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(Radius.sheet)
            .background(BrandSurface)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(HairLineStrong)
        )
        Spacer(Modifier.height(12.dp))

        when {
            // 原型 p01:loading —— 分块占位，不白屏
            state.loading -> TaskCardSkeleton()
            // 原型 p01:error —— 保留卡片形状，给「重新加载」这个唯一出口
            state.loadFailed -> TaskCardError(onRetryLoad = onRetryLoad)
            else -> TaskCardBody(state = state, onOpenLandmark = onOpenLandmark)
        }
    }
}

@Composable
private fun TaskCardSkeleton() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SkeletonBox(modifier = Modifier.size(64.dp), corner = 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.58f).height(15.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.80f).height(11.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.40f).height(11.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    SkeletonBox(modifier = Modifier.fillMaxWidth().height(50.dp), corner = 14.dp)
}

@Composable
private fun TaskCardError(onRetryLoad: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(SurfaceVariant, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Map, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("附近地标没加载出来", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "网络看起来不稳，先回探索页看看，也可以直接重试",
            style = AppText.small,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        AppButton(
            text = "重新加载",
            onClick = onRetryLoad,
            style = AppButtonStyle.Tonal,
            icon = Icons.Outlined.Refresh,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TaskCardBody(
    state: ExploreUiState,
    onOpenLandmark: (String, Boolean) -> Unit
) {
    val target = state.target
    val owned = state.owns(target.slot)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(Radius.thumb)
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            LandmarkImage(
                landmarkId = target.id,
                modifier = Modifier.fillMaxSize(),
                big = false
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (owned) target.name else "？",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OnSurface
            )
            Text(
                text = if (owned) "已打卡 · 碎片已归位" else "走近后揭晓 · 拍一张人物与地标同框的照片",
                style = AppText.small,
                color = OnSurfaceVariant
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.GpsFixed,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = state.distanceText,
                    style = AppText.tiny.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                Text(
                    text = "  ·  " + target.difficulty + "  ·  " + target.terrain,
                    style = AppText.tiny,
                    color = OnSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    AppButton(
        text = if (owned) "查看详情" else "查看任务",
        onClick = { onOpenLandmark(target.id, owned) },
        modifier = Modifier.fillMaxWidth(),
        // 原型只在「定位未开启 / 加载失败」时禁用；网络中断仍可点进详情看离线内容
        enabled = state.location != LocationState.PERMISSION_DENIED && !state.loadFailed,
        icon = Icons.Outlined.ChevronRight
    )
}
