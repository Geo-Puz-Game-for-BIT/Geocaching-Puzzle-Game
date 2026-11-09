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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.AppButtonStyle
import com.bitse.geotreasure.core.ui.components.LandmarkImage
import com.bitse.geotreasure.core.ui.theme.GoldText
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.HairLine
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.RewardGold
import com.bitse.geotreasure.core.ui.theme.RewardGoldSurface
import com.bitse.geotreasure.core.ui.theme.RewardGoldSurfaceEnd
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import com.bitse.geotreasure.core.ui.theme.SurfaceVariant

/** 细进度条：集齐时变金色（原型 .ptrack i.gold） */
@Composable
fun ProgressTrack(fraction: Float, gold: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color(0xFFDDE3D6))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(if (gold) RewardGold else Primary)
        )
    }
}

/** 总览里的一个片区：2×2 迷你格 + 名字 + x/4 + 细进度条（原型 .mod，宽由 .mapgrid 三等分给出） */
@Composable
fun ModuleBlock(snapshot: ModuleSnapshot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gold = snapshot.complete
    Column(modifier = modifier.clickable(onClick = onClick)) {
        // .sub2：2×2、gap 3、底 #CBD8D2、内边距 3、圆角 10
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFCBD8D2), RoundedCornerShape(10.dp))
                .padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            snapshot.module.landmarks.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    rowItems.forEach { landmark ->
                        MiniTile(
                            landmark = landmark,
                            owned = landmark.slot in snapshot.ownedSlots,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        // .tag：名字 + n/4 底下有一块浅绿底板。基础 .tag 规则带 background:var(--pc)=#E6EDE5 /
        // padding 3px 7px / radius 6px，.mod .tag 只覆盖了排布、没重置底色 —— 别漏这块板。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE6EDE5), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                snapshot.module.name,
                style = AppText.tiny.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp),
                color = OnSurface,
                maxLines = 1
            )
            Text(
                text = if (gold) "已合成" else "${snapshot.ownedCount} / ${snapshot.total}",
                style = AppText.tiny.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp),
                color = when {
                    gold -> GoldText
                    snapshot.ownedCount == 0 -> OnSurfaceVariant
                    else -> Primary
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        // .bar：4dp，底 #C9D6D1，集齐转金色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFC9D6D1))
        ) {
            Box(
                Modifier
                    .fillMaxWidth((snapshot.ownedCount.toFloat() / snapshot.total).coerceIn(0f, 1f))
                    .fillMaxSize()
                    .background(if (gold) RewardGold else Primary)
            )
        }
    }
}

@Composable
private fun MiniTile(landmark: Landmark, owned: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFFC3D0CA))
    ) {
        if (owned) {
            // 方形容器一律 contain：3:2 的抠图 cover 会把建筑两侧裁掉
            LandmarkImage(
                landmarkId = landmark.id,
                modifier = Modifier.fillMaxSize(),
                big = false
            )
        } else {
            // .veil：整格压暗 50% + 居中锁
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80232D2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Color(0xFFE2EAE6),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun AchievementCard(title: String, body: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.cardLarge)
            .background(
                Brush.linearGradient(listOf(RewardGoldSurface, RewardGoldSurfaceEnd))
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0x33B58B45), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.MilitaryTech, contentDescription = null, tint = GoldText, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = AppText.body.copy(fontWeight = FontWeight.Bold), color = Color(0xFF33260B))
            Spacer(Modifier.height(2.dp))
            Text(body, style = AppText.small, color = Color(0xFF5A4A20), lineHeight = 18.sp)
        }
    }
}

/** 「下一块碎片」引导卡 */
@Composable
fun NextUpCard(next: ModuleSnapshot, onExplore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(BrandSurface)
            .border(1.dp, HairLine, Radius.card)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(Color(0xFFE6EDE5), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Place, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("下一块碎片：？", style = AppText.small.copy(fontWeight = FontWeight.Bold), color = OnSurface)
            Text(
                "${next.module.name} · 还有 ${next.total - next.ownedCount} 块未收集，靠近后在地图上点亮",
                style = AppText.caption,
                color = OnSurfaceVariant
            )
        }
        AppButton(
            text = "去看看",
            onClick = onExplore,
            style = AppButtonStyle.Tonal,
            height = 36.dp,
            modifier = Modifier.width(84.dp)
        )
    }
}

/** 片区未集齐时的对账清单：哪个打了、哪个没打，一眼能看清 */
@Composable
fun LandmarkCheckList(module: ModuleSnapshot, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        module.module.landmarks.forEach { landmark ->
            val ok = landmark.slot in module.ownedSlots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Place,
                    contentDescription = null,
                    tint = if (ok) Primary else OnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        landmark.name,
                        style = AppText.small.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )
                    Text(
                        "${landmark.distanceLabel} · ${landmark.difficulty} · ${landmark.terrain}",
                        style = AppText.caption,
                        color = OnSurfaceVariant
                    )
                }
                Text(
                    text = if (ok) "已打卡" else "待打卡",
                    style = AppText.caption,
                    color = if (ok) Primary else OnSurfaceVariant
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(HairLine))
        }
    }
}
