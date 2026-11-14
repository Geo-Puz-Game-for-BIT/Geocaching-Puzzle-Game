package com.bitse.geotreasure.feature.overview

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.app.ExplorationProgressStore
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.AppTab
import com.bitse.geotreasure.core.ui.components.AppTabBar
import com.bitse.geotreasure.core.ui.components.PageHeader
import com.bitse.geotreasure.core.ui.components.zoneFullName
import com.bitse.geotreasure.core.ui.theme.Background
import com.bitse.geotreasure.core.ui.theme.GoldText
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一枚标记：云散开（已打卡）= opened */
data class OverviewPin(val landmark: Landmark, val opened: Boolean)

data class OverviewZone(
    val zone: CampusZone,
    val label: String,
    val done: Int,
    val total: Int,
    val pins: List<OverviewPin>,
) {
    val complete: Boolean get() = done >= total
}

data class Overview3DUiState(
    val zones: List<OverviewZone> = emptyList(),
    /** null = 全览；否则聚焦该校区 */
    val selectedZone: CampusZone? = null,
) {
    val openedTotal: Int get() = zones.sumOf { it.done }
    val doneMods: Int get() = zones.count { it.complete }
}

sealed interface Overview3DAction {
    data class SelectZone(val zone: CampusZone?) : Overview3DAction
}

class Overview3DViewModel(progressStore: ExplorationProgressStore) : ViewModel() {
    private val mutableUiState = MutableStateFlow(Overview3DUiState())
    val uiState: StateFlow<Overview3DUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            progressStore.completedLandmarkIds.collect { ids ->
                val zones = ZONE_LABELS.map { (zone, label) ->
                    val inZone = LiangxiangTreasureCatalog.landmarks
                        .filter { it.campusZone == zone }
                        .sortedBy { it.pieceIndex }
                    OverviewZone(
                        zone = zone,
                        label = label,
                        done = inZone.count { it.id in ids },
                        total = inZone.size,
                        pins = inZone.map { OverviewPin(it, it.id in ids) },
                    )
                }
                mutableUiState.update { current ->
                    current.copy(
                        zones = zones,
                        selectedZone = current.selectedZone?.takeIf { selected ->
                            zones.any { it.zone == selected }
                        },
                    )
                }
            }
        }
    }

    fun onAction(action: Overview3DAction) {
        when (action) {
            is Overview3DAction.SelectZone -> mutableUiState.update { it.copy(selectedZone = action.zone) }
        }
    }

    companion object {
        fun factory(progressStore: ExplorationProgressStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    Overview3DViewModel(progressStore) as T
            }
    }
}

/** 片区标签：与原型 CN 映射一致（MP-A 南 / MP-B 北 / MP-C 东），顺序 南→北→东 */
internal val ZONE_LABELS: List<Pair<CampusZone, String>> = listOf(
    CampusZone.SOUTH to "南",
    CampusZone.NORTH to "北",
    CampusZone.EAST to "东",
)

// ── P07 配色（.fig / 原型 CSS 实测）───────────────────────────────────
private val FogChipBg = Color(0xFFEAF5EF)      // 迷雾卡图标盒 / 「M/3 校区」章 / mtag
private val CellOff = Color(0xFFD8E2DD)        // 迷雾小格未开
private val ZoneCountLabel = Color(0xFF7A8A83) // 「南 4」小字
private val CloudThumbBg = Color(0xFFEDF4F0)   // fog 态缩略图底
private val NotInTitle = Color(0xFF6C7464)     // 「本期不纳入」
private val NotInSub = Color(0xFF7E8476)
private val TipTextC = Color(0xFF4A5A53)       // 提示条文字
private val TipIconC = Color(0xFF6E9A8C)
private val ZoomIdle = Color(0xFF63736C)       // 分段未选中 / 2D
private val HandleGray = Color(0xFFB9C2B6)     // 底卡拖柄
private val SubText = Color(0xFF5C6B64)        // 迷雾卡「已散开」

// 3D 地图配色（取自原型 3D 地图色板，浅色系不压地图）
private val CampusPlatform = Color(0xFFE7EDE8)
private val CampusPlatformSide = Color(0xFFB8C6BF)
private val CloudWhite = Color(0xFFF7FAF7)
private val CloudShadow = Color(0xFF232D2A)

