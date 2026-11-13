package com.bitse.geotreasure.feature.checkin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.PageHeader
import com.bitse.geotreasure.core.ui.components.zoneFullName
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.FragmentCell
import com.bitse.geotreasure.core.ui.theme.GoldText
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.SuccessContainer
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class CheckInResultUiState(
    val checkInId: String,
    val landmarkId: String,
    val grantedFragmentTitle: String?,
) {
    val landmark: Landmark?
        get() = LiangxiangTreasureCatalog.landmarks.firstOrNull { it.id == landmarkId }
}

sealed interface CheckInResultAction { data object OpenPuzzle : CheckInResultAction }
sealed interface CheckInResultEffect { data class NavigateToPuzzle(val puzzleId: String) : CheckInResultEffect }

/**
 * 打卡结果页：仅展示服务端已确认的成功结果。
 * 碎片发放已由 CameraViewModel 在收到 CheckInReceipt 后调用 markLandmarkCompleted 完成，
 * 本页不再重复标记，避免「本地拍照成功即发奖」。
 */
class CheckInResultViewModel(
    checkInId: String,
    landmarkId: String,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        CheckInResultUiState(checkInId, landmarkId, LiangxiangTreasureCatalog.landmarks.firstOrNull { it.id == landmarkId }?.name),
    )
    val uiState: StateFlow<CheckInResultUiState> = mutableUiState.asStateFlow()
    private val effectChannel = Channel<CheckInResultEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onAction(action: CheckInResultAction) {
        if (action == CheckInResultAction.OpenPuzzle) {
            effectChannel.trySend(CheckInResultEffect.NavigateToPuzzle("pzl_campus_01"))
        }
    }

    companion object {
        fun factory(
            checkInId: String,
            landmarkId: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CheckInResultViewModel(checkInId, landmarkId) as T
        }
    }
}

/**
 * P04 打卡结果 · 成功 —— 视觉层严格按交付原型 .fig（P04 打卡结果 · 成功）实现：
 * 头栏（打卡结果 / X · 碎片已归位）→ 84dp 成功圆标（#D6EBDD）→ 「打卡成功」24sp
 * → 副题与确认文案 → 2×2 碎片格（#DCE5E1，本块高亮）→ 归位说明 → 奖励卡（#FFFEFA + 金色计数）
 * → 底部三键：查看拼图（主）/ 看 3D 地图开云 / 继续探索。
 */
@Composable
fun CheckInResultScreen(
    viewModel: CheckInResultViewModel,
    onOpenPuzzle: (String) -> Unit,
    onOpenMap: () -> Unit,
    onOpenOverview3D: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is CheckInResultEffect.NavigateToPuzzle) onOpenPuzzle(effect.puzzleId)
        }
    }

    val landmark = state.landmark
    Scaffold(
        containerColor = Color(0xFFF8F7F2),
        topBar = {
            PageHeader(
                title = "打卡结果",
                subtitle = landmark?.let { "${it.name} · 碎片已归位" } ?: "碎片已归位",
                onBack = onOpenMap,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(SuccessContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFF1F7A4C),
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("打卡成功", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            Spacer(Modifier.height(10.dp))
            Text(
                landmark?.let { "${it.name} · 同框打卡" } ?: "同框打卡",
                fontSize = 13.5.sp,
                color = OnSurfaceVariant,
            )
            Text(
                text = buildString {
                    landmark?.let {
                        append("服务端已确认：第 ${it.pieceIndex + 1} 块碎片已归位，")
                        append("${zoneFullName(it.campusZone)}点亮。")
                    } ?: append("服务端已确认打卡成功，碎片已归位。")
                },
                fontSize = 13.5.sp,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(26.dp))
            if (landmark != null) {
                FragmentGrid(landmark = landmark)
                Spacer(Modifier.height(14.dp))
                Text(
                    "${landmark.name}碎片自动归位 · ${zoneFullName(landmark.campusZone)} ${landmark.pieceIndex + 1}/4 点亮",
                    style = AppText.tiny,
                    color = OnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(22.dp))
            RewardCard(landmark = landmark)

            Spacer(Modifier.weight(1f))
            AppButton(
                text = "查看拼图",
                onClick = { viewModel.onAction(CheckInResultAction.OpenPuzzle) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            AppButton(
                text = "看 3D 地图开云",
                onClick = onOpenOverview3D,
                modifier = Modifier.fillMaxWidth(),
                style = com.bitse.geotreasure.core.ui.components.AppButtonStyle.Text,
            )
            AppButton(
                text = "继续探索",
                onClick = onOpenMap,
                modifier = Modifier.fillMaxWidth(),
                style = com.bitse.geotreasure.core.ui.components.AppButtonStyle.Text,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 2×2 碎片格：已归位 #DCE5E1 上叠地标图，本块高亮描边；未归位灰格 */
@Composable
private fun FragmentGrid(landmark: Landmark) {
    val zonePeers = LiangxiangTreasureCatalog.landmarks
        .filter { it.campusZone == landmark.campusZone }
        .sortedBy { it.pieceIndex }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        zonePeers.chunked(2).forEach { rowPeers ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPeers.forEach { peer ->
                    val isCurrent = peer.id == landmark.id
                    Box(
                        Modifier
                            .size(73.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(FragmentCell),
                    ) {
                        com.bitse.geotreasure.core.ui.components.LandmarkImage(
                            landmarkId = peer.id,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isCurrent) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x3300634D)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 奖励卡（原型 #FFFEFA 372×82）：金渐变圆 + 「X碎片 ×1」+ 片区位置 + 金色计数 */
@Composable
private fun RewardCard(landmark: Landmark?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BrandSurface, RoundedCornerShape(10.dp))
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFBF3DD), Color(0xFFF1E2BC)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Stars, contentDescription = null, tint = Color(0xFFB58B45), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    landmark?.let { "${it.name}碎片 ×1" } ?: "碎片 ×1",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                )
                Text(
                    landmark?.let { "${zoneFullName(it.campusZone)} · 第 ${it.pieceIndex + 1} / 4 块" } ?: "片区碎片",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                )
            }
            Text(
                "4/4",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GoldText,
            )
        }
    }
}
