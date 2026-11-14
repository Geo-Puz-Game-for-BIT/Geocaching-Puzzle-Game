package com.bitse.geotreasure.feature.overview

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.R
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
import kotlin.math.roundToInt
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
 * P07 3D 校园地图（按 .fig / 原型 HTML，浮层改上下排布防重叠）：
 * 头栏（标题+副标题+右侧 40dp 圆钮）→ 迷雾进度卡 + 全览/南/北/东分段(右 3D/2D 切换) + 提示条 →
 * 3D 地图区（径向渐变底 + 三校区插画卡片 + 「本期不纳入」标注）→ 底部信息卡（四态文案）→ 底部三 Tab。
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

        // 迷雾进度卡 / 分段 / 提示条独立排在画布上方（原型为画布内浮层，真机上会盖住北校区
        // 卡片且聚焦缩放后卡片钻入浮层下方 → 改为不重叠的上下排布）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            FogCard(state)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    ZoomChips(state, onSelect = { viewModel.onAction(Overview3DAction.SelectZone(it)) })
                }
                Spacer(Modifier.width(8.dp))
                // 3D/2D 切换（原型 .m3toggle，2D → 回 P01 地图）
                M3Toggle(onOpen2D = onOpenMap)
            }
            Spacer(Modifier.height(8.dp))
            TipRow()
        }

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

            // 「本期不纳入 · 融创楼 · 工训楼」标注（右下空地，避开三张片区图）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 26.dp, bottom = 14.dp),
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

// ── 3D 底图：campus-unified-v5 三校区插画（源 D:\campus-unified-v5，按 calibration.json 裁切）──
// 三张图共用一套米制投影：x=(lon-lon0)·mPerDeg·1.5，y=-(lat-lat0)·mPerDeg·1.125。
// 钉子不直接取绝对像素（部分地标落在插画覆盖面之外），而是按同校区 4 个地标的投影坐标
// min-max 归一后铺进卡片内框，保留相对方位且保证全部可见。
private const val MAP3D_ORIGIN_LON = 116.16447
private const val MAP3D_ORIGIN_LAT = 39.73013
private const val MAP3D_EAST_M_PER_DEG = 85729.07360052191
private const val MAP3D_NORTH_M_PER_DEG = 111029.4509960947
private const val MAP3D_PX_PER_EAST_M = 1.5
private const val MAP3D_PX_PER_NORTH_M = 1.125

/** 插画裁切后的宽高比（与 res/drawable-nodpi/map3d_*.png 一致） */
private fun zoneAspect(zone: CampusZone): Float = when (zone) {
    CampusZone.NORTH -> 738f / 1019f
    CampusZone.SOUTH -> 742f / 676f
    CampusZone.EAST -> 708f / 699f
}

/**
 * 虚拟版式框（宽 1.0、高 1.10，单位=画布宽），三张卡片按此框内的比例坐标摆放，
 * 再整体等比 contain 到画布并居中 —— 任何屏幕比例下卡片都不会重叠或溢出。
 * 方位沿用原型三平台：北在上、东在右中、南在下。
 */
private const val MAP3D_LAYOUT_W = 1.0f
private const val MAP3D_LAYOUT_H = 1.10f

private fun zoneCardRect(zone: CampusZone, w: Float, h: Float): Rect {
    val s = minOf(w / MAP3D_LAYOUT_W, h / MAP3D_LAYOUT_H)
    val ox = (w - MAP3D_LAYOUT_W * s) / 2f
    val oy = (h - MAP3D_LAYOUT_H * s) / 2f
    val cwU = when (zone) {
        CampusZone.NORTH -> 0.42f
        CampusZone.SOUTH -> 0.46f
        CampusZone.EAST -> 0.42f
    }
    val chU = cwU / zoneAspect(zone)
    val leftU = when (zone) {
        CampusZone.NORTH -> 0.05f
        CampusZone.SOUTH -> 0.02f
        CampusZone.EAST -> 0.54f
    }
    val topU = when (zone) {
        CampusZone.NORTH -> 0.02f
        CampusZone.SOUTH -> 0.66f
        CampusZone.EAST -> 0.24f
    }
    return Rect(
        ox + leftU * s,
        oy + topU * s,
        ox + (leftU + cwU) * s,
        oy + (topU + chU) * s,
    )
}

/** 米制投影像素（只用于求相对方位） */
private fun projectToPlane(lat: Double, lon: Double): Offset = Offset(
    ((lon - MAP3D_ORIGIN_LON) * MAP3D_EAST_M_PER_DEG * MAP3D_PX_PER_EAST_M).toFloat(),
    (-(lat - MAP3D_ORIGIN_LAT) * MAP3D_NORTH_M_PER_DEG * MAP3D_PX_PER_NORTH_M).toFloat(),
)

/** 同校区 4 个地标的归一化位置 (u,v) ∈ [0,1] */
private fun zonePinUnits(pins: List<Landmark>): Map<String, Offset> {
    if (pins.isEmpty()) return emptyMap()
    val pts = pins.associate { it.id to projectToPlane(it.point.latitude, it.point.longitude) }
    val minX = pts.values.minOf { it.x }
    val maxX = pts.values.maxOf { it.x }
    val minY = pts.values.minOf { it.y }
    val maxY = pts.values.maxOf { it.y }
    val spanX = (maxX - minX).takeIf { it > 1f } ?: 1f
    val spanY = (maxY - minY).takeIf { it > 1f } ?: 1f
    return pts.mapValues { (_, p) -> Offset((p.x - minX) / spanX, (p.y - minY) / spanY) }
}