/** 底部信息卡四态（对应 .fig：fog / next(=live) / campus / all；unlock 动画态不单列，见交付文档） */
private sealed interface SheetSpec {
    data object Fog : SheetSpec
    data object All : SheetSpec
    data class CampusDone(val zoneName: String, val lastLandmark: String) : SheetSpec
    data class NextTarget(val zoneName: String, val unlocked: Int, val landmarkId: String) : SheetSpec
}

private fun sheetSpecOf(state: Overview3DUiState): SheetSpec = when {
    state.openedTotal >= 12 -> SheetSpec.All
    state.openedTotal == 0 -> SheetSpec.Fog
    else -> {
        val focused = state.selectedZone
            ?.let { sel -> state.zones.firstOrNull { it.zone == sel } }
            ?: state.zones.firstOrNull { !it.complete }
            ?: state.zones.first()
        val zoneName = zoneFullName(focused.zone)
        if (focused.complete) {
            SheetSpec.CampusDone(zoneName, focused.pins.last { it.opened }.landmark.name)
        } else {
            SheetSpec.NextTarget(zoneName, focused.done, focused.pins.first { !it.opened }.landmark.id)
        }
    }
}

/**
 * P07 3D 校园地图（严格按 .fig / 原型 HTML）：
 * 头栏（标题+副标题+右侧 40dp 圆钮）→ 3D 地图区（径向渐变底 + 迷雾进度卡叠层 +
 * 全览/南/北/东分段 + 提示条 + 3D/2D 切换 + 「本期不纳入」标注）→ 底部信息卡（四态文案）→ 底部三 Tab。
 */
@Composable
fun Overview3DScreen(
    viewModel: Overview3DViewModel,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenPuzzle: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenLandmark: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageHeader(
            title = "3D 校园地图",
            subtitle = "良乡 · 云层已散开 ${state.openedTotal}/12 片 · ${state.doneMods}/3 校区",
            onBack = onBack,
            actionIcon = Icons.Outlined.Map,
            actionDescription = "一片云对应一个地标",
            onAction = onOpenMap,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 径向渐变底（原型 .m3wrap：radial-gradient(120% 70% at 50% 8%)）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to Color(0xFFF2F7EC),
                                    0.42f to Color(0xFFE7F0E3),
                                    0.72f to Color(0xFFDCE9DC),
                                    1f to Color(0xFFD3E2D6),
                                ),
                                center = Offset(size.width / 2f, size.height * 0.08f),
                                radius = size.width * 1.25f,
                            )
                        )
                    }
            )
            CampusCanvas(state = state, onOpenLandmark = onOpenLandmark)

            // 左上叠层：迷雾进度卡 → 全览分段 → 提示条（与原型 .m3card/.m3zoom/.m3tip 顺序一致）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .align(Alignment.TopStart)
            ) {
                Spacer(Modifier.height(12.dp))
                FogCard(state)
                Spacer(Modifier.height(10.dp))
                ZoomChips(state, onSelect = { viewModel.onAction(Overview3DAction.SelectZone(it)) })
                Spacer(Modifier.height(10.dp))
                TipRow()
            }

            // 右上 3D/2D 切换（原型 .m3toggle，2D → 回 P01 地图）
            M3Toggle(
                onOpen2D = onOpenMap,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp),
            )

            // 「本期不纳入 · 融创楼 · 工训楼」标注（原型在地图右中部）
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 210.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("本期不纳入", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NotInTitle)
                Spacer(Modifier.height(3.dp))
                Text("融创楼 · 工训楼", fontSize = 10.sp, color = NotInSub)
            }
        }

        M3Sheet(
            state = state,
            onOpenPuzzle = onOpenPuzzle,
            onOpenLandmark = onOpenLandmark,
        )

        // P07 原型：底部三 Tab，「探索」为选中态（点探索回 2D 地图）
        AppTabBar(
            current = AppTab.Explore,
            shards = state.openedTotal,
            onSelect = { tab ->
                when (tab) {
                    AppTab.Explore -> onOpenMap()
                    AppTab.Puzzle -> onOpenPuzzle()
                    AppTab.Records -> onOpenRecords()
                }
            },
        )
    }
}

