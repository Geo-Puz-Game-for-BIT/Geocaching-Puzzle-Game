package com.bitse.geotreasure.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitse.geotreasure.core.ui.theme.AppText
import com.bitse.geotreasure.core.ui.theme.Dimen
import com.bitse.geotreasure.core.ui.theme.DisabledText
import com.bitse.geotreasure.core.ui.theme.ErrorContainer
import com.bitse.geotreasure.core.ui.theme.ErrorRed
import com.bitse.geotreasure.core.ui.theme.HairLine
import com.bitse.geotreasure.core.ui.theme.OnErrorContainer
import com.bitse.geotreasure.core.ui.theme.OnPrimary
import com.bitse.geotreasure.core.ui.theme.OnPrimaryContainer
import com.bitse.geotreasure.core.ui.theme.OnSurface
import com.bitse.geotreasure.core.ui.theme.OnSurfaceVariant
import com.bitse.geotreasure.core.ui.theme.OnWarningContainer
import com.bitse.geotreasure.core.ui.theme.OutlineVariant
import com.bitse.geotreasure.core.ui.theme.Primary
import com.bitse.geotreasure.core.ui.theme.PrimaryContainer
import com.bitse.geotreasure.core.ui.theme.Radius
import com.bitse.geotreasure.core.ui.theme.Surface as BrandSurface
import com.bitse.geotreasure.core.ui.theme.SurfaceVariant
import com.bitse.geotreasure.core.ui.theme.WarningContainer

/** 按钮四型，与原型 .btn.primary / .tonal / .text / .outline 对应 */
enum class AppButtonStyle { Primary, Tonal, Text, Outline }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = Dimen.buttonHeight
) {
    val background = when (style) {
        AppButtonStyle.Primary -> if (enabled) Primary else SurfaceVariant
        AppButtonStyle.Tonal -> PrimaryContainer
        AppButtonStyle.Text -> Color.Transparent
        AppButtonStyle.Outline -> Color.Transparent
    }
    val contentColor = when (style) {
        AppButtonStyle.Primary -> if (enabled) OnPrimary else DisabledText
        AppButtonStyle.Tonal -> OnPrimaryContainer
        AppButtonStyle.Text -> Primary
        AppButtonStyle.Outline -> OnSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = Radius.button,
        color = background,
        contentColor = contentColor,
        border = if (style == AppButtonStyle.Outline) BorderStroke(1.4.dp, OutlineVariant) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 提示条三色：warn（黄）/ error（红）/ info（浅绿），与原型 .banner 一致 */
enum class BannerTone { Warn, Error, Info }

@Composable
fun BannerBar(
    text: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val bg = when (tone) {
        BannerTone.Warn -> WarningContainer
        BannerTone.Error -> ErrorContainer
        BannerTone.Info -> PrimaryContainer
    }
    val fg = when (tone) {
        BannerTone.Warn -> OnWarningContainer
        BannerTone.Error -> OnErrorContainer
        BannerTone.Info -> OnPrimaryContainer
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = AppText.small,
            color = fg,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Surface(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                color = fg.copy(alpha = 0.08f),
                contentColor = fg
            ) {
                Text(
                    text = actionLabel,
                    style = AppText.small.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** 骨架屏：加载中不白屏，形状与原内容一致 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shift"
    )
    val base = Brush.linearGradient(
        colors = listOf(HairLine, Color(0xFFF2F6F4), HairLine),
        start = Offset(shift.value * 300f, 0f),
        end = Offset(shift.value * 300f + 320f, 0f)
    )
    Box(
        modifier
            .background(base, RoundedCornerShape(corner))
    )
}

/** 分区小标题（原型 .sec h4） */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = AppText.body.copy(fontWeight = FontWeight.Bold),
        color = OnSurface
    )
}

/** 表格化的事实格（原型 .fact）：图标 + 值 + 标签 */
@Composable
fun FactCell(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BrandSurface, Radius.card)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = AppText.body.copy(fontWeight = FontWeight.Bold),
            color = OnSurface,
            maxLines = 1
        )
        Text(text = label, style = AppText.caption, color = OnSurfaceVariant)
    }
}

/** 说明块（原型 .notebox / .hint）：浅底 + 图标 + 长文本 */
@Composable
fun InfoNote(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = PrimaryContainer,
    content: Color = OnPrimaryContainer
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(text = text, style = AppText.small, color = content, modifier = Modifier.weight(1f))
    }
}

/** 带描边的白卡片容器 */
@Composable
fun OutlineCard(
    modifier: Modifier = Modifier,
    borderColor: Color = HairLine,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.card,
        color = BrandSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(Modifier.padding(12.dp)) { content() }
    }
}

/** 页面底部固定 CTA 区（原型 .bottomcta：说明条在上、整宽主按钮在下，纵向排列） */
@Composable
fun BottomCta(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BrandSurface,
        border = BorderStroke(0.5.dp, HairLine)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** 错误色文字（用于“为什么没成功”） */
val ErrorTextColor: Color get() = ErrorRed
