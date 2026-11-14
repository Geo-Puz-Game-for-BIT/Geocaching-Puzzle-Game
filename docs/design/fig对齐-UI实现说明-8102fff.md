# .fig 原型对齐 · UI 实现说明（commit `8102fff`）

> 分支 `feat/android-ui-refresh-v2`（基于 `3e895c2`）· 2026-11-13
> 背景：真机检验发现界面与设计不符。经对 `.fig` 全量解析与文案对账确认：根因不是 `.fig` 版本陈旧，
> 而是 P01–P05 各屏此前为 Material 通用布局 + 自创文案，并非 `.fig` 的忠实实现。
> 本轮以交付包内 `拼迹-可点击原型.fig`（52 帧导出）为**唯一 UI 规格源**逐屏重写。

## 1. 对账方法

1. `.fig` 解析：`.fig` = ZIP（canvas.fig + meta.json + images/*）；canvas.fig 为 Figma kiwi 格式，
   用 npm `openfig-core@0.4.1` 解析，导出 52 帧 JSON（每帧含节点扁平树：类型/坐标/尺寸/填充/文字/字号/对齐）。
   顶层 52 帧全部 412×915（≈ Android dp 1:1）。
2. 文案对账：脚本按「帧文本 → 全工程 Kotlin 归一化匹配」比对（合并 y 相邻同字号碎片、识别动态占位），
   证实 P01–P05 原型文案在改码前几乎全缺失（连「去打卡」「拼图收集」都不存在），P06/P07 接近。
3. 改码后重跑对账：静态缺失项均为动态模板（如 `"和${lm.name}合个影"`、`"${lm.name} · 同框打卡"`），
   已用模板关键字逐条复核命中。

## 2. 逐屏改动（10 文件，+1333 / −668）

| 屏 | 文件 | 改动 |
|---|---|---|
| 公共 | `core/ui/components/Proto.kt`（新增） | 原型辅助层：星级/地形/任务文案/打卡规则/提示盒/校区分段控件 |
| 数据 | `core/model/LiangxiangTreasureCatalog.kt` | 12 地标补 `difficulty/terrain`（难度 1=★☆☆ 2=★★☆；地形 1=平地 2=林荫 3=台阶 4=湖畔） |
| Token | `core/ui/theme/Color.kt` | 新增 `HintBox #F3F4EC`、`FragmentCell #DCE5E1` |
| P01 | `feature/map/MapScreen.kt` | 原型顶栏 + 校区分段（选中 #00634D 白字）+ 片区进度卡（四段进度条 + 推荐位）+「地图为示意」胶囊 + 2D/3D 切换 + 底部地标面板（拖柄/缩略图/提示语/约 X m/查看任务） |
| P02 | `feature/landmark/LandmarkDetailScreen.kt` | 252dp Hero（cover + 渐变）+ 距离/难度/地形三格事实卡 + 关于 + 任务卡（编号 pill + 奖励盒 + 提示盒）+ 打卡规则 + 到点门控六态保留 + 「去打卡」 |
| P03 | `feature/camera/CameraScreen.kt` | 深底 #0B0F0E + 六态副标题（「X · 同框打卡」等）+ 取景叠加（距目标 chip / 三分线取景框 / 「地标完整入镜」）+ 72dp 白色快门 + 拍后确认文案 + 重拍/确认提交 |
| P04 | `feature/checkin/CheckInResultScreen.kt` | 按成功帧重做：84dp 绿圆 + 「打卡成功」+ 碎片归位格（当前块高亮）+ 奖励卡（金渐变 + 金色 4/4）+ 查看/3D/继续三键；补 `onOpenMap`/`onOpenOverview3D` 接线 |
| P05 | `feature/puzzle/PuzzleScreen.kt` | 「我的校园地图」+ 总进度条 + 三片区卡（2×2 碎片格 + 已收 N/4 条）+ 下一块碎片卡（「去看看」）+ 归位说明；删自研 RevealModeSelector UI（VM 保留） |
| P06 | `feature/records/RecordsScreen.kt` | 页头改 PageHeader、片区改全称（南/北/东校区）、统计行去图标、照片区标题精简 |
| 导航 | `navigation/GeoTreasureNavHost.kt` | P04 结果页两个回调接线 |

## 3. 设计取舍（trade-offs，需评审知悉）

1. **P04 七态裁剪**：`.fig` 含成功/处理中/待确认/未成功×4 共 7 帧；app 架构中「处理中/失败」由
   相机页横幅承载，仅成功页是独立路由。按架构重做成功页，不为此重构导航。
2. **P05 两帧合并**：原型「总览」「片区」两帧合并为单页三张片区卡（含「还差 N 块」信息），不新增子页导航。
3. **奖励计数口径**：原型「南校区第 4/4 块」为演示终态；实现取 `pieceIndex+1`（碎片片区内序号）。
4. **P07 已于后续提交对齐**：见文末「P07 3D 总览页 .fig 对齐（后续提交）」一节。
5. 前端仍不写死打卡半径/人脸比对（口径：提交后服务端判定），`.fig` 中「约 68 m」等数值为演示占位。

## 4. 验证数据

- `:app:testDebugUnitTest`：**137/137 通过**（19 个套件，0 failures / 0 errors）
- `:app:compileDebugKotlin` / `:app:assembleDebug`：BUILD SUCCESSFUL
- `:app:lintDebug`：**0 errors / 7 warnings**（与上轮基线一致）
- APK：`ui-v2-8102fff-fig对齐-debug.apk`（121.6 MB，含 AMAP_API_KEY、浅色主题）
  SHA256 `02ba977c060ff7564ba46bb0100c95205ab3d3f6848311d40db68f3181e4de15`
- 推送：`3e895c2..8102fff feat/android-ui-refresh-v2`（GitHub 443 本次可用）
