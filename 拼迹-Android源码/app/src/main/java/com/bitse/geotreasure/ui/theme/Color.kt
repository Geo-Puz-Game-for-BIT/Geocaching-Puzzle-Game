package com.bitse.geotreasure.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 品牌色板 —— 与 design/tokens/tokens.json 及 design/prototype/index.html 的 :root 保持一致。
 * 改色请先改 token JSON，再同步这里，避免设计与代码两套色。
 */

// 主色（深绿）
val Primary = Color(0xFF00634D)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFE6EDE5)
val OnPrimaryContainer = Color(0xFF123A2E)

val Secondary = Color(0xFF4A5B54)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFE6EDE5)
val OnSecondaryContainer = Color(0xFF123A2E)

val Tertiary = Color(0xFF8A6A32)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFF5E6C8)
val OnTertiaryContainer = Color(0xFF33260B)

// 表面
val Background = Color(0xFFF8F7F2)
val OnBackground = Color(0xFF202923)
val Surface = Color(0xFFFFFEFA)
val OnSurface = Color(0xFF202923)
val SurfaceVariant = Color(0xFFE6EDE5)
val OnSurfaceVariant = Color(0xFF5D6962)
val Outline = Color(0xFF8A938D)
val OutlineVariant = Color(0xFFCFD8CF)

// 语义色
val Success = Color(0xFF1F7A4C)
val OnSuccess = Color(0xFFFFFFFF)
val SuccessContainer = Color(0xFFD6EBDD)
val Warning = Color(0xFF8A6A32)
val WarningContainer = Color(0xFFF5E6C8)
val OnWarningContainer = Color(0xFF4A2E00)
val ErrorRed = Color(0xFFB3261E)
val OnErrorRed = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFF9DEDC)
val OnErrorContainer = Color(0xFF410E0B)

/** 碎片、成就、剧情解锁的奖励色；不用于普通按钮（文本放在它上面要用深色） */
val RewardGold = Color(0xFFB58B45)
val OnRewardGold = Color(0xFFFFFFFF)
val RewardGoldSurface = Color(0xFFFBF3DD)
val RewardGoldSurfaceEnd = Color(0xFFF1E2BC)
/** 原型 .reward 的暖金底与文字色（P02 任务卡「奖励：…碎片 ×1」） */
val RewardBox = Color(0xFFFBF5E6)
val OnRewardBox = Color(0xFF5A4405)

/** 地图底图与允许半径 */
val MapCanvas = Color(0xFFEFF2EA)
val MapRoad = Color(0xFFE7EBE0)
val RouteLine = Color(0xFF146C5A)

/** 相机页深色底 */
val CameraScrim = Color(0xFF0B0F0E)
val CameraSurface = Color(0xFF10201B)

/** 卡片描边、分隔线 */
val HairLine = Color(0xFFE7E9DE)
val HairLineStrong = Color(0xFFCFD8CF)

/** 禁用态文字 */
val DisabledText = Color(0xFF8C9893)
