package com.bitse.geotreasure.feature.landmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.core.checkin.BlockReason
import com.bitse.geotreasure.core.checkin.CheckInGateResult
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.AppButtonStyle
import com.bitse.geotreasure.core.ui.components.BannerBar
import com.bitse.geotreasure.core.ui.components.BannerTone
import com.bitse.geotreasure.core.ui.components.BottomCta
import com.bitse.geotreasure.core.ui.components.InfoNote
import com.bitse.geotreasure.core.ui.components.LandmarkImage
import com.bitse.geotreasure.core.ui.components.SkeletonBox
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.HairLine
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import kotlin.math.roundToInt

/**
 * P02 地标详情 —— 仅渲染层。
 *
 * 业务全部沿用基线 LandmarkDetailViewModel（定位门控 / attemptId / RefreshLocation / Retry /
 * StartTask / effects 通道一律不改，本文件不声明任何业务类型）。
 * 状态→视觉映射（与 C 对齐的六态）：
 *  1. isLoading                 → 骨架屏
 *  2. landmark==null && 错误    → 错误条 + 重试（Retry）
 *  3. gate==null / NO_LOCATION  → 「正在定位…」，CTA 禁用
 *  4. LOCATION_STALE            → 警示条 + 重新定位（RefreshLocation）
 *  5. ACCURACY_TOO_LOW          → 警示条（≤30m 提示）+ 重新定位
 *  6. OUT_OF_RANGE              → 警示条 + 实时距离 + 重新定位
 *  7. Ready                     → 绿色确认 + 「开始打卡」（StartTask → effect → onOpenCamera）
 * 注：基线 UiState 无「已完成」字段，完成态由列表/拼图页 DoneBadge 承担（与 C 确认项，
 *     不得为画稿在 VM 加字段）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandmarkDetailScreen(
    viewModel: LandmarkDetailViewModel,
    onBack: () -> Unit,
    onOpenCamera: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LandmarkDetailEffect.NavigateToCamera ->
                    onOpenCamera(effect.landmarkId, effect.attemptId)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(uiState.landmark?.name ?: "地标详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading && uiState.landmark != null) {
                DetailBottomCta(uiState = uiState, viewModel = viewModel)
            }
        },
    ) { padding ->
        val landmark = uiState.landmark
        when {
            uiState.isLoading -> SkeletonBody(Modifier.padding(padding))

            landmark == null -> ErrorBody(
                message = uiState.errorMessage ?: "加载失败",
                onRetry = { viewModel.onAction(LandmarkDetailAction.Retry) },
                modifier = Modifier.padding(padding),
            )

            else -> LoadedBody(
                landmark = landmark,
                gate = uiState.gate,
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

/* ---------------- 加载骨架 ---------------- */

@Composable
private fun SkeletonBody(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().height(252.dp), corner = 18.dp)
        SkeletonBox(Modifier.fillMaxWidth().height(28.dp), corner = 6.dp)
        SkeletonBox(Modifier.fillMaxWidth(0.7f).height(20.dp), corner = 6.dp)
        SkeletonBox(Modifier.fillMaxWidth().height(96.dp), corner = 14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.weight(1f).height(64.dp), corner = 8.dp)
            SkeletonBox(Modifier.weight(1f).height(64.dp), corner = 8.dp)
        }
    }
}

/* ---------------- 加载失败 ---------------- */

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(44.dp))
        Text(message, style = AppText.body, color = OnSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        AppButton(text = "重试", onClick = onRetry, style = AppButtonStyle.Primary)
    }
}

/* ---------------- 正常内容 ---------------- */

