package com.bitse.geotreasure.feature.landmark

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.bitse.geotreasure.core.ui.components.PageHeader
import com.bitse.geotreasure.core.ui.components.SkeletonBox
import com.bitse.geotreasure.core.ui.components.aboutOf
import com.bitse.geotreasure.core.ui.components.bottomNoteOf
import com.bitse.geotreasure.core.ui.components.taskDescOf
import com.bitse.geotreasure.core.ui.components.taskNoOf
import com.bitse.geotreasure.core.ui.components.taskTitleOf
import com.bitse.geotreasure.core.ui.components.terrainOf
import com.bitse.geotreasure.core.ui.components.zoneFullName
import com.bitse.geotreasure.core.ui.components.CHECK_IN_RULE_TEXT
import com.bitse.geotreasure.core.ui.components.LandmarkImage
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.HintBox
import com.bitse.geotreasure.core.ui.theme.OnPrimaryContainer
import com.bitse.geotreasure.core.ui.theme.OnRewardBox
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.PrimaryContainer
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.RewardBox
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import kotlin.math.roundToInt

/**
 * P02 地标详情 —— 视觉层严格按交付原型 .fig（P02 地标详情 · 默认）实现：
 * 头栏（地标详情 / 任务 T-00X · 和X合个影）→ 252dp 实拍 Hero（名字 22sp 白 + 「良乡校区 · 南校区 · 地标 No.00X」）
 * → 三格事实卡（距离/难度/地形）→ 关于这个地标 → 任务卡（编号 pill + 标题 + 描述 + 奖励盒 + 提示盒）
 * → 打卡规则 → 底部「距离约 X m · 提交后由服务端判定」+「去打卡」。
 *
 * 业务全部沿用基线 LandmarkDetailViewModel（定位门控 / attemptId / RefreshLocation / Retry /
 * StartTask / effects 通道一律不改，本文件不声明任何业务类型）。
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

    val landmark = uiState.landmark
    Scaffold(
        containerColor = Color(0xFFF8F7F2),
        topBar = {
            PageHeader(
                title = "地标详情",
                subtitle = landmark?.let { "${taskNoOf(it.pieceIndex)} · ${taskTitleOf(it.name)}" } ?: "加载中",
                onBack = onBack,
                actionIcon = Icons.Rounded.PhotoCamera,
                actionDescription = "任务拍照示意",
            )
        },
        bottomBar = {
            if (!uiState.isLoading && landmark != null) {
                DetailBottomCta(uiState = uiState, viewModel = viewModel)
            }
        },
    ) { padding ->
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().height(252.dp), corner = 0.dp)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(Modifier.fillMaxWidth().height(87.dp), corner = 10.dp)
            SkeletonBox(Modifier.fillMaxWidth().height(120.dp), corner = 10.dp)
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

/* ---------------- 正常内容（P02 原型结构） ---------------- */

@Composable
private fun LoadedBody(
    landmark: Landmark,
    gate: CheckInGateResult?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        // Hero：412×252 宽幅实拍（全页唯一 cover 位置），底部渐变上叠名字与归属行
        Box(
            Modifier
                .fillMaxWidth()
                .height(252.dp),
        ) {
            LandmarkImage(
                landmarkId = landmark.id,
                modifier = Modifier.fillMaxSize(),
                big = true,
                crop = true,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color(0x66000000),
                        ),
                    ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(
                    landmark.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "良乡校区 · ${zoneFullName(landmark.campusZone)} · 地标 No.00${landmark.pieceIndex + 1}",
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(14.dp))

            // 三格事实卡：距离 / 难度 / 地形（原型 121×87 #FFFEFA）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FactCellP02(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp)) },
                    value = gateDistanceText(gate),
                    label = "距离",
                )
                FactCellP02(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.Stars, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp)) },
                    value = if (landmark.difficulty >= 2) "★★☆" else "★☆☆",
                    label = "难度",
                )
                FactCellP02(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.Terrain, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp)) },
                    value = terrainOf(landmark.terrain),
                    label = "地形",
                )
            }

            // 关于这个地标
            Column {
                Text("关于这个地标", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Spacer(Modifier.height(8.dp))
                Text(aboutOf(landmark.campusZone, landmark.name), fontSize = 13.sp, lineHeight = 21.sp, color = OnSurfaceVariant)
            }

            // 任务卡（原型 #FFFEFA 圆角卡）
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(BrandSurface, RoundedCornerShape(10.dp))
                    .padding(13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        taskNoOf(landmark.pieceIndex),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnPrimaryContainer,
                        modifier = Modifier
                            .background(PrimaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        taskTitleOf(landmark.name),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text(taskDescOf(landmark.name), fontSize = 12.5.sp, lineHeight = 19.sp, color = OnSurfaceVariant)

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(RewardBox, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Stars,
                        contentDescription = null,
                        tint = OnRewardBox,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "奖励：${landmark.name}碎片 ×1（${zoneFullName(landmark.campusZone)}第 ${landmark.pieceIndex + 1}/4 块）",
                        fontSize = 12.sp,
                        color = OnRewardBox,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(HintBox, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "提示：把地标放在画面上半部分，人站在它前面，两者都完整入镜。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = OnSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 打卡规则
            Column {
                Text("打卡规则", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Spacer(Modifier.height(8.dp))
                Text(CHECK_IN_RULE_TEXT, fontSize = 13.sp, lineHeight = 21.sp, color = OnSurfaceVariant)
            }

            // 门控状态（六态：与 C 对齐，仅展示不判定）
            GateCard(gate = gate)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FactCellP02(
    icon: @Composable () -> Unit,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(BrandSurface, RoundedCornerShape(10.dp))
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(5.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Text(label, fontSize = 10.5.sp, color = OnSurfaceVariant)
    }
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

/* ---------------- 底部 CTA（原型：说明行 + 整宽「去打卡」） ---------------- */

@Composable
private fun DetailBottomCta(uiState: LandmarkDetailUiState, viewModel: LandmarkDetailViewModel) {
    val ready = uiState.canStartTask
    val distance = (uiState.gate as? CheckInGateResult.Ready)?.distanceMeters
        ?: (uiState.gate as? CheckInGateResult.Blocked)?.distanceMeters
    BottomCta {
        Text(
            text = bottomNoteOf(distance),
            style = AppText.tiny,
            color = OnSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            text = "去打卡",
            onClick = { viewModel.onAction(LandmarkDetailAction.StartTask) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        )
        // C 拍板（协作清单 Q2）：Blocked 态提供手动「重新定位」
        if (!ready && uiState.gate is CheckInGateResult.Blocked) {
            Spacer(Modifier.height(8.dp))
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


private fun gateDistanceText(gate: CheckInGateResult?): String = when (gate) {
    is CheckInGateResult.Ready -> "${gate.distanceMeters.roundToInt()} m"
    is CheckInGateResult.Blocked -> gate.distanceMeters?.let { "${it.roundToInt()} m" } ?: "—"
    else -> "—"
}
