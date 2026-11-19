package com.bitse.geotreasure.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitse.geotreasure.core.ui.assets.LandmarkImages
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.ErrorRed
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.RewardGold
import com.bitse.geotreasure.core.ui.theme.SurfaceVariant

/**
 * 地标照片的统一入口。
 *
 * object-fit 规则（与原型一致）：
 *  - 方形容器（缩略图 / 拼图格 / 记录照 / 相机预览）必须 Fit（contain）—— 3:2 的抠图用 Crop 会切掉建筑两侧；
 *  - 只有 P02 Hero（412×252 宽幅）用 Crop（cover）。
 */
@Composable
fun LandmarkImage(
    landmarkId: String,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    crop: Boolean = false
) {
    Image(
        painter = painterResource(if (big) LandmarkImages.big(landmarkId) else LandmarkImages.small(landmarkId)),
        contentDescription = null,
        modifier = modifier,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit
    )
}

/**
 * 2×2 拼图里的一格（也用于 P04 碎片归位）。
 * owned=false 时压深色遮罩 + 锁；isNew=true 时描金边并轻微放大，对应「刚归位」。
 */
@Composable
fun FragmentTile(
    landmarkId: String,
    label: String,
    owned: Boolean,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    aspect: Float = 1f,
    showLabel: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val newScale by animateFloatAsState(if (isNew) 1f else 0.97f, label = "tileScale")
    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(Radius.tile)
            .background(SurfaceVariant)
            .then(if (isNew) Modifier.border(2.dp, RewardGold, Radius.tile) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (owned) {
            LandmarkImage(
                landmarkId = landmarkId,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(if (isNew) newScale else 1f),
                big = false
            )
            if (showLabel) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to Color(0x94000000)
                            )
                        )
                )
                Text(
                    text = label,
                    color = Color.White,
                    style = AppText.tiny.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    maxLines = 1
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xB8232D2A))
            )
            Icon(
                Icons.Outlined.Lock,
                contentDescription = "未解锁",
                tint = Color(0xFFE6ECE9),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp)
            )
        }
    }
}

/** P06 打卡照片：图 + 左上隐私角标 + 右上删除 + 底部地名 */
@Composable
fun PhotoTile(
    landmarkId: String,
    label: String,
    pending: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
    ) {
        LandmarkImage(
            landmarkId = landmarkId,
            modifier = Modifier.fillMaxSize(),
            big = false
        )

        // 左上角标：确认中优先于私密。原型 .priv 是 flex 行、gap:3px、图标 16px
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .background(Color(0x9E0A1210), Radius.chip)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = if (pending) Icons.Outlined.HourglassEmpty else Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = if (pending) "确认中" else "私密",
                color = Color.White,
                style = AppText.caption.copy(fontSize = 9.5.sp, lineHeight = 13.sp)
            )
        }

        // 删除：36dp 触控（原型 .photo .del）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF0FFFFFF))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "删除这张照片",
                tint = ErrorRed,
                modifier = Modifier.size(16.dp)
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.6f to Color.Transparent,
                        1f to Color(0xA8000000)
                    )
                )
        )
        Text(
            text = label,
            color = Color.White,
            style = AppText.caption,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 7.dp, vertical = 5.dp),
            maxLines = 1
        )
    }
}

/** 已打卡对勾小徽标（列表里用） */
@Composable
fun DoneBadge(modifier: Modifier = Modifier) {
    Icon(
        Icons.Outlined.CheckCircle,
        contentDescription = null,
        tint = Primary,
        modifier = modifier.size(18.dp)
    )
}
