package com.bitse.geotreasure.ui.pages.landmark

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.ui.components.AppButton
import com.bitse.geotreasure.ui.components.AppButtonStyle
import com.bitse.geotreasure.ui.components.BannerBar
import com.bitse.geotreasure.ui.components.BannerTone
import com.bitse.geotreasure.ui.components.BottomCta
import com.bitse.geotreasure.ui.components.FactCell
import com.bitse.geotreasure.ui.components.LandmarkImage
import com.bitse.geotreasure.ui.components.PageHeader
import com.bitse.geotreasure.ui.components.SectionTitle
import com.bitse.geotreasure.ui.components.SkeletonBox
import com.bitse.geotreasure.ui.theme.AppText
import com.bitse.geotreasure.ui.theme.Dimen
import com.bitse.geotreasure.ui.theme.ErrorContainer
import com.bitse.geotreasure.ui.theme.ErrorRed
import com.bitse.geotreasure.ui.theme.OnPrimaryContainer
import com.bitse.geotreasure.ui.theme.OnRewardBox
import com.bitse.geotreasure.ui.theme.OnSurface
import com.bitse.geotreasure.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.ui.theme.OutlineVariant
import com.bitse.geotreasure.ui.theme.Primary
import com.bitse.geotreasure.ui.theme.PrimaryContainer
import com.bitse.geotreasure.ui.theme.Radius
import com.bitse.geotreasure.ui.theme.RewardBox
import com.bitse.geotreasure.ui.theme.RewardGold
import com.bitse.geotreasure.ui.theme.Surface as BrandSurface

/**
 * P02 · 地标详情。
 *
 * Hero 是唯一允许 ContentScale.Crop 的地方（412×252 宽幅，cover 才不空边）；
 * 其余所有方形容器都必须 contain，否则 3:2 抠图的建筑两侧会被切掉。
 */
@Composable
fun LandmarkDetailRoute(
    onBack: () -> Unit,
    onStartCheckIn: (landmarkId: String) -> Unit,
    onOpenPuzzle: () -> Unit,
    viewModel: LandmarkDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LandmarkDetailScreen(
        state = state,
        onBack = onBack,
        onStartCheckIn = onStartCheckIn,
        onOpenPuzzle = onOpenPuzzle,
        onRetry = viewModel::retryLoad
    )
}

