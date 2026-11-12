package com.bitse.geotreasure.feature.overview

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.bitse.geotreasure.core.ui.components.AppTab
import com.bitse.geotreasure.core.ui.components.AppTabBar
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.SurfaceVariant
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

// 3D 地图配色（取自原型 3D 地图色板，浅色系不压地图）
private val CampusGround = Color(0xFFDDE5E1)
private val CampusPlatform = Color(0xFFE7EDE8)
private val CampusPlatformSide = Color(0xFFB8C6BF)
private val CampusSlot = Color(0xFFCBD8D2)
private val CloudWhite = Color(0xFFF7FAF7)
private val CloudShadow = Color(0xFF232D2A)

/**
 * P07 3D 校园地图（原型 p07 对齐）：
 * 迷雾卡片（三校区 4 点计数）/ 全览-南-北-东 聚焦切换 / 2.5D 校园 Canvas（打卡后云散开）/ 点标记看任务。
 * 迷雾读 ExplorationProgressStore 同一份进度（两套地图共用一份数据）。
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("3D 校园地图", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "良乡 · 云层已散开 ${state.openedTotal}/12 片 · ${state.doneMods}/3 校区",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMap) {
                        Icon(Icons.Outlined.Map, contentDescription = "切换 2D 地图")
                    }
                },
            )
        },
        bottomBar = {
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
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FogCard(state)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(Radius.cardLarge)
                    .background(CampusGround),
            ) {
                CampusCanvas(state = state, onOpenLandmark = onOpenLandmark)
            }

            TipRow()
            ZoomChips(state, onSelect = { viewModel.onAction(Overview3DAction.SelectZone(it)) })
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun FogCard(state: Overview3DUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E9DE)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("迷雾", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "已散开 ",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
                Text(
                    "${state.openedTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
                Text(
                    "/12 片",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Surface(shape = Radius.chip, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Text(
                        "${state.doneMods}/3 校区",
                        style = AppTextSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                state.zones.forEach { zone ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        zone.pins.forEach { pin ->
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(Radius.pill)
                                    .background(if (pin.opened) Primary else SurfaceVariant),
                            )
                        }
                        Text("${zone.label} ${zone.done}", style = AppTextSmall, color = OnSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("每片云\n= 一个地标", style = AppTextCaption, color = OnSurfaceVariant)
            }
        }
    }
}

private val AppTextSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
private val AppTextCaption = TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp)

@Composable
private fun TipRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Layers, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
        Text("点标记看任务 · 打卡后云会散开", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun ZoomChips(state: Overview3DUiState, onSelect: (CampusZone?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZoomChip(label = "全览", selected = state.selectedZone == null, onClick = { onSelect(null) })
        state.zones.forEach { zone ->
            ZoomChip(label = zone.label, selected = state.selectedZone == zone.zone, onClick = { onSelect(zone.zone) })
        }
    }
}

@Composable
private fun ZoomChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = Radius.chip,
        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E9DE)),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
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
