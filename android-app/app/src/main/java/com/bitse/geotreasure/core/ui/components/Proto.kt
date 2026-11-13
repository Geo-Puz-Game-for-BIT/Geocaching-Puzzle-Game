package com.bitse.geotreasure.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface

/**
 * 原型对账辅助（.fig 导出规格）：
 *  - 难度星级：★☆☆(1) / ★★☆(2)，颜色 #202923
 *  - 地形：1 平地 / 2 林荫 / 3 台阶 / 4 湖畔（P05 三校区片区帧实测）
 *  - 校区全称：南校区 / 北校区 / 东校区（P01 分段控件文案）
 */
fun starsOf(difficulty: Int): String = when {
    difficulty >= 2 -> "★★☆"
    else -> "★☆☆"
}

fun terrainOf(terrain: Int): String = when (terrain) {
    2 -> "林荫"
    3 -> "台阶"
    4 -> "湖畔"
    else -> "平地"
}

fun zoneFullName(zone: CampusZone): String = when (zone) {
    CampusZone.SOUTH -> "南校区"
    CampusZone.NORTH -> "北校区"
    CampusZone.EAST -> "东校区"
}

fun zoneShort(zone: CampusZone): String = when (zone) {
    CampusZone.SOUTH -> "南"
    CampusZone.NORTH -> "北"
    CampusZone.EAST -> "东"
}

/** 任务编号：与原型 P02 一致「任务 T-00X」（X = 片区内序号） */
fun taskNoOf(pieceIndex: Int): String = "任务 T-00${pieceIndex + 1}"

/** P02 任务卡标题：与原型一致「和X合个影」 */
fun taskTitleOf(landmarkName: String): String = "和${landmarkName}合个影"

/** P02 任务描述：与原型一致 */
fun taskDescOf(landmarkName: String): String =
    "拍摄一张人物与${landmarkName}同框的照片并提交。不需要身份认证，也不做人脸识别。"

/** P02 「关于这个地标」正文：与原型一致 */
fun aboutOf(zone: CampusZone, landmarkName: String): String =
    "${zoneFullName(zone)}里容易辨认的一处：${landmarkName}。走到它跟前，把人和它一起拍进画面就算完成。"

/** P02 打卡规则正文：与原型一致 */
const val CHECK_IN_RULE_TEXT: String =
    "到达地标附近 · 照片需现场拍摄 · 定位可用。界面不预设打卡半径，也不用人脸或身份识别；是否通过由服务端在你提交后判定。"

/** P02 底部说明：与原型一致 */
fun bottomNoteOf(distanceMeters: Double?): String =
    if (distanceMeters != null) "距离约 ${distanceMeters.toInt()} m · 提交后由服务端判定"
    else "提交后由服务端判定"

/** P01 地图免责声明小条：与原型一致 */
const val MAP_DISCLAIMER: String = "地图为示意，不对应真实道路与坐标"

/** P01 未解锁推荐位副标题：与原型一致 */
const val REVEAL_HINT_TEXT: String = "走近后揭晓 · 拍一张人物与地标同框的照片"

/**
 * P01 校区分段控件：#FFFEFA 容器 380×50 内三段 40dp，
 * 选中段 #00634D 白字 13.5 Bold，未选 #5F6B64。
 */
@Composable
fun CampusSegmentedControl(
    selected: CampusZone,
    onSelect: (CampusZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zones = listOf(CampusZone.SOUTH, CampusZone.NORTH, CampusZone.EAST)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(BrandSurface, RoundedCornerShape(12.dp))
            .padding(5.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
    ) {
        zones.forEach { zone ->
            val active = zone == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        if (active) Primary else Color.Transparent,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onSelect(zone) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = zoneFullName(zone),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else Color(0xFF5F6B64),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 原型分隔点「·」（#B9C2B6 12sp） */
@Composable
fun ProtoDot(modifier: Modifier = Modifier) {
    Text(
        text = "·",
        fontSize = 12.sp,
        color = Color(0xFFB9C2B6),
        modifier = modifier,
    )
}

/** 原型星级+地形行（P01 底卡 / P05 片区行共用） */
@Composable
fun ProtoMetaRow(
    difficulty: Int,
    terrain: Int,
    modifier: Modifier = Modifier,
    distanceText: String? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (distanceText != null) {
            Text(distanceText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D6962))
            ProtoDot(Modifier.padding(horizontal = 6.dp))
        }
        Text(starsOf(difficulty), fontSize = 12.sp, color = Color(0xFF5D6962))
        ProtoDot(Modifier.padding(horizontal = 6.dp))
        Text(terrainOf(terrain), fontSize = 12.sp, color = Color(0xFF5D6962))
    }
}
