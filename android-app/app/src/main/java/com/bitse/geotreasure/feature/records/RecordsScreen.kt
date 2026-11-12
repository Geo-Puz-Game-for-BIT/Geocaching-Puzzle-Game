package com.bitse.geotreasure.feature.records

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.bitse.geotreasure.core.ui.components.LandmarkImage
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.ErrorRed
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.RewardGold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 片区进度行：一个 2×2 模块 = 一个校区片区 */
data class ZoneProgress(
    val zone: CampusZone,
    val label: String,
    val done: Int,
    val total: Int,
) {
    val complete: Boolean get() = done >= total
}

data class RecordsUiState(
    val completed: List<Landmark> = emptyList(),
    val zoneProgress: List<ZoneProgress> = emptyList(),
    /** 删除仅影响本页照片墙展示，不影响碎片与打卡记录（与原型文案一致） */
    val hiddenPhotoIds: Set<String> = emptySet(),
    val confirmDeleteId: String? = null,
) {
    val shards: Int get() = completed.size
    val doneMods: Int get() = zoneProgress.count { it.complete }
    val photos: List<Landmark> get() = completed.filter { it.id !in hiddenPhotoIds }
}

sealed interface RecordsAction {
    data class RequestDelete(val landmarkId: String) : RecordsAction
    data object ConfirmDelete : RecordsAction
    data object CancelDelete : RecordsAction
}

/** 片区标签：与原型 CN 映射一致（MP-A 南 / MP-B 北 / MP-C 东），顺序 南→北→东 */
internal val ZONE_LABELS: List<Pair<CampusZone, String>> = listOf(
    CampusZone.SOUTH to "南",
    CampusZone.NORTH to "北",
    CampusZone.EAST to "东",
)

internal fun zoneProgressOf(completedIds: Set<String>): List<ZoneProgress> =
    ZONE_LABELS.map { (zone, label) ->
        val inZone = LiangxiangTreasureCatalog.landmarks.filter { it.campusZone == zone }
        ZoneProgress(
            zone = zone,
            label = label,
            done = inZone.count { it.id in completedIds },
            total = inZone.size,
        )
    }

class RecordsViewModel(progressStore: ExplorationProgressStore) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            progressStore.completedLandmarkIds.collect { ids ->
                val ordered = LiangxiangTreasureCatalog.landmarks.filter { it.id in ids }
                mutableUiState.update {
                    it.copy(completed = ordered, zoneProgress = zoneProgressOf(ids))
                }
            }
        }
    }

    fun onAction(action: RecordsAction) {
        when (action) {
            is RecordsAction.RequestDelete -> mutableUiState.update {
                it.copy(confirmDeleteId = action.landmarkId)
            }

            RecordsAction.ConfirmDelete -> mutableUiState.update {
                val target = it.confirmDeleteId ?: return@update it
                it.copy(hiddenPhotoIds = it.hiddenPhotoIds + target, confirmDeleteId = null)
            }

            RecordsAction.CancelDelete -> mutableUiState.update {
                it.copy(confirmDeleteId = null)
            }
        }
    }

    companion object {
        fun factory(progressStore: ExplorationProgressStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RecordsViewModel(progressStore) as T
            }
    }
}

