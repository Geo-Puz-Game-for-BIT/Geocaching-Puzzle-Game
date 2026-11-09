package com.bitse.geotreasure.feature.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.AppButtonStyle
import com.bitse.geotreasure.core.ui.components.AppTab
import com.bitse.geotreasure.core.ui.components.AppTabBar
import com.bitse.geotreasure.core.ui.components.FragmentTile
import com.bitse.geotreasure.core.ui.components.LandmarkImage
import com.bitse.geotreasure.core.ui.components.PageHeader
import com.bitse.geotreasure.core.ui.theme.OnPrimaryContainer
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.HairLine
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.PrimaryContainer
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.RewardGold
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import com.bitse.geotreasure.core.ui.theme.SurfaceVariant

/**
 * P05 · 拼图收集。
 *
 * 明暗必须按 ownedSlots（哪几格）来判定，不能写「前 N 格」——
 * 原型里就是这一处写错过，导致总览亮的是疏桐、片区页暗的是工业生态楼，两边正好相反。
 */
@Composable
fun PuzzleRoute(
    onBack: () -> Unit,
    onOpenModule: (moduleId: String) -> Unit,
    onOpenLandmark: (landmarkId: String) -> Unit,
    onExplore: () -> Unit,
    onOpenRecords: () -> Unit,
    viewModel: PuzzleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PuzzleOverviewScreen(
        state = state,
        onBack = onBack,
        onOpenModule = onOpenModule,
        onExplore = onExplore,
        onOpenRecords = onOpenRecords
    )
}

