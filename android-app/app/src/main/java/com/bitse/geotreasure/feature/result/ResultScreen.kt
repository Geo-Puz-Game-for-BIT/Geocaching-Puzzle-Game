package com.bitse.geotreasure.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.AppButtonStyle
import com.bitse.geotreasure.core.ui.components.FragmentTile
import com.bitse.geotreasure.core.ui.components.PageHeader
import com.bitse.geotreasure.core.ui.navigation.OutcomeKeys
import com.bitse.geotreasure.core.ui.theme.GoldText
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.ErrorContainer
import com.bitse.geotreasure.core.ui.theme.ErrorRed
import com.bitse.geotreasure.core.ui.theme.HairLine
import com.bitse.geotreasure.core.ui.theme.OnPrimaryContainer
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.PrimaryContainer
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.RewardGold
import com.bitse.geotreasure.core.ui.theme.Success
import com.bitse.geotreasure.core.ui.theme.SuccessContainer
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import com.bitse.geotreasure.core.ui.theme.SurfaceVariant
import com.bitse.geotreasure.core.ui.theme.WarningContainer

/**
 * P04 · 打卡结果。
 *
 * 全部文案取自原型 FAILS（四种失败原因各一套「为什么 / 该怎么改」），
 * 这一页只负责「说清楚发生了什么、下一步点哪里」，不重复判定。
 */
@Composable
fun ResultRoute(
    onBack: () -> Unit,
    onOpenPuzzle: (moduleId: String) -> Unit,
    onOpen3D: () -> Unit,
    onExplore: () -> Unit,
    onOpenRecords: () -> Unit,
    onRetryCheckIn: (landmarkId: String) -> Unit,
    onOpenDetail: (landmarkId: String) -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ResultScreen(
        state = state,
        onBack = onBack,
        onOpenPuzzle = { state.module?.let { onOpenPuzzle(it.id) } },
        onOpen3D = onOpen3D,
        onExplore = onExplore,
        onOpenRecords = onOpenRecords,
        onRetryCheckIn = { onRetryCheckIn(state.landmarkId) },
        onOpenDetail = { onOpenDetail(state.landmarkId) }
    )
}

@Composable
fun ResultScreen(
    state: ResultUiState,
    onBack: () -> Unit,
    onOpenPuzzle: () -> Unit,
    onOpen3D: () -> Unit,
    onExplore: () -> Unit,
    onOpenRecords: () -> Unit,
    onRetryCheckIn: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(com.bitse.geotreasure.ui.theme.Background).statusBarsPadding()) {
        PageHeader(
            title = "打卡结果",
            subtitle = "${state.landmarkName} · " + when {
                state.isSuccess -> "碎片已归位"
                state.isPending -> "服务端正在确认"
                state.isConfirm -> "结果待确认"
                else -> "本次未通过"
            },
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            when {
                state.isSuccess -> SuccessBody(state, onOpenPuzzle, onOpen3D, onExplore)
                state.isPending -> PendingBody(onOpenRecords, onExplore)
                state.isConfirm -> ConfirmBody(onExplore)
                else -> FailureBody(
                    outcomeKey = state.outcomeKey,
                    onOpenRecords = onOpenRecords,
                    onRetryCheckIn = onRetryCheckIn,
                    onOpenDetail = onOpenDetail
                )
            }
        }
    }
}

/* ---------------- 成功 ---------------- */

@Composable
private fun SuccessBody(
    state: ResultUiState,
    onOpenPuzzle: () -> Unit,
    onOpen3D: () -> Unit,
    onExplore: () -> Unit
) {
    ResultBadge(
        icon = Icons.Outlined.CheckCircle,
        tint = Success,
        background = SuccessContainer
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = "打卡成功",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurface,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    // 原型 .lead：13.5px、行高 1.6、max-width 280 居中
    Text(
        text = "${state.landmarkName} · 同框打卡\n${state.successLead}",
        fontSize = 13.5.sp,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 46.dp)
    )

    Spacer(Modifier.height(18.dp))
    MergeGrid(state)
    Spacer(Modifier.height(18.dp))
    RewardTileCard(
        name = state.landmarkName,
        moduleName = state.moduleName,
        ownedCount = state.moduleOwnedCount,
        total = state.module?.total ?: 4
    )

    // 原型把三颗按钮竖排在底部（.spacer 顶开），不并排
    Spacer(Modifier.height(24.dp))
    AppButton(text = "查看拼图", onClick = onOpenPuzzle, icon = Icons.Outlined.Extension)
    Spacer(Modifier.height(4.dp))
    AppButton(
        text = "看 3D 地图开云",
        onClick = onOpen3D,
        style = AppButtonStyle.Text,
        icon = Icons.Outlined.Cloud
    )
    Spacer(Modifier.height(2.dp))
    AppButton(
        text = "继续探索",
        onClick = onExplore,
        style = AppButtonStyle.Text
    )
    Spacer(Modifier.navigationBarsPadding())
}

