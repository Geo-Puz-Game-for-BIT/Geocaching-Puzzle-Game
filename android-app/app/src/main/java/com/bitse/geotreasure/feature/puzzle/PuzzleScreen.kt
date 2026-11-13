package com.bitse.geotreasure.feature.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.app.ExplorationProgressStore
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog
import com.bitse.geotreasure.core.model.PuzzleRevealMode
import com.bitse.geotreasure.core.ui.components.AppTab
import com.bitse.geotreasure.core.ui.components.AppTabBar
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.AppButtonStyle
import com.bitse.geotreasure.core.ui.components.LandmarkImage
import com.bitse.geotreasure.core.ui.components.PageHeader
import com.bitse.geotreasure.core.ui.components.zoneFullName
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.OnPrimaryContainer
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.PrimaryContainer
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PuzzleUiState(
    val puzzleId: String,
    val revealMode: PuzzleRevealMode = LiangxiangTreasureCatalog.puzzle.defaultRevealMode,
    val ownedFragmentIds: Set<String> = emptySet(),
)

/**
 * 真实拼图进度：只由「已完成地标」推导碎片，不预置任何默认演示碎片。
 * 未打卡 → 不点亮；服务端确认成功后经 markLandmarkCompleted 写入才点亮；
 * 失败/UNKNOWN 不提前点亮；重复打卡由集合幂等去重；进度经 DataStore 持久化，重启保留。
 */
internal fun ownedFragmentIdsFor(completedLandmarkIds: Set<String>): Set<String> =
    LiangxiangTreasureCatalog.landmarks
        .filter { it.id in completedLandmarkIds }
        .map { it.fragmentId }
        .toSet()

sealed interface PuzzleAction {
    data class ChangeRevealMode(val revealMode: PuzzleRevealMode) : PuzzleAction
    data class SelectFragment(val fragmentId: String) : PuzzleAction
}

sealed interface PuzzleEffect

class PuzzleViewModel(puzzleId: String, progressStore: ExplorationProgressStore) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PuzzleUiState(puzzleId))
    val uiState: StateFlow<PuzzleUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            progressStore.completedLandmarkIds.collect { completedIds ->
                mutableUiState.update { it.copy(ownedFragmentIds = ownedFragmentIdsFor(completedIds)) }
            }
        }
    }

    fun onAction(action: PuzzleAction) {
        when (action) {
            is PuzzleAction.ChangeRevealMode -> mutableUiState.update {
                it.copy(revealMode = action.revealMode)
            }

            is PuzzleAction.SelectFragment -> {
                if (action.fragmentId !in mutableUiState.value.ownedFragmentIds) return
            }
        }
    }

    companion object {
        fun factory(puzzleId: String, progressStore: ExplorationProgressStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PuzzleViewModel(puzzleId, progressStore) as T
        }
    }
}

