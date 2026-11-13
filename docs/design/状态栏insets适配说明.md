# 状态栏 / 导航栏 insets 适配说明（真机问题修复）

> 分支 `feat/android-ui-refresh-v2` · 2026-11-13
> 触发：真机走查发现 P01 头图与状态栏之间有空隙（露出地图）、P05「拼图收集」与 P06「我的记录」标题与状态栏重叠。

## 1. 根因

`MainActivity` 全局启用 edge-to-edge：

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```

于是所有页面都从 y=0 开始绘制、状态栏区域透明。但两处头部组件没有按这个前提处理：

| 组件 | 原实现 | 后果 |
|---|---|---|
| `BrandHeader`（P01） | `statusBarsPadding()` 加在**外部** | 状态栏那条带子透明 → 露出底下地图，形成「空隙被地图所占」 |
| `PageHeader`（P02/P04/P05/P06） | 固定 60dp、**完全无状态栏 inset** | 标题从 y=0 起画，中段（约 20–40dp）正好压在状态栏时间/电量上 |

`AppTabBar` 原本已是正确范式（背景铺满 + `navigationBarsPadding()`），PageHeader 与它不一致才出问题。

## 2. 修复（统一范式：先铺满背景，再吃 inset）

1. **`PageHeader`**（`core/ui/components/Headers.kt`）
   ```kotlin
   .fillMaxWidth()
   .background(background)     // 背景包含状态栏那条带子
   .statusBarsPadding()        // 标题行整体下移到状态栏下方
   .height(Dimen.pageHeaderHeight)
   ```
   一次改动覆盖 地标详情 / 打卡结果 / 拼图收集 / 我的记录 四页。

2. **`BrandHeader`**（同文件）
   ```kotlin
   Column(modifier.fillMaxWidth()
       .background(HeaderPaper)   // #FBFAF5，取自 brand_header.png 第 0–40 行实测色，接缝不可见
       .statusBarsPadding())
   ```
   头图与头像热区整体位于状态栏下方，头图**视觉上上移**贴住状态栏；`MapScreen` 里原来的外部
   `statusBarsPadding()` 已删除。

3. **`CameraScreen`（P03 深底页）**
   - `Scaffold(contentWindowInsets = WindowInsets(0,0,0,0))`：顶部改由 PageHeader 自身 inset 负责，
     避免 Scaffold + Header 双重 inset 造成的大缝隙（底部早已有 `navigationBarsPadding`）。
   - 新增 `core/ui/system/SystemBars.kt` → `StatusBarIconsDark(darkIcons=false)`：深底页状态栏图标切浅色，
     否则主题里 `windowLightStatusBar=true`（深色图标）会在 #10201B 顶栏上「看不见」；离开页面自动还原，
     不会污染其它页面。

4. **底部安全区**：`BottomCta`（P02 CTA）与 P04 结果页补 `navigationBarsPadding()`，
   手势条 / 三键导航机型都不会遮住按钮。

5. **字体缩放**：`PageHeader` 由固定 `height(60dp)` 改为 `heightIn(min = 60.dp)`，
   系统字体放大到 1.3×/1.5× 时标题与副标题不会被裁切（默认字号下仍是 60dp，视觉不变）。

## 3. 为什么能覆盖不同机型

- 全部使用**系统上报的 inset**（`statusBarsPadding` / `navigationBarsPadding`），没有写死 24/30dp 之类常量，
  刘海屏、打孔屏、升降摄像头、手势导航、三键导航、Android 15 强制 edge-to-edge 都按各机实际尺寸计算。
- 状态栏图标明暗随页面切换（浅色页深色图标、深底页浅色图标），进入记录「进入前取值」、离开还原，
  任意导航顺序都不会残留错误状态。
- 头图顶部同色底取自素材实测像素，不同宽度下都是纯色带，不会出现色块接缝。
- 顶栏高度随系统字体缩放自适应，不裁字、不压内容。

## 4. 提交与验证

- 提交：**`0af0b4e`**（insets 修复）+ **`dd1ae20`**（顶栏高度随字体缩放），基于已推送的 `8102fff` / `b63b4d8`
- `:app:compileDebugKotlin` / `:app:assembleDebug`：BUILD SUCCESSFUL
- `:app:testDebugUnitTest`：**137/137 通过**
- `:app:lintDebug`：0 errors / 7 warnings（与基线一致）
- APK：`ui-v2-dd1ae20-状态栏适配-debug.apk`（SHA256 `18996add…52c04`）
- 推送：本轮 GitHub 443 连续不可达，两个提交暂存本地；同时导出补丁
  `拼迹-交付-v1.3/文档/0001-*.patch`、`0002-*.patch`，网络恢复后执行
  `git am 0001-*.patch 0002-*.patch && git push origin feat/android-ui-refresh-v2` 即可。
- 待真机复验：P01 头图上移后无地图空隙、P05/P06 标题与状态栏不重叠、P03 深底页图标可见。

