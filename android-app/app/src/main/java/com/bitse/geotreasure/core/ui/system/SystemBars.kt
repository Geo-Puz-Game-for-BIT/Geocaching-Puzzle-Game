package com.bitse.geotreasure.core.ui.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 状态栏图标明暗控制（edge-to-edge 下必需）。
 *
 * 主题默认 [android.R.attr.windowLightStatusBar] = true（深色图标），适配 P01 奶油底 / P05 P06 浅色页面。
 * P03 相机页等深色页面必须在进入时切换为浅色图标，否则状态栏「时间/电量」会和深底融为一体看不见。
 * 离开页面时自动还原为进入前的取值，所以不同机型、不同导航顺序都不会残留错误状态。
 */
@Composable
fun StatusBarIconsDark(darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, darkIcons) {
        val controller = view.context.findActivityOrNull()
            ?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = darkIcons
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }
}

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