/**
 * P05 拼图收集 —— 视觉层严格按交付原型 .fig（P05 拼图收集 · 总览·打卡前 / 片区）实现：
 * 头栏（拼图收集 / 已收 X/12 块 · 完成 Y/3 个片区）→ 「我的校园地图」+「N / 3 个片区」
 * → 总进度条 → 三张校区卡（2×2 碎片格 + 「南校区 3 / 4」）→ 归位说明
 * → 「下一块碎片」卡（+去看看）→ 「去地图继续打卡」→ 页脚说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val puzzle = LiangxiangTreasureCatalog.puzzle
    val owned = state.ownedFragmentIds
    val doneMods = puzzle.sections.count { sec -> sec.fragments.all { it.id in owned } }
    Scaffold(
        containerColor = Color(0xFFF8F7F2),
        topBar = {
            PageHeader(
                title = "拼图收集",
                subtitle = "已收 ${owned.size}/${puzzle.fragmentCount} 块 · 完成 $doneMods/3 个片区",
            )
        },
        bottomBar = {
            // P05 原型：底部三 Tab 常驻，「拼图」为选中态
            AppTabBar(
                current = AppTab.Puzzle,
                shards = owned.size,
                onSelect = { tab ->
                    when (tab) {
                        AppTab.Explore -> onOpenMap()
                        AppTab.Puzzle -> Unit
                        AppTab.Records -> onOpenRecords()
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "我的校园地图",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text("$doneMods / 3 个片区", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
                }
            }
            item {
                Text(
                    "每个片区 2×2 四块碎片，各对应一个地标；集齐后自动归位",
                    fontSize = 12.5.sp,
                    color = OnSurfaceVariant,
                )
            }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PrimaryContainer),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(owned.size.toFloat() / puzzle.fragmentCount.coerceAtLeast(1))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Primary),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    puzzle.sections.forEach { section ->
                        CampusCard(
                            sectionName = sectionDisplayName(section.campusZone),
                            landmarks = section.fragments.mapNotNull { f ->
                                LiangxiangTreasureCatalog.landmarks.firstOrNull { it.fragmentId == f.id }
                            },
                            owned = owned,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                Text(
                    "已收 ${owned.size} / ${puzzle.fragmentCount} 块碎片 · 点片区看它的 2×2",
                    style = AppText.tiny,
                    color = OnSurfaceVariant,
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(PrimaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "服务端确认打卡成功后，这块碎片自动点亮并归位，不需要手动拖动或拼合。集齐一个片区后可手动回顾拼合过程。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = OnPrimaryContainer,
                    )
                }
            }
            item {
                NextShardCard(
                    nextLandmark = nextPendingLandmark(owned),
                    ownedCountIds = owned,
                    onGoMap = onOpenMap,
                )
            }
            item {
                AppButton(
                    text = "去地图继续打卡",
                    onClick = onOpenMap,
                    modifier = Modifier.fillMaxWidth(),
                    style = AppButtonStyle.Tonal,
                )
            }
            item {
                Text(
                    "碎片只对它所属的地标生效，不会过期",
                    style = AppText.tiny,
                    color = OnSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private fun sectionDisplayName(zone: CampusZone): String = zoneFullName(zone)

private fun nextPendingLandmark(owned: Set<String>): Landmark? =
    LiangxiangTreasureCatalog.landmarks.firstOrNull { it.fragmentId !in owned }

/** 校区卡：2×2 碎片格（#CBD8D2 待收 / 实拍图已收）+ 底部「南校区 3 / 4」 */
@Composable
private fun CampusCard(
    sectionName: String,
    landmarks: List<Landmark>,
    owned: Set<String>,
    modifier: Modifier = Modifier,
) {
    val doneCount = landmarks.count { it.fragmentId in owned }
    Column(
        modifier
            .background(Color(0xFFDDE5E1), RoundedCornerShape(10.dp))
            .padding(4.dp),
    ) {
        landmarks.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { lm ->
                    val has = lm.fragmentId in owned
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFC3D0CA)),
                    ) {
                        if (has) {
                            LandmarkImage(landmarkId = lm.id, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(
                                Modifier.align(Alignment.Center).size(18.dp),
                            ) {
                                Text("?", color = Color(0xFF8FA184), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFE6EDE5), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
        ) {
            Column {
                Text(sectionName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text("$doneCount / 4", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant)
            }
        }
    }
}

/** 「下一块碎片」卡（原型 #FFFEFA 380×93） */
@Composable
private fun NextShardCard(
    nextLandmark: Landmark?,
    ownedCountIds: Set<String>,
    onGoMap: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BrandSurface, RoundedCornerShape(10.dp))
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Primary)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("下一块碎片：？", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Spacer(Modifier.height(3.dp))
                Text(
                    nextLandmark?.let {
                        val zonePending = LiangxiangTreasureCatalog.landmarks
                            .count { z -> z.campusZone == it.campusZone && z.fragmentId !in ownedCountIds }
                        "${zoneFullName(it.campusZone)} · 还有 $zonePending 块未收集，靠近后在地图上点亮"
                    } ?: "12 块碎片已全部收集，去 3D 地图回看点亮情况",
                    style = AppText.tiny,
                    color = OnSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .background(PrimaryContainer, RoundedCornerShape(8.dp))
                    .clickable { onGoMap() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("去看看", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnPrimaryContainer)
            }
        }
    }
}