/** 迷雾进度卡（原型 .m3card）：图标盒 + 迷雾/已散开 N/12 片 + 校区章；下排三组 2×2 小格 + 注释 */
@Composable
private fun FogCard(state: Overview3DUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 3.dp,
    ) {
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(FogChipBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("迷雾", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Spacer(Modifier.width(8.dp))
                Text("已散开 ", fontSize = 12.sp, color = SubText)
                Text("${state.openedTotal}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
                Text("/12 片", fontSize = 12.sp, color = SubText)
                Spacer(Modifier.weight(1f))
                Surface(shape = Radius.pill, color = FogChipBg) {
                    Text(
                        "${state.doneMods}/3 校区",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.zones.forEach { zone ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            zone.pins.forEach { pin ->
                                Box(
                                    Modifier
                                        .size(11.dp)
                                        .clip(Radius.pill)
                                        .background(if (pin.opened) Primary else CellOff),
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("${zone.label} ${zone.done}", fontSize = 9.sp, color = ZoneCountLabel)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "每片云\n= 一个地标",
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Right,
                )
            }
        }
    }
}

/** 全览/南/北/东 聚焦分段（原型 .m3zoom）：白色容器，选中项主色底白字 */
@Composable
private fun ZoomChips(state: Overview3DUiState, onSelect: (CampusZone?) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ZoomChip(label = "全览", selected = state.selectedZone == null, onClick = { onSelect(null) })
            state.zones.forEach { zone ->
                ZoomChip(label = zone.label, selected = state.selectedZone == zone.zone, onClick = { onSelect(zone.zone) })
            }
        }
    }
}

@Composable
private fun ZoomChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .defaultMinSize(minWidth = 38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else ZoomIdle)
    }
}

/** 提示条（原型 .m3tip） */
@Composable
private fun TipRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Layers, contentDescription = null, tint = TipIconC, modifier = Modifier.size(16.dp))
            Text("点标记看任务 · 打卡后云会散开", fontSize = 10.5.sp, color = TipTextC)
        }
    }
}

/** 3D/2D 切换（原型 .m3toggle）：3D 常亮，2D 回 P01 地图 */
@Composable
private fun M3Toggle(onOpen2D: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ToggleBtn("3D", selected = true, onClick = {})
            ToggleBtn("2D", selected = false, onClick = onOpen2D)
        }
    }
}

@Composable
private fun ToggleBtn(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else ZoomIdle)
    }
}

/** 底部信息卡（原型 .m3sheet）：拖柄 + 64dp 缩略图 + 标题（可带 mtag）+ 副标题 + lead + 整宽主按钮 */
@Composable
private fun M3Sheet(
    state: Overview3DUiState,
    onOpenPuzzle: () -> Unit,
    onOpenLandmark: (String) -> Unit,
) {
    val spec = sheetSpecOf(state)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(Radius.pill)
                    .background(HandleGray)
            )
            Spacer(Modifier.height(12.dp))
            when (spec) {
                SheetSpec.Fog -> {
                    SheetHead(
                        thumbBg = CloudThumbBg, thumbTint = Primary, icon = Icons.Outlined.Cloud,
                        title = "校园被云层盖住了", tag = null,
                        sub = "12 片云，一片对应一个地标",
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "到地标跟前拍一张并打卡成功，它头顶的云就会散开；一个校区 4 个地标都打卡，整片云一起散。",
                        fontSize = 12.sp, lineHeight = 19.sp, color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        text = "去最近的地标打卡",
                        icon = Icons.Outlined.PhotoCamera,
                        onClick = {
                            val first = state.zones.firstOrNull { !it.complete }
                                ?.pins?.firstOrNull { !it.opened }?.landmark?.id
                            if (first != null) onOpenLandmark(first) else onOpenPuzzle()
                        },
                    )
                }
                SheetSpec.All -> {
                    SheetHead(
                        thumbBg = GoldThumbBg, thumbTint = GoldText, icon = Icons.Rounded.Stars,
                        title = "12 片云全部散开", tag = null,
                        sub = "三个校区都集齐了，3D 校园完整可见",
                    )
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        text = "查看拼图",
                        icon = Icons.Outlined.Extension,
                        onClick = onOpenPuzzle,
                    )
                }
                is SheetSpec.CampusDone -> {
                    SheetHead(
                        thumbBg = GoldThumbBg, thumbTint = GoldText, icon = Icons.Outlined.Extension,
                        title = "${spec.zoneName} 云层整片散开", tag = null,
                        sub = "${spec.lastLandmark} 打卡完成 · 4/4 全部点亮",
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "最后一片云也在散开 —— 整片 ${spec.zoneName} 露出完整 3D 地图。",
                        fontSize = 12.sp, lineHeight = 19.sp, color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        text = "查看 ${spec.zoneName}拼图",
                        icon = Icons.Outlined.Extension,
                        onClick = onOpenPuzzle,
                    )
                }
                is SheetSpec.NextTarget -> {
                    SheetHead(
                        thumbBg = GoldThumbBg, thumbTint = GoldText, icon = Icons.Rounded.LocationOn,
                        title = "？", tag = "下一个目标",
                        sub = "${spec.zoneName} · 已解锁 ${spec.unlocked}/4 片云",
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "这一片云还没散开。走到这块地标跟前拍一张、打卡成功，它头顶的云就会散开。",
                        fontSize = 12.sp, lineHeight = 19.sp, color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        text = "去这个地标打卡",
                        icon = Icons.Outlined.PhotoCamera,
                        onClick = { onOpenLandmark(spec.landmarkId) },
                    )
                }
            }
        }
    }
}

