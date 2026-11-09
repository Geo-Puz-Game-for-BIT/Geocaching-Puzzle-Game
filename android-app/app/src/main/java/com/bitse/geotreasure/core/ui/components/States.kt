package com.bitse.geotreasure.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.HairLine
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.SurfaceVariant
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface

/**
 * A-N03：空态 / 错误态公共组件。
 *
 * 规则：任何「没有数据」或「这次没成功」的页面状态，都用这两个组件渲染——
 * 结构统一（图标块 + 标题 + 说明 + 操作），且**必须有可操作的出口**（A-N06：
 * 失败提示不允许是死路，至少给一个「重试 / 去设置 / 去探索」按钮）。
 * 各页只传文案与动作，不再各画一套。
 */

/** 空态：没有数据时（P06 暂无照片、搜索无结果…）。action 为 null 表示纯说明。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    StatePanel(modifier, icon, title, description, actionLabel, onAction)
}

/** 错误态：这次没成功时（加载失败、提交失败…）。actionLabel 必须给——失败不允许是死路。 */
@Composable
fun ErrorState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    StatePanel(modifier, icon, title, description, actionLabel, onAction, secondaryLabel, onSecondary)
}

@Composable
private fun StatePanel(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BrandSurface, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, HairLine), RoundedCornerShape(16.dp))
            .padding(vertical = 26.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp).background(SurfaceVariant, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            description,
            style = AppText.small,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            AppButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.width(200.dp)
            )
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            AppButton(
                text = secondaryLabel,
                onClick = onSecondary,
                style = AppButtonStyle.Text,
                modifier = Modifier.width(200.dp)
            )
        }
    }
}
