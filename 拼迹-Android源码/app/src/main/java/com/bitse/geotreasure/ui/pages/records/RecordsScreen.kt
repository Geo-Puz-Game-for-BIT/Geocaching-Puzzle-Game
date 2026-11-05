package com.bitse.geotreasure.ui.pages.records

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.domain.model.CheckInPhoto
import com.bitse.geotreasure.ui.components.AppButton
import com.bitse.geotreasure.ui.components.AppButtonStyle
import com.bitse.geotreasure.ui.components.AppTab
import com.bitse.geotreasure.ui.components.AppTabBar
import com.bitse.geotreasure.ui.components.FactCell
import com.bitse.geotreasure.ui.components.PageHeader
import com.bitse.geotreasure.ui.components.PhotoTile
import com.bitse.geotreasure.ui.pages.puzzle.ProgressTrack
import com.bitse.geotreasure.ui.theme.AppText
import com.bitse.geotreasure.ui.theme.ErrorRed
import com.bitse.geotreasure.ui.theme.HairLine
import com.bitse.geotreasure.ui.theme.OnSurface
import com.bitse.geotreasure.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.ui.theme.OutlineVariant
import com.bitse.geotreasure.ui.theme.Primary
import com.bitse.geotreasure.ui.theme.PrimaryContainer
import com.bitse.geotreasure.ui.theme.Radius
import com.bitse.geotreasure.ui.theme.Surface as BrandSurface
import com.bitse.geotreasure.ui.theme.SurfaceVariant

/**
 * P06 · 我的记录。
 *
 * 三处数字（已收碎片 / 片区完成 / 照片张数）都从同一个 UserProgress 派生，
 * 所以换到 P01 或 P05 看到的数一定一致。
 */