/** 成功后 2×2 碎片归位：原型 .mgrid 是 152px 宽的小方阵居中，只有新到手的那一格描金边 */
@Composable
private fun MergeGrid(state: ResultUiState) {
    val module = state.module ?: return
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(152.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                module.landmarks.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { landmark ->
                            FragmentTile(
                                landmarkId = landmark.id,
                                label = landmark.name,
                                owned = landmark.slot in state.ownedSlots,
                                isNew = landmark.slot == state.newSlot,
                                aspect = 1f,
                                showLabel = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
    // 原型 .mnote 在 .mgrid 之外，占满宽 —— 别跟着 152dp 一起收窄，否则会折成两行
    Spacer(Modifier.height(9.dp))
    Text(
        text = "${state.landmarkName}碎片自动归位 · ${state.moduleName} ${state.moduleOwnedCount}/${module.total} " +
            (if (state.moduleOwnedCount >= module.total) "点亮" else "已拼合"),
        fontSize = 11.5.sp,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RewardTileCard(name: String, moduleName: String, ownedCount: Int, total: Int) {
    // 原型 .tilecard：金边、金色渐变方块、右侧 num 走 #7A5B12
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandSurface, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFEFE6CF), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFFF3E3BE), Color(0xFFB58B45))),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Extension, contentDescription = null, tint = Color(0xFF3B2A00), modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("$name 碎片 ×1", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            Text("$moduleName · 第 $ownedCount / $total 块", fontSize = 12.sp, color = OnSurfaceVariant)
        }
        Text(
            text = "$ownedCount/$total",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = GoldText
        )
    }
}

/* ---------------- 处理中 / 待确认 ---------------- */

@Composable
private fun PendingBody(onOpenRecords: () -> Unit, onExplore: () -> Unit) {
    ResultBadge(icon = Icons.Outlined.HourglassEmpty, tint = GoldText, background = WarningContainer)
    Spacer(Modifier.height(14.dp))
    Text(
        "服务端正在确认",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurface,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "照片已经上传，正在核对打卡条件。\n这一步还没有结果，请先不要重复提交。",
        fontSize = 13.5.sp,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 46.dp)
    )

    Spacer(Modifier.height(18.dp))
    StatusCard(
        icon = Icons.Outlined.Info,
        title = "处理中",
        body = "结果出来后这一页会自动更新。现在既没有成功，也没有失败。"
    )
    Spacer(Modifier.height(8.dp))
    StatusCard(
        icon = Icons.Outlined.Shield,
        title = "照片已经留着",
        body = "照片保留在「我的记录」里。恢复时用同一条打卡请求，不会重复提交，也不会重复发放碎片。"
    )

    Spacer(Modifier.height(20.dp))
    AppButton(text = "刷新状态", onClick = { /* 结果由服务端推送，这里不假装刷新成功 */ }, style = AppButtonStyle.Tonal)
    Spacer(Modifier.height(4.dp))
    AppButton(text = "去我的记录", onClick = onOpenRecords, style = AppButtonStyle.Text)
    AppButton(text = "先去探索别的", onClick = onExplore, style = AppButtonStyle.Text)
}

@Composable
private fun ConfirmBody(onExplore: () -> Unit) {
    ResultBadge(icon = Icons.AutoMirrored.Outlined.HelpOutline, tint = GoldText, background = WarningContainer)
    Spacer(Modifier.height(14.dp))
    Text(
        "结果待确认",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurface,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "这次打卡还需要一点时间才能确认。\n确认完成后会通知你，碎片不会漏发。",
        fontSize = 13.5.sp,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 46.dp)
    )

    Spacer(Modifier.height(18.dp))
    StatusCard(
        icon = Icons.Outlined.Shield,
        title = "不会重复发放",
        body = "同一个地标只记一次。现在不显示成功，是避免提前给结果。"
    )
    Spacer(Modifier.height(8.dp))
    StatusCard(
        icon = Icons.Outlined.Image,
        title = "可以回到这一条",
        body = "在「我的记录」里能找到这条待确认的打卡，用原来的请求恢复结果；删除照片不会撤销已获得的碎片。"
    )

    Spacer(Modifier.height(20.dp))
    AppButton(text = "刷新状态", onClick = { }, style = AppButtonStyle.Tonal)
    Spacer(Modifier.height(4.dp))
    AppButton(text = "先去探索别的", onClick = onExplore, style = AppButtonStyle.Text)
}