/** 归一化位置 → 卡片内落点（内框留边，避免钉子贴边） */
private fun pinCenter(rect: Rect, unit: Offset): Offset = Offset(
    rect.left + rect.width * (0.14f + 0.72f * unit.x),
    rect.top + rect.height * (0.18f + 0.64f * unit.y),
)

private fun cloudRadius(rect: Rect): Float = rect.width * 0.085f

/** 软边云团：白心 → 透明边的径向渐变，叠几层像积云 */
private fun DrawScope.cloudPuff(center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = alpha),
                0.62f to Color.White.copy(alpha = alpha * 0.94f),
                1f to Color.White.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

@Composable
private fun CampusCanvas(state: Overview3DUiState, onOpenLandmark: (String) -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    val resources = LocalContext.current.resources
    val images = remember {
        mapOf(
            CampusZone.NORTH to ImageBitmap.imageResource(resources, R.drawable.map3d_north),
            CampusZone.SOUTH to ImageBitmap.imageResource(resources, R.drawable.map3d_south),
            CampusZone.EAST to ImageBitmap.imageResource(resources, R.drawable.map3d_east),
        )
    }
    val scale = remember { Animatable(1f) }
    val offX = remember { Animatable(0f) }
    val offY = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(state.selectedZone, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        if (state.selectedZone == null) {
            launch { scale.animateTo(1f) }
            launch { offX.animateTo(0f) }
            launch { offY.animateTo(0f) }
        } else {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val rect = zoneCardRect(state.selectedZone, w, h)
            val targetScale = minOf(w * 0.94f / rect.width, h * 0.66f / rect.height)
                .coerceIn(1.2f, 3.0f)
            val center = rect.center
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
                        val rect = zoneCardRect(zone.zone, w, h)
                        val units = zonePinUnits(zone.pins.map { it.landmark })
                        val radius = cloudRadius(rect)
                        zone.pins.forEach { pin ->
                            if (!pin.opened) return@forEach
                            val unit = units[pin.landmark.id] ?: return@forEach
                            val center = pinCenter(rect, unit)
                            if ((q - center).getDistance() <= radius * 1.3f) {
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
            drawCampus(state, w, h, textMeasurer, images)
        }
    }
}

private fun DrawScope.drawCampus(
    state: Overview3DUiState,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer,
    images: Map<CampusZone, ImageBitmap>,
) {
    state.zones.forEach { zone ->
        val rect = zoneCardRect(zone.zone, w, h)
        val image = images[zone.zone] ?: return@forEach
        val corner = minOf(rect.width, rect.height) * 0.055f

        // 卡片投影（轻微下沉，营造浮起的 2.5D 感）
        drawRoundRect(
            color = Color(0x1F14201B),
            topLeft = Offset(rect.left, rect.top + rect.height * 0.012f),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(corner, corner),
        )
        // 圆角裁切后绘制插画底图
        clipPath(Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner, corner))) }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }

        // 校区章（左上白底胶囊）
        val chipPad = rect.width * 0.02f
        val chipLayout = textMeasurer.measure(
            text = AnnotatedString("${zone.label} · ${zone.done}/${zone.total}"),
            style = TextStyle(fontSize = 10.5.sp, color = Color(0xFF20302A), fontWeight = FontWeight.Bold),
        )
        val chipLeft = rect.left + chipPad
        val chipTop = rect.top + chipPad
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(chipLeft - chipPad * 0.8f, chipTop - chipPad * 0.45f),
            size = Size(chipLayout.size.width + chipPad * 1.6f, chipLayout.size.height + chipPad * 0.9f),
            cornerRadius = CornerRadius(chipLayout.size.height * 0.6f, chipLayout.size.height * 0.6f),
        )
        drawText(chipLayout, topLeft = Offset(chipLeft, chipTop))

        // 地标：未打卡 = 云团压住；已打卡 = 白圈 + 主色钉 + 名称
        val units = zonePinUnits(zone.pins.map { it.landmark })
        val base = cloudRadius(rect)
        zone.pins.forEach { pin ->
            val unit = units[pin.landmark.id] ?: return@forEach
            val center = pinCenter(rect, unit)
            if (pin.opened) {
                drawCircle(Color.White.copy(alpha = 0.92f), radius = base * 0.62f, center = center)
                drawCircle(Primary, radius = base * 0.34f, center = center)
                drawCircle(Color.White, radius = base * 0.12f, center = center)
                val label = textMeasurer.measure(
                    text = AnnotatedString(pin.landmark.name),
                    style = TextStyle(fontSize = 9.5.sp, color = Color(0xFF1E2A24), fontWeight = FontWeight.Bold),
                )
                val labelTop = center.y + base * 0.66f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.82f),
                    topLeft = Offset(center.x - label.size.width / 2f - 4f, labelTop - 1f),
                    size = Size(label.size.width + 8f, label.size.height.toFloat() + 2f),
                    cornerRadius = CornerRadius(5f, 5f),
                )
                drawText(label, topLeft = Offset(center.x - label.size.width / 2f, labelTop))
            } else {
                // 未打卡：软边云团（径向渐变白 → 透明，三层叠加出蓬松感）
                drawCircle(
                    color = CloudShadow.copy(alpha = 0.10f),
                    radius = base * 1.10f,
                    center = center + Offset(0f, base * 0.14f),
                )
                cloudPuff(center + Offset(-base * 0.34f, base * 0.10f), base * 0.86f, 0.95f)
                cloudPuff(center + Offset(base * 0.30f, -base * 0.04f), base * 0.96f, 0.95f)
                cloudPuff(center + Offset(0f, -base * 0.26f), base * 0.64f, 0.9f)
            }
        }
    }
}