/**
 * P06 我的记录（原型 p06 对齐）：
 * 账号卡 / 三格统计 / 片区进度 / 打卡照片墙（3 列，私密角标 + 删除）/ 隐私提示 / 删除确认弹窗。
 * 照片 = 已完成打卡的地标实拍小图（真实进度推导，不预置演示照片）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenPuzzle: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("我的记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "向川 · 已收 ${state.shards}/12 块碎片 · 照片 ${state.photos.size} 张",
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
                    IconButton(onClick = {
                        Toast.makeText(context, "打卡照片仅自己可见", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.Shield, contentDescription = "隐私说明")
                    }
                },
            )
        },
        bottomBar = {
            // P06 原型：底部三 Tab 常驻，「我的」为选中态
            AppTabBar(
                current = AppTab.Records,
                shards = state.shards,
                onSelect = { tab ->
                    when (tab) {
                        AppTab.Explore -> onOpenMap()
                        AppTab.Puzzle -> onOpenPuzzle()
                        AppTab.Records -> Unit
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
            item { AccountCard() }
            item {
                StatRow(
                    shards = state.shards,
                    doneMods = state.doneMods,
                    photoCount = state.photos.size,
                )
            }
            item { ZoneProgressCard(state.zoneProgress) }
            item {
                Column {
                    Text("我的打卡照片", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "仅自己可见 · 共 ${state.photos.size} 张",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                }
            }
            if (state.photos.isEmpty()) {
                item { EmptyPhotos(onExplore = onOpenMap) }
            } else {
                val rows = state.photos.chunked(3)
                items(rows.size) { rowIndex ->
                    PhotoRow(
                        photos = rows[rowIndex],
                        onRequestDelete = { viewModel.onAction(RecordsAction.RequestDelete(it)) },
                    )
                }
            }
            item { PrivacyNote() }
        }
    }

    state.confirmDeleteId?.let { targetId ->
        val target = state.completed.firstOrNull { it.id == targetId }
        DeleteConfirmDialog(
            photoName = target?.name ?: "",
            onConfirm = { viewModel.onAction(RecordsAction.ConfirmDelete) },
            onDismiss = { viewModel.onAction(RecordsAction.CancelDelete) },
        )
    }
}

@Composable
private fun AccountCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.cardLarge,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E9DE)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(Radius.button)
                    .background(Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("向", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("向川", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("学号 112023**** · 良乡校区", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            IconButton(onClick = { /* 账号设置（原型为 toast 示意） */ }) {
                Icon(Icons.Outlined.Info, contentDescription = "账号信息", tint = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatRow(shards: Int, doneMods: Int, photoCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatFact(Modifier.weight(1f), Icons.Outlined.Extension, "$shards / 12", "已收集碎片")
        StatFact(Modifier.weight(1f), Icons.Outlined.Map, "$doneMods / 3", "片区完成")
        StatFact(Modifier.weight(1f), Icons.Outlined.PhotoCamera, "$photoCount", "打卡照片")
    }
}

@Composable
private fun StatFact(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Surface(
        modifier = modifier,
        shape = Radius.card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E9DE)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = AppText.caption, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun ZoneProgressCard(zoneProgress: List<ZoneProgress>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E9DE)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            zoneProgress.forEach { zone ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        zone.label,
                        style = AppText.small,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(52.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(Radius.chip)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(zone.done.toFloat() / zone.total.coerceAtLeast(1))
                                .height(6.dp)
                                .clip(Radius.chip)
                                .background(if (zone.complete) RewardGold else Primary),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${zone.done}/${zone.total}",
                        style = AppText.caption,
                        color = OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(
    photos: List<Landmark>,
    onRequestDelete: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEach { landmark ->
            PhotoCell(
                landmark = landmark,
                onRequestDelete = { onRequestDelete(landmark.id) },
                modifier = Modifier.weight(1f),
            )
        }
        // 补位，保证末行不足 3 张时仍然左对齐
        repeat(3 - photos.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun PhotoCell(
    landmark: Landmark,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(Radius.card)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        LandmarkImage(
            landmarkId = landmark.id,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp),
            shape = Radius.chip,
            color = Color(0xFF0A1210).copy(alpha = 0.62f),
            contentColor = Color.White,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(11.dp))
                Text("私密", style = AppText.caption)
            }
        }
        Surface(
            onClick = onRequestDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(30.dp),
            shape = Radius.button,
            color = Color.White.copy(alpha = 0.94f),
            contentColor = ErrorRed,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除照片", modifier = Modifier.size(16.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.66f)),
                    ),
                )
                .padding(top = 14.dp, start = 7.dp, bottom = 5.dp),
        ) {
            Text(landmark.name, color = Color.White, style = AppText.caption, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyPhotos(onExplore: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.cardLarge,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E9DE)),
    ) {
        Column(
            modifier = Modifier.padding(26.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(Radius.cardLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("还没有打卡照片", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "完成一次打卡后，照片会出现在这里",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onExplore) {
                Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("去探索")
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.button,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                "打卡照片默认仅自己可见。删除照片不会影响已获得的碎片和打卡记录。",
                style = AppText.small,
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    photoName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这张照片？") },
        text = {
            Text("「$photoName」的打卡照片将被移出照片墙。\n删除照片不会影响已获得的碎片和打卡记录。\n删除后无法恢复。")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed,
                    contentColor = Color.White,
                ),
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