/* ---------------- 失败 ---------------- */

private data class FailCopy(
    val title: String,
    val lead: String,
    val headline: String,
    val body: String,
    val todo: String,
    val main: String,
    val sub: String
)

private val FAILS: Map<String, FailCopy> = mapOf(
    OutcomeKeys.FAIL_RADIUS to FailCopy(
        title = "还没到地标附近",
        lead = "你离目标地标还有一段距离，这次没有通过。",
        headline = "为什么没成功",
        body = "判定用的是提交那一刻的位置。距离地标还远时，这张照片不能算作这次打卡。",
        todo = "走到地标跟前，让人和它一起进画面，再重新拍一张。",
        main = "走近再拍",
        sub = "返回详情"
    ),
    OutcomeKeys.FAIL_ACCURACY to FailCopy(
        title = "定位还不够稳",
        lead = "这次拿到的位置偏差比较大，没法确认你在地标旁边。",
        headline = "为什么没成功",
        body = "楼群之间定位容易漂移。位置不准的时候，系统分不清你在楼里还是楼外。",
        todo = "走到开阔处等几秒，等定位稳定下来再提交。",
        main = "等定位稳定，重试",
        sub = "返回详情"
    ),
    OutcomeKeys.FAIL_PHOTO to FailCopy(
        title = "这张照片用不了",
        lead = "照片不是刚刚在现场拍的，不能作为这次打卡凭证。",
        headline = "为什么没成功",
        body = "打卡要求现场拍摄：手机里已有的旧图会被拦下。这一步检测照片是现场拍的、含清晰人脸，不比对身份。",
        todo = "重新拍一张，快门按下后直接提交就行。",
        main = "重新拍一张",
        sub = "返回详情"
    ),
    OutcomeKeys.FAIL_NETWORK to FailCopy(
        title = "没提交成功",
        lead = "这一次的提交没有成功，碎片还没有发放。",
        headline = "发生了什么",
        body = "上传过程中连接断了。打卡需要联网完成，恢复后请重新提交。",
        todo = "检查网络后重试，或者先返回地标详情。",
        main = "重试提交",
        sub = "返回详情"
    )
)

@Composable
private fun FailureBody(
    outcomeKey: String,
    onOpenRecords: () -> Unit,
    onRetryCheckIn: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val copy = FAILS[outcomeKey] ?: FAILS.getValue(OutcomeKeys.FAIL_NETWORK)

    ResultBadge(icon = Icons.Outlined.Cancel, tint = ErrorRed, background = ErrorContainer)
    Spacer(Modifier.height(14.dp))
    Text(
        copy.title,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurface,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        copy.lead,
        fontSize = 13.5.sp,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 46.dp)
    )

    Spacer(Modifier.height(16.dp))
    // 原型 .reason：白底 + 淡红描边 + 圆角 14；标题用红色；.todo 是纯文字行，不是填色块
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandSurface, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFF2DEDC), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(copy.headline, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
        Spacer(Modifier.height(6.dp))
        Text(copy.body, fontSize = 12.5.sp, color = OnSurfaceVariant, lineHeight = 20.sp)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = OnSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(copy.todo, fontSize = 12.5.sp, color = OnSurface, lineHeight = 19.sp)
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7FAF8), RoundedCornerShape(16.dp))
            .border(1.dp, HairLine, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenRecords)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(Color(0xFFE9EFEC), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = Color(0xFF404A46), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("这次没有发放碎片", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            Text("照片仍留在「我的记录」，点这里去管理", fontSize = 12.sp, color = OnSurfaceVariant)
        }
    }

    Spacer(Modifier.height(18.dp))
    AppButton(text = copy.main, onClick = onRetryCheckIn)
    Spacer(Modifier.height(4.dp))
    AppButton(text = copy.sub, onClick = onOpenDetail, style = AppButtonStyle.Text)
}

/* ---------------- 共用小件 ---------------- */

@Composable
private fun ResultBadge(icon: ImageVector, tint: Color, background: Color) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(84.dp).background(background, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun StatusCard(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandSurface, Radius.card)
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Column {
            Text(title, style = AppText.small.copy(fontWeight = FontWeight.Bold), color = OnSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, style = AppText.small, color = OnSurfaceVariant, lineHeight = 19.sp)
        }
    }
}