@Composable
private fun LoadedBody(
    landmark: Landmark,
    gate: CheckInGateResult?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero：412×252 宽幅，唯一允许 cover 的位置
        LandmarkImage(
            landmarkId = landmark.id,
            modifier = Modifier
                .fillMaxWidth()
                .height(252.dp)
                .clip(RoundedCornerShape(Radius.cardLarge.topStart)),
            big = true,
            crop = true,
        )

        Text(landmark.name, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Text(
            "${zoneLabel(landmark.campusZone)} · 拼图碎片 №${landmark.pieceIndex + 1}",
            style = AppText.small,
            color = OnSurfaceVariant,
        )

        // 硬性事实：难度 / 地形 / 打卡范围
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineFact("难度 ${landmark.difficulty}")
            OutlineFact("地形 ${landmark.terrain}")
            OutlineFact(areaLabel(landmark))
        }

        landmark.hint?.takeIf { it.isNotBlank() }?.let { hint ->
            InfoNote(text = "提示：$hint", icon = Icons.Rounded.Info)
        }

        GateCard(gate = gate)
        Text(
            "提交后由服务端判定是否打卡成功，手机端不做最终判定",
            style = AppText.caption,
            color = OnSurfaceVariant,
        )
    }
}

@Composable
private fun OutlineFact(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(BrandSurface, RoundedCornerShape(Radius.chip.topStart))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = AppText.small,
        color = OnSurfaceVariant,
    )
}

/* ---------------- 门控状态卡（六态核心） ---------------- */

@Composable
private fun GateCard(gate: CheckInGateResult?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (gate) {
            null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                Text("正在定位…", style = AppText.body, color = OnSurfaceVariant)
            }

            is CheckInGateResult.Ready -> BannerBar(
                text = "已进入打卡范围 · 距离约 ${formatMeters(gate.distanceMeters)}",
                tone = BannerTone.Info,
                icon = Icons.Rounded.LocationOn,
            )

            is CheckInGateResult.Blocked -> when (gate.reason) {
                BlockReason.NO_LOCATION -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                    Text(gate.reason.message, style = AppText.body, color = OnSurfaceVariant)
                }

                BlockReason.LOCATION_STALE -> BannerBar(
                    text = gate.reason.message,
                    tone = BannerTone.Warn,
                    icon = Icons.Rounded.LocationSearching,
                )

                BlockReason.ACCURACY_TOO_LOW -> BannerBar(
                    text = gate.reason.message,
                    tone = BannerTone.Warn,
                    icon = Icons.Rounded.LocationSearching,
                )

                BlockReason.OUT_OF_RANGE -> BannerBar(
                    text = buildString {
                        append(gate.reason.message)
                        gate.distanceMeters?.let { append("（距离约 ${formatMeters(it)}）") }
                    },
                    tone = BannerTone.Warn,
                    icon = Icons.Rounded.LocationOn,
                )
            }
        }
    }
}

/* ---------------- 底部 CTA ---------------- */

@Composable
private fun DetailBottomCta(uiState: LandmarkDetailUiState, viewModel: LandmarkDetailViewModel) {
    val ready = uiState.canStartTask
    BottomCta {
        when {
            ready -> BannerBar(
                text = "定位满足打卡条件，可以开始拍摄",
                tone = BannerTone.Info,
                icon = Icons.Rounded.LocationOn,
            )

            else -> Text(
                text = uiState.gateMessage ?: "等待定位满足打卡条件…",
                style = AppText.caption,
                color = OnSurfaceVariant,
            )
        }
        AppButton(
            text = if (ready) "开始打卡" else "开始打卡（未就绪）",
            onClick = { viewModel.onAction(LandmarkDetailAction.StartTask) },
            enabled = ready,
            icon = Icons.Rounded.CameraAlt,
            modifier = Modifier.fillMaxWidth(),
        )
        // C 拍板（协作清单 Q2）：Blocked 态（定位过期/精度不足/范围外）提供手动「重新定位」
        if (!ready && uiState.gate is CheckInGateResult.Blocked) {
            AppButton(
                text = "重新定位",
                onClick = { viewModel.onAction(LandmarkDetailAction.RefreshLocation) },
                style = AppButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* ---------------- 小工具 ---------------- */

private fun formatMeters(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "${meters.roundToInt()} m"

private fun zoneLabel(zone: CampusZone): String = when (zone) {
    CampusZone.SOUTH -> "南区"
    CampusZone.NORTH -> "北区"
    CampusZone.EAST -> "东区"
}

private fun areaLabel(landmark: Landmark): String = when (val area = landmark.checkInArea) {
    is CircularCheckInArea -> "范围 ${area.radiusMeters.roundToInt()} m"
    else -> "范围：多边形区域"
}