@Composable
fun LandmarkDetailScreen(
    state: LandmarkDetailUiState,
    onBack: () -> Unit,
    onStartCheckIn: (landmarkId: String) -> Unit,
    onOpenPuzzle: () -> Unit,
    onRetry: () -> Unit
) {
    val landmark = state.landmark
    val module = state.module
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(com.bitse.geotreasure.ui.theme.Background).statusBarsPadding()) {
        PageHeader(
            title = "地标详情",
            subtitle = if (landmark != null) "任务 ${state.taskNo} · 和${landmark.name}合个影" else "内容加载中",
            onBack = onBack,
            // 原型 actIc:'award'（奖章），不是书签
            actionIcon = Icons.Outlined.MilitaryTech,
            actionDescription = "收藏",
            onAction = { Toast.makeText(context, "已收藏（演示，未接后端）", Toast.LENGTH_SHORT).show() }
        )

        when {
            state.loading -> SkeletonBody()
            state.notFound -> NotFoundBody(onRetry = onRetry, onBack = onBack)
            landmark != null && module != null -> {
                Box(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Hero(landmarkId = landmark.id, name = landmark.name, moduleName = module.name, taskNo = state.taskNo)

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (!state.online) {
                                // 原型 netBar：同一句文案 + 重试，全站一致
                                BannerBar(
                                    text = "网络中断，暂时拿不到数据",
                                    tone = BannerTone.Error,
                                    icon = Icons.Outlined.CloudOff,
                                    actionLabel = "重试",
                                    onAction = onRetry,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FactCell(
                                    icon = Icons.Outlined.Navigation,
                                    value = landmark.distanceLabel,
                                    label = "距离",
                                    modifier = Modifier.weight(1f)
                                )
                                FactCell(
                                    icon = Icons.Outlined.Speed,
                                    value = landmark.difficulty,
                                    label = "难度",
                                    modifier = Modifier.weight(1f)
                                )
                                FactCell(
                                    icon = Icons.Outlined.Terrain,
                                    value = landmark.terrain,
                                    label = "地形",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            SectionTitle("关于这个地标")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${module.name}里容易辨认的一处：${landmark.name}。走到它跟前，把人和它一起拍进画面就算完成。",
                                style = AppText.body,
                                color = OnSurfaceVariant,
                                lineHeight = 21.sp
                            )

                            Spacer(Modifier.height(16.dp))
                            TaskCard(
                                name = landmark.name,
                                taskNo = state.taskNo,
                                hint = landmark.hint,
                                moduleName = module.name,
                                rewardIndex = state.rewardIndex
                            )

                            Spacer(Modifier.height(16.dp))
                            SectionTitle("打卡规则")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "到达地标附近 · 照片需现场拍摄 · 定位可用。界面不预设打卡半径，也不用人脸或身份识别；" +
                                    "是否通过由服务端在你提交后判定。",
                                style = AppText.small,
                                color = OnSurfaceVariant,
                                lineHeight = 19.sp
                            )
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }

                // 底部 CTA：已完成 / 离线 / 可打卡 三种口径分开写，避免文案与实际行为不符
                // 版式按原型 .bottomcta：说明条居中在上，主按钮整宽在下
                BottomCta(modifier = Modifier.navigationBarsPadding()) {
                    if (state.owned) {
                        Text(
                            text = "该地标已完成，不会重复发放碎片",
                            style = AppText.tiny,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        AppButton(
                            text = "查看拼图",
                            onClick = onOpenPuzzle,
                            style = AppButtonStyle.Tonal,
                            icon = Icons.Outlined.Extension,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = if (state.online) "距离约 ${landmark.distanceLabel} · 提交后由服务端判定"
                            else "网络恢复后才能提交",
                            style = AppText.tiny,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        AppButton(
                            text = "去打卡",
                            onClick = { onStartCheckIn(landmark.id) },
                            enabled = state.online,
                            icon = Icons.Outlined.PhotoCamera,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(landmarkId: String, name: String, moduleName: String, taskNo: String) {
    Box(modifier = Modifier.fillMaxWidth().height(Dimen.heroHeight)) {
        LandmarkImage(
            landmarkId = landmarkId,
            modifier = Modifier.fillMaxSize(),
            big = true,
            // 唯一的 Crop：宽幅 Hero，contain 会上下留白
            crop = true
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xD9000000)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = name,
                color = Color.White,
                // 原型 .hero .h-title h1 = 22px
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "良乡校区 · $moduleName · 地标 No.${taskNo.removePrefix("T-")}",
                color = Color(0xE0FFFFFF),
                style = AppText.tiny
            )
        }
    }
}

@Composable
private fun TaskCard(
    name: String,
    taskNo: String,
    hint: String,
    moduleName: String,
    rewardIndex: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandSurface, Radius.cardLarge)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "任务 $taskNo",
                style = AppText.caption.copy(fontWeight = FontWeight.Bold),
                color = Primary,
                modifier = Modifier
                    .background(PrimaryContainer, Radius.chip)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "和${name}合个影",
                style = AppText.body.copy(fontWeight = FontWeight.Bold),
                color = OnSurface
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "拍摄一张人物与${name}同框的照片并提交。不需要身份认证，也不做人脸识别。",
            style = AppText.small,
            color = OnSurfaceVariant,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 原型 .reward：暖金底 + 金图标，和绿色主色区分开
                .background(RewardBox, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Extension, contentDescription = null, tint = RewardGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "奖励：${name}碎片 ×1（${moduleName}第 $rewardIndex/4 块）",
                style = AppText.small,
                color = OnRewardBox
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF7FAF8), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "提示：$hint",
                style = AppText.small,
                color = OnSurfaceVariant,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun SkeletonBody() {
    Column(modifier = Modifier.fillMaxSize()) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(Dimen.heroHeight), corner = 0.dp)
        Column(modifier = Modifier.padding(16.dp)) {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(18.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    SkeletonBox(modifier = Modifier.weight(1f).height(64.dp), corner = 14.dp)
                }
            }
            Spacer(Modifier.height(14.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(120.dp), corner = 14.dp)
        }
    }
}

@Composable
private fun NotFoundBody(onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(ErrorContainer, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("这个地标没打开", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "这个地标的内容没加载出来，通常是网络问题。",
            style = AppText.small,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        AppButton(
            text = "重试一次",
            onClick = onRetry,
            icon = Icons.Outlined.Refresh,
            modifier = Modifier.width(220.dp)
        )
        Spacer(Modifier.height(6.dp))
        AppButton(
            text = "返回地图",
            onClick = onBack,
            style = AppButtonStyle.Text,
            modifier = Modifier.width(220.dp)
        )
    }
}