private val GoldThumbBg = Color(0xFFFBF3DD)

@Composable
private fun SheetHead(
    thumbBg: Color,
    thumbTint: Color,
    icon: ImageVector,
    title: String,
    tag: String?,
    sub: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(thumbBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = thumbTint, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                if (tag != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = FogChipBg) {
                        Text(
                            tag,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(sub, fontSize = 12.5.sp, color = OnSurfaceVariant)
        }
    }
}

/** 校区在未缩放坐标系里的平台位置（比例坐标，随画布尺寸缩放） */
private fun zoneRect(zone: CampusZone, w: Float, h: Float): Rect = when (zone) {
    CampusZone.NORTH -> Rect(w * 0.05f, h * 0.06f, w * 0.50f, h * 0.38f)
    CampusZone.SOUTH -> Rect(w * 0.05f, h * 0.56f, w * 0.50f, h * 0.88f)
    CampusZone.EAST -> Rect(w * 0.54f, h * 0.24f, w * 0.95f, h * 0.66f)
}

/** 校区内 2×2 地标槽位中心（按 pieceIndex 排布） */
private fun slotCenters(zone: CampusZone, w: Float, h: Float, landmarks: List<Landmark>): Map<Int, Offset> {
    val rect = zoneRect(zone, w, h)
    val padX = rect.width * 0.12f
    val padY = rect.height * 0.16f
    val slotW = (rect.width - padX * 2) / 2f
    val slotH = (rect.height - padY * 2) / 2f
    return landmarks.associate { landmark ->
        val col = landmark.pieceIndex % 2
        val row = landmark.pieceIndex / 2
        landmark.pieceIndex to Offset(
            rect.left + padX + slotW * (col + 0.5f),
            rect.top + padY + slotH * (row + 0.5f),
        )
    }
}

@Composable
private fun CampusCanvas(state: Overview3DUiState, onOpenLandmark: (String) -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    val scale = remember { Animatable(1f) }
    val offX = remember { Animatable(0f) }
    val offY = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(state.selectedZone, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val targetScale = 2.1f
        if (state.selectedZone == null) {
            launch { scale.animateTo(1f) }
            launch { offX.animateTo(0f) }
            launch { offY.animateTo(0f) }
        } else {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val rect = zoneRect(state.selectedZone, w, h)
            val center = Offset(rect.center.x, rect.center.y)
            val tx = w / 2f - center.x * targetScale
            val ty = h / 2f - center.y * targetScale
            launch { scale.animateTo(targetScale) }
            launch { offX.animateTo(tx) }
            launch { offY.animateTo(ty) }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(state.zones) {
                detectTapGestures { tap ->
                    if (size == IntSize.Zero || scale.value <= 0f) return@detectTapGestures
                    val q = Offset(
                        (tap.x - offX.value) / scale.value,
                        (tap.y - offY.value) / scale.value,
                    )
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    for (zone in state.zones) {
                        val slots = slotCenters(zone.zone, w, h, zone.pins.map { it.landmark })
                        zone.pins.forEach { pin ->
                            if (!pin.opened) return@forEach
                            val center = slots[pin.landmark.pieceIndex] ?: return@forEach
                            val radius = slotRadius(zone.zone, w, h)
                            if ((q - center).getDistance() <= radius * 1.15f) {
                                onOpenLandmark(pin.landmark.id)
                            }
                        }
                    }
                }
            },
    ) {
        val w = this.size.width
        val h = this.size.height
        withTransform({
            translate(offX.value, offY.value)
            scale(scale.value, scale.value, pivot = Offset.Zero)
        }) {
            drawCampus(state, w, h, textMeasurer)
        }
    }
}

private fun slotRadius(zone: CampusZone, w: Float, h: Float): Float {
    val rect = zoneRect(zone, w, h)
    return minOf(rect.width, rect.height) * 0.14f
}

private fun DrawScope.drawCampus(
    state: Overview3DUiState,
    w: Float,
    h: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val depth = h * 0.018f

    // 校区间步道
    val southC = zoneRect(CampusZone.SOUTH, w, h).center
    val northC = zoneRect(CampusZone.NORTH, w, h).center
    val eastC = zoneRect(CampusZone.EAST, w, h).center
    drawLine(CampusPlatformSide, Offset(northC.x, northC.y), Offset(eastC.x, eastC.y), strokeWidth = w * 0.012f)
    drawLine(CampusPlatformSide, Offset(southC.x, southC.y), Offset(eastC.x, eastC.y), strokeWidth = w * 0.012f)

    state.zones.forEach { zone ->
        val rect = zoneRect(zone.zone, w, h)
        val corner = w * 0.05f

        // 平台侧影（2.5D 厚度）
        drawRoundRect(
            color = CampusPlatformSide,
            topLeft = Offset(rect.left, rect.top + depth),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        )
        // 平台顶面
        drawRoundRect(
            color = CampusPlatform,
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        )

        val slots = slotCenters(zone.zone, w, h, zone.pins.map { it.landmark })
        zone.pins.forEach { pin ->
            val center = slots[pin.landmark.pieceIndex] ?: return@forEach
            val base = slotRadius(zone.zone, w, h)
            if (pin.opened) {
                // 打卡后：干净孔洞 + 标记点
                drawCircle(Color.White.copy(alpha = 0.9f), radius = base, center = center)
                drawCircle(Primary, radius = base * 0.46f, center = center)
                drawCircle(Color.White, radius = base * 0.16f, center = center)
                drawText(
                    textMeasurer = textMeasurer,
                    text = pin.landmark.name,
                    topLeft = Offset(center.x - base * 1.2f, center.y + base * 1.15f),
                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF202923), fontWeight = FontWeight.Bold),
                )
            } else {
                // 未打卡：云层盖住（暗部 + 云体 + 高光三层）
                drawCircle(CloudShadow.copy(alpha = 0.14f), radius = base * 1.05f, center = center + Offset(0f, depth))
                drawCircle(CloudWhite.copy(alpha = 0.97f), radius = base * 0.78f, center = center + Offset(-base * 0.35f, base * 0.1f))
                drawCircle(CloudWhite.copy(alpha = 0.97f), radius = base * 0.9f, center = center + Offset(base * 0.3f, -base * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.85f), radius = base * 0.5f, center = center + Offset(0f, -base * 0.32f))
            }
        }

        // 校区标签
        drawText(
            textMeasurer = textMeasurer,
            text = "${zone.label}·${zone.done}/${zone.total}",
            topLeft = Offset(rect.left + rect.width * 0.04f, rect.top + rect.height * 0.02f),
            style = TextStyle(fontSize = 12.sp, color = Color(0xFF404A46), fontWeight = FontWeight.Bold),
        )
    }
}