@Composable
fun RecordsRoute(
    onBack: () -> Unit,
    onExplore: () -> Unit,
    onOpenPuzzle: () -> Unit,
    onOpenPending: () -> Unit,
    viewModel: RecordsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RecordsScreen(
        state = state,
        onBack = onBack,
        onExplore = onExplore,
        onOpenPuzzle = onOpenPuzzle,
        onOpenPending = onOpenPending,
        onDeletePhoto = viewModel::deletePhoto
    )
}

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onExplore: () -> Unit,
    onOpenPuzzle: () -> Unit,
    onOpenPending: () -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    // 待删除的照片：非空即弹出确认框（先确认再删，不做「点一下就没了」）
    var pendingDelete by remember { mutableStateOf<CheckInPhoto?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(com.bitse.geotreasure.ui.theme.Background).statusBarsPadding()) {
        PageHeader(
            title = "我的记录",
            subtitle = "向川 · 已收 ${state.shards}/${state.totalShards} 块碎片 · 照片 ${state.photos.size} 张",
            onBack = onBack,
            actionIcon = Icons.Outlined.Shield,
            actionDescription = "照片仅自己可见",
            onAction = { }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AccountCard()
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FactCell(
                    icon = Icons.Outlined.Extension,
                    value = "${state.shards} / ${state.totalShards}",
                    label = "已收集碎片",
                    modifier = Modifier.weight(1f)
                )
                FactCell(
                    icon = Icons.Outlined.Map,
                    value = "${state.completedModules} / 3",
                    label = "片区完成",
                    modifier = Modifier.weight(1f)
                )
                FactCell(
                    icon = Icons.Outlined.PhotoCamera,
                    value = "${state.photos.size}",
                    label = "打卡照片",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            ModuleProgressList(state)

            if (state.pendingCount > 0) {
                Spacer(Modifier.height(12.dp))
                PendingCard(count = state.pendingCount, onOpen = onOpenPending)
            }

            Spacer(Modifier.height(16.dp))
            Text("我的打卡照片", style = AppText.body.copy(fontWeight = FontWeight.Bold), color = OnSurface)
            Text(
                "仅自己可见 · 共 ${state.photos.size} 张",
                style = AppText.small,
                color = OnSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))
            if (state.photos.isEmpty()) {
                EmptyPhotos(onExplore = onExplore)
            } else {
                // 原型 .photos{grid-template-columns:repeat(3,1fr);gap:8px} —— 3 列等宽，4 张排成 3+1
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.photos.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { photo ->
                                PhotoTile(
                                    landmarkId = photo.landmarkId,
                                    label = photo.landmarkName,
                                    pending = photo.pending && state.pendingCount > 0,
                                    onDelete = { pendingDelete = photo },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryContainer, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color(0xFF123A2E), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "打卡照片默认仅自己可见。删除照片不会影响已获得的碎片和打卡记录。",
                    style = AppText.small,
                    color = Color(0xFF123A2E),
                    lineHeight = 19.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        AppTabBar(
            current = AppTab.Records,
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

    pendingDelete?.let { photo ->
        DeletePhotoDialog(
            onCancel = { pendingDelete = null },
            onConfirm = {
                onDeletePhoto(photo.id)
                pendingDelete = null
            }
        )
    }
}

@Composable
private fun AccountCard() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandSurface, Radius.cardLarge)
            .border(1.dp, HairLine, Radius.cardLarge)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 原型 .acct .av：52×52、圆角 16、实心主色底、白字 18px（不是浅色圆形头像）
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Primary, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("向", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("向川", style = AppText.body.copy(fontWeight = FontWeight.Bold), color = OnSurface)
            Text("学号 112023**** · 良乡校区", style = AppText.small, color = OnSurfaceVariant)
        }
        // 原型 .iconbtn：44×44、透明、圆角 12、图标 20
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    // 原型 data-act="toast:账号设置（原型示意）"
                    Toast.makeText(context, "账号设置（原型示意）", Toast.LENGTH_SHORT).show()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Info, contentDescription = "账号设置", tint = OnSurface, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ModuleProgressList(state: RecordsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandSurface, Radius.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        state.progress.modules.forEach { moduleProgress ->
            val name = state.moduleNames[moduleProgress.moduleId] ?: moduleProgress.moduleId
            val owned = moduleProgress.ownedSlots.size
            val all = owned >= 4
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = AppText.small,
                    color = OnSurface,
                    modifier = Modifier.width(58.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    ProgressTrack(fraction = owned / 4f, gold = all)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "$owned/4",
                    style = AppText.small.copy(fontWeight = FontWeight.Bold),
                    color = if (all) Color(0xFF7A5B12) else Primary
                )
            }
        }
    }
}

@Composable
private fun PendingCard(count: Int, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandSurface, Radius.card)
            .border(1.dp, HairLine, Radius.card)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(PrimaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, tint = Color(0xFF7A5B12), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("$count 条打卡还在确认", style = AppText.small.copy(fontWeight = FontWeight.Bold), color = OnSurface)
            Text(
                "照片已保留，可在原请求上恢复结果，不会重复提交。",
                style = AppText.caption,
                color = OnSurfaceVariant
            )
        }
        AppButton(
            text = "查看",
            onClick = onOpen,
            style = AppButtonStyle.Tonal,
            height = 34.dp,
            modifier = Modifier.width(64.dp)
        )
    }
}

@Composable
private fun EmptyPhotos(onExplore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandSurface, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE7E9DE), RoundedCornerShape(16.dp))
            .padding(vertical = 26.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp).background(SurfaceVariant, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("还没有打卡照片", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "完成一次打卡后，照片会出现在这里",
            style = AppText.small,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        AppButton(
            text = "去探索",
            onClick = onExplore,
            icon = Icons.Outlined.Map,
            modifier = Modifier.width(200.dp)
        )
    }
}

@Composable
private fun DeletePhotoDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BrandSurface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("删除这张照片？", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "删除照片不会影响已获得的碎片和打卡记录。\n删除后无法恢复。",
                    style = AppText.small,
                    color = OnSurfaceVariant,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        text = "取消",
                        onClick = onCancel,
                        style = AppButtonStyle.Outline,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = Radius.button,
                        color = ErrorRed,
                        contentColor = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("删除", style = AppText.body.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
