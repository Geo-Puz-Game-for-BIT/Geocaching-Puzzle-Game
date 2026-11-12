package com.bitse.geotreasure.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitse.geotreasure.R
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.CameraSurface
import com.bitse.geotreasure.core.ui.theme.Dimen
import com.bitse.geotreasure.core.ui.theme.HairLine
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnPrimaryContainer
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.PrimaryContainer
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface

/**
 * 首页品牌头图（P01 顶部）。
 * 整块用设计给的图片拉伸铺满（宽 412 → 高 91.7dp，比例 575:128，不裁切），
 * 圆形头像热区叠在图上原位（原图 (528,45.5)/575 → right 12 / top 10 / 44dp），
 * 所以“视觉在图上、点击在按钮上”，改图也不会丢跳转。
 */
@Composable
fun BrandHeader(onProfileClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(R.drawable.brand_header),
            contentDescription = "拼迹 · 发现校园的另一面",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(575f / 128f),
            contentScale = ContentScale.FillBounds
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 10.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onProfileClick
                )
        )
    }
}

/**
 * 二级页标题栏（P02–P06）。
 * title + 一行状态说明；返回键 44dp 触控；右侧可选动作。dark = 相机/3D 页深色版。
 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    dark: Boolean = false,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null
) {
    val background = if (dark) CameraSurface else BrandSurface
    val titleColor = if (dark) Color(0xFFEAF3EE) else OnSurface
    val subColor = if (dark) Color(0xFF9FB5AC) else OnSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimen.pageHeaderHeight)
            .background(background)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(Dimen.iconButton),
                shape = Radius.button,
                color = Color.Transparent,
                contentColor = titleColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = subColor,
                    style = AppText.tiny,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (actionIcon != null && onAction != null) {
            Surface(
                onClick = onAction,
                modifier = Modifier.size(40.dp),
                shape = Radius.button,
                color = if (dark) Color(0x29FFFFFF) else PrimaryContainer,
                contentColor = if (dark) Color.White else Primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        actionIcon,
                        contentDescription = actionDescription,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** 底部三 Tab。拼图 Tab 带「已收/12」角标，数字来自同一个进度源 */
enum class AppTab { Explore, Puzzle, Records }

@Composable
fun AppTabBar(
    current: AppTab,
    shards: Int,
    totalShards: Int = 12,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BrandSurface)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(HairLine))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(Dimen.tabBarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(
                tab = AppTab.Explore,
                label = "探索",
                icon = Icons.Outlined.Map,
                selected = current == AppTab.Explore,
                onClick = onSelect,
                modifier = Modifier.weight(1f)
            )
            TabItem(
                tab = AppTab.Puzzle,
                label = "拼图",
                icon = Icons.Outlined.Extension,
                selected = current == AppTab.Puzzle,
                onClick = onSelect,
                badge = "$shards/$totalShards",
                modifier = Modifier.weight(1f)
            )
            TabItem(
                tab = AppTab.Records,
                label = "我的",
                icon = Icons.Outlined.Person,
                selected = current == AppTab.Records,
                onClick = onSelect,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabItem(
    tab: AppTab,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val tint = if (selected) Primary else OnSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimen.tabBarHeight)
            .clickable { onClick(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 选中态：图标上方 16×3 的短条（原型 .tab.on::after）
        Box(
            Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(if (selected) Primary else Color.Transparent)
        )
        Spacer(Modifier.height(3.dp))
        Box {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            if (badge != null) {
                Text(
                    text = badge,
                    style = AppText.badge,
                    color = if (selected) Color.White else OnPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 14.dp, y = (-4).dp)
                        .background(
                            if (selected) Primary else PrimaryContainer,
                            Radius.chip
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = AppText.tab.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = tint
        )
    }
}