@Composable
fun PuzzleOverviewScreen(
    state: PuzzleOverviewUiState,
    onBack: () -> Unit,
    onOpenModule: (String) -> Unit,
    onExplore: () -> Unit,
    onOpenRecords: () -> Unit
) {
    val context = LocalContext.current
    val allDone = state.completedModules >= state.modules.size

    Column(modifier = Modifier.fillMaxSize().background(com.bitse.geotreasure.ui.theme.Background).statusBarsPadding()) {
        PageHeader(
            title = "拼图收集",
            subtitle = "已收 ${state.shards}/${state.totalShards} 块 · 完成 ${state.completedModules}/${state.modules.size} 个片区",
            onBack = onBack,
            // 原型 hdr(act:'toast:3 个片区 × 4 块 = 12 块碎片（原型示意）', actIc:'info')
            actionIcon = Icons.Outlined.Info,
            actionDescription = "拼图规则",
            onAction = {
                Toast.makeText(context, "3 个片区 × 4 块 = 12 块碎片（原型示意）", Toast.LENGTH_SHORT).show()
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("我的校园地图", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.completedModules} / ${state.modules.size} 个片区",
                    style = AppText.small,
                    color = OnSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (allDone) "三个片区都集齐了，完整校园地图已点亮"
                else "每个片区 2×2 四块碎片，各对应一个地标；集齐后自动归位",
                style = AppText.small,
                color = OnSurfaceVariant,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(8.dp))
            ProgressTrack(
                fraction = state.completedModules.toFloat() / state.modules.size,
                gold = allDone
            )

            Spacer(Modifier.height(12.dp))
            // 原型 .mapgrid：三个片区并排在一张灰绿底卡片里，不是三张竖排大卡
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFDDE5E1), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                state.modules.forEach { snapshot ->
                    ModuleBlock(
                        snapshot = snapshot,
                        onClick = { onOpenModule(snapshot.module.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Extension, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "已收 ${state.shards} / ${state.totalShards} 块碎片 · 点片区看它的 2×2",
                    style = AppText.caption,
                    color = OnSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryContainer, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = OnPrimaryContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "服务端确认打卡成功后，这块碎片自动点亮并归位，不需要手动拖动或拼合。集齐一个片区后可手动回顾拼合过程。",
                    style = AppText.small,
                    color = OnPrimaryContainer,
                    lineHeight = 19.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            if (allDone) {
                AchievementCard(title = "成就 · 校园地图完成", body = "12 块碎片全部归位，三个片区连成一张图")
            } else {
                NextUpCard(next = state.nextModule, onExplore = onExplore)
            }

            Spacer(Modifier.height(14.dp))
            // 原型这颗按钮只有文字、没有前置图标
            AppButton(
                text = if (allDone) "再逛逛地图" else "去地图继续打卡",
                onClick = onExplore,
                style = if (allDone) AppButtonStyle.Tonal else AppButtonStyle.Outline
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (allDone) "已解锁成就 · 全部 ${state.modules.size} / ${state.modules.size} 片区"
                else "碎片只对它所属的地标生效，不会过期",
                style = AppText.caption,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        AppTabBar(
            current = AppTab.Puzzle,
            shards = state.shards,
            totalShards = state.totalShards,
            onSelect = { tab ->
                when (tab) {
                    AppTab.Explore -> onExplore()
                    AppTab.Puzzle -> Unit
                    AppTab.Records -> onOpenRecords()
                }
            }
        )
    }
}

/* ---------------- 片区页 ---------------- */

@Composable
fun ModulePuzzleRoute(
    onBack: () -> Unit,
    onOpenLandmark: (landmarkId: String) -> Unit,
    onExplore: () -> Unit,
    onOpenRecords: () -> Unit,
    viewModel: ModulePuzzleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = state.snapshot ?: return

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(com.bitse.geotreasure.ui.theme.Background).statusBarsPadding()) {
        PageHeader(
            title = "片区拼图",
            subtitle = "${snapshot.module.name} · 已到 ${snapshot.ownedCount}/${snapshot.total} 块",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(snapshot.module.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Spacer(Modifier.weight(1f))
                Text("${snapshot.ownedCount} / ${snapshot.total}", style = AppText.small, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (snapshot.complete) "4 块都到位了，这个片区已经拼合"
                else "还差 ${snapshot.total - snapshot.ownedCount} 块：${snapshot.missingNames.joinToString("、")}",
                style = AppText.small,
                color = OnSurfaceVariant,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(8.dp))
            ProgressTrack(
                fraction = snapshot.ownedCount.toFloat() / snapshot.total,
                gold = snapshot.complete
            )

            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot.module.landmarks.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { landmark ->
                            val owned = landmark.slot in snapshot.ownedSlots
                            FragmentTile(
                                landmarkId = landmark.id,
                                label = landmark.name,
                                owned = owned,
                                onClick = if (owned) {
                                    { onOpenLandmark(landmark.id) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (snapshot.complete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryContainer, Radius.card)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = OnPrimaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${snapshot.module.landmarks.size} 个地标全部打卡\n" +
                            snapshot.module.landmarks.joinToString(" · ") { it.name },
                        style = AppText.small,
                        color = OnPrimaryContainer,
                        lineHeight = 19.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                AchievementCard(
                    title = "成就 · 拼合${snapshot.module.name}",
                    body = "第一个片区已点亮，地图进度 ${state.completedModules} / ${state.totalModules}"
                )
            } else {
                LandmarkCheckList(module = snapshot)
            }

            Spacer(Modifier.height(if (snapshot.complete) 10.dp else 14.dp))
            AppButton(
                text = if (snapshot.complete) "回地图总览看进度" else "去地图继续找碎片",
                onClick = onBack,
                style = if (snapshot.complete) AppButtonStyle.Tonal else AppButtonStyle.Outline,
                icon = Icons.Outlined.Map
            )
            // 原型 p05:complete 的次级入口：手动回顾只作回顾，不占主按钮
            if (snapshot.complete) {
                Spacer(Modifier.height(4.dp))
                AppButton(
                    text = "回顾拼合过程",
                    onClick = { Toast.makeText(context, "回顾拼合过程（演示，未接后端）", Toast.LENGTH_SHORT).show() },
                    style = AppButtonStyle.Text,
                    icon = Icons.Outlined.Extension
                )
            }
            Spacer(Modifier.height(if (snapshot.complete) 6.dp else 10.dp))
            Text(
                text = if (snapshot.complete) "北校区、东校区与南校区同构，可直接在地图切换查看"
                else "点已点亮的格子，可以查看它的地标和打卡记录",
                style = AppText.caption,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        AppTabBar(
            current = AppTab.Puzzle,
            shards = state.shards,
            totalShards = state.totalShards,
            onSelect = { tab ->
                when (tab) {
                    AppTab.Explore -> onExplore()
                    AppTab.Puzzle -> Unit
                    AppTab.Records -> onOpenRecords()
                }
            }
        )
    }
}
