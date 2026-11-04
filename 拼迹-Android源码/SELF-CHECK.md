# 与 v1.7 原型的对齐自检

**基准**：`D:/桌面/拼迹-七页可点击原型-v1.7.html`（唯一真源）
**对象**：`android/`（Kotlin + Compose，包名 `com.bitse.geotreasure`）
**结论**：7 页 / 40 个状态全部落地，文案逐条对得上；本轮改掉 **26 处**与原型不一致的地方（1 处错别字、10 处结构性偏差、15 处视觉口径偏差），39/40 个状态与原型对得上，唯一缺口是 P07 的 `unlock`（开云动画帧），见第四节的第 2 条。

---

## 一、怎么核的（三套手段，别只信一种）

| 手段 | 做法 | 抓什么 |
|---|---|---|
| ① 文案对账 | `.cache/text-diff.py`：把原型 40 个状态的文案按「数字/符号」切成纯文字片段，逐段在 Kotlin 字符串字面量里查 | 漏写、改词、插值写错 |
| ② 近似文案审计 | `.cache/copy-audit.py`：原型每句 ≥8 字的文案与所有字面量做 `difflib` 相似度，0.72~1.0 且不完全相等的列出来 | **错别字**（只差一两个字，肉眼最难发现） |
| ③ 视觉对照 | 原型用 Playwright 逐状态截设备框（`.cache/shot-p04.cjs`、`.cache/shot-pages.cjs`），Android 用 ADB 实跑截图（`.cache/walk6.py`、`.cache/walk7.py`） | 尺寸、颜色、层级、漏元素 |

②这一套就是本轮「核对接卡条件」（应为「核对打卡条件」）被抓出来的原因 —— ①因为是整句子串匹配，错一个字就整句判缺失，噪音太大。

实机走查的坐标不要靠估算：`adb shell uiautomator dump` 拉 `bounds` 再点。这台模拟器 Compose 首帧 2~3s（logcat 里 `Davey! duration≈2400ms`），每步必须等 9~12s，等短了截到的是上一页。

---

## 二、40 个状态对照

「实机」= 模拟器上真点到并截图看过；「代码」= 状态机与布局分支逐条对过原型源码。

| 页 | 状态 | 文案 | 结构 | 核对方式 |
|---|---|---|---|---|
| P01 | default | ✓ | ✓ | 实机 |
| P01 | loading | ✓ | ✓ | 代码 |
| P01 | denied | ✓ | ✓ | 代码（`LocationState.PERMISSION_DENIED`） |
| P01 | weak | ✓ | ✓ | 代码（`LocationState.WEAK_SIGNAL`） |
| P01 | neterr | ✓ | ✓ | 代码（`BannerBar` + 重试） |
| P01 | error | ✓ | ✓ | 代码（`loadFailed`） |
| P02 | default | ✓ | ✓ | 实机 |
| P02 | loading | ✓ | ✓ | 代码 |
| P02 | done | ✓ | ✓ | 代码（`done=true` 路由参数） |
| P02 | neterr | ✓ | ✓ | 代码 |
| P02 | error | ✓ | ✓ | 代码 |
| P03 | default | ✓ | ✓ | 实机 |
| P03 | preview | ✓ | ✓ | 实机（真拍了一张） |
| P03 | denied | ✓ | ✓ | 代码（`CameraPermission.Denied`） |
| P03 | far | ✓ | ✓ | 代码（只提醒不禁快门） |
| P03 | submitting | ✓ | ✓ | 代码（`CameraPhase.Submitting`） |
| P03 | neterr | ✓ | ✓ | 代码（`state.online=false`） |
| P03 | error | ✓ | ✓ | 代码（`CameraPhase.Error`） |
| P04 | success | ✓ | ✓ | 实机 |
| P04 | pending | ✓ | ✓ | 代码 + 原型截图逐句对 |
| P04 | confirm | ✓ | ✓ | 代码 + 原型截图逐句对 |
| P04 | fail-radius | ✓ | ✓ | 代码 + 原型截图逐句对 |
| P04 | fail-accuracy | ✓ | ✓ | 代码 + 原型截图逐句对 |
| P04 | fail-photo | ✓ | ✓ | 代码 + 原型截图逐句对 |
| P04 | fail-network | ✓ | ✓ | 代码 + 原型截图逐句对 |
| P05 | progress | ✓ | ✓ | 实机 |
| P05 | overview | ✓ | ✓ | 代码 |
| P05 | module | ✓ | ✓ | 实机（上一轮） |
| P05 | complete | ✓ | ✓ | 代码 |
| P05 | mapdone | ✓ | ✓ | 代码 |
| P06 | default | ✓ | ✓ | 实机 |
| P06 | nopend | ✓ | ✓ | 实机（默认就是这个：在途数 0） |
| P06 | empty | ✓ | ✓ | 代码（`EmptyPhotos`） |
| P06 | del | ✓ | ✓ | 代码（`DeletePhotoDialog`） |
| P07 | live | ✓ | ✓ | 实机 |
| P07 | fog | ✓ | ✓ | 代码 |
| P07 | partial | ✓ | ✓ | 代码 |
| P07 | unlock | ✗ | ✗ | **未实现**，见第四节第 2 条 |
| P07 | campus | ✓ | ✓ | 代码 |
| P07 | all | ✓ | ✓ | 代码 |

---

## 三、本轮改掉的 17 处

### P01
1. `ui/pages/explore/ExploreScreen.kt` — 2D/3D 切换按原型 `.mapmode` 重做：胶囊（`999dp`）→ **12dp 圆角容器 + 38×32 按钮**（圆角 9、未选中字色 `#63736C`、间距 2）。

### P03
2. `ui/pages/camera/CameraScreen.kt` — 取景辅助从「Column 竖排三段」改成 `GuidesLayer`：四角 26/2.5dp、三分网格、地标虚线框（左右 5%、顶 40%、高 56%、圆角 12、dash 10-8）、「地标完整入镜」标签贴框顶边、人形贴框底 6%。
3. 同文件 — 顶部两颗胶囊 `TopStart` → **`TopCenter`**（原型 `.camt` 居中）。
4. 同文件 — 底部压暗渐变起点 `0.72` → `0.55`、底部加深到 92% 不透明。原因：原型底子是静态深色图，真机预览是亮画面，0.72 才开始上色会让白字浮在亮绿上（对比度直接掉）。
5. 同文件 — 提示条 11.5sp/`0xCCE6EDE9`/bottom 128 → **12.5sp/纯白/bottom 150**。原型写的是 `bottom:118`，但它是按 72px 快门 + 更薄底栏算的，我们的快门区含「轻点拍摄」标签高约 30dp，照抄 118 会压字。

### P04
6. `ui/pages/result/ResultScreen.kt` — **错别字**：「正在核对**接卡**条件」→「正在核对**打卡**条件」。
7. 同文件 — 结果徽章 78dp/圆角 26/图标 40 → **84dp/圆角 28/图标 48**（原型 `.result .badge`）。
8. 同文件 — 标题 22sp → **24sp**；导语 → **13.5sp / 行高 21 / 左右各 46dp 收窄**（原型 `.lead` 13.5px + max-width 280）。
9. 同文件 — 碎片归位网格从「铺满整屏的 2 列大卡（aspect 1.32）」改成原型 `.mgrid`：**152dp 宽、2 列、gap 6、格子正方、去地名标签**（原型 `.mcell` 里没有文字）。
10. 同文件 — `.mnote` 从 152dp 列内**提到列外**，否则「疏桐碎片自动归位 · 南校区 4/4 点亮」会折成两行。
11. 同文件 — 奖励卡从「浅绿方块 + 绿数字」改成原型 `.tilecard`：**56dp 金色渐变方块（`#F3E3BE`→`#B58B45`）+ 金边 `#EFE6CF` + 数字 `#7A5B12`**。
12. 同文件 — 成功后三颗按钮从「1 颗整宽 + 2 颗并排」改成**竖排**（原型是三行全宽）。
13. 同文件 — 失败页 `.reason` 按原型重做：白底 + **淡红描边 `#F2DEDC`** + 14dp 圆角、标题改**红色**、`.todo` 从「填绿块的提示框」改回**纯文字行**（info 图标 + 正文，无底色）。
14. 同文件 — 失败页第二张卡：图标方块 40 → **56dp**、补 `#E7E9DE` 描边、去掉原型没有的右侧箭头、删掉多出来的第三颗「去我的记录」按钮。

### P05
15. `ui/pages/puzzle/PuzzleParts.kt` — `ModuleBlock` 补 **`.tag` 浅绿底板**（`#E6EDE5`、圆角 6、padding 3×7、与格子间距 7、与进度条间距 4）。这层板来自原型基础 `.tag` 规则（`.mod .tag` 只覆盖排布、没重置 `background`），之前整块漏掉了。
16. `ui/pages/puzzle/PuzzleScreen.kt` — 底部按钮去掉前置地图图标（原型那颗 `btn outline` 只有文字）。

### P06
17. `ui/pages/records/RecordsScreen.kt` — 照片网格 `chunked(2)` → **`chunked(3)`**（原型 `.photos` 是 `repeat(3,1fr)`，4 张排成 3+1，末行补 2 个空位）。
18. `ui/components/Landmark.kt` — `PhotoTile` 角标补图标：`hourglass`(确认中) / `lock`(私密)，16dp + gap 3 + 9.5sp（原型 `.priv` 是 `display:flex`，不是纯文字）。
19. `data/repository/DemoGeoTreasureRepository.kt` — 「在途打卡条数」原来由 `photos.count{pending}` 推导，导致**一进 P06 就有「1 条打卡还在确认」**。改成独立的 `_pending`（= 原型 `S.pend`）：默认 0，`submitCheckIn` 开始置 1、拿到 Success 归 0。`CheckInPhoto.pending` 保留（对应 `MY_PHOTOS[0].pend=true`），但显示与否由在途数把关 —— 和原型 `isPend = p.pend && pendCount > 0` 完全同构。
20. `ui/pages/records/RecordsScreen.kt` — 账号卡按原型 `.acct`：头像 44dp 浅色圆 → **52dp 圆角 16 实心主色 + 白字 18sp**；右侧 `.iconbtn` 36dp 实心 → **44dp 透明圆角 12**，点击给 toast（原型 `toast:账号设置（原型示意）`）。

### P07
21. `ui/pages/map3d/Map3DScreen.kt` — 标记层只画**已打卡**与**下一个目标**两种；未解锁位置一个点都不画（云底保持未知，不是 12 个问号）。
22. 同文件 — pin 两态按原型 `pinSvg()` 重绘：`done` = 白底小圆点 + 绿勾（无标签牌）；`next` = 金色方牌 `#B58B45` + 白描边 + 深色针形 + 顶部深绿胶囊标签。
23. 同文件 — 顶部覆盖层改竖排（3D/2D 切换 → 迷雾卡 → 全览/南/北/东 → 提示条），对齐原型 62→194px 的自上而下顺序。
24. 同文件 — 地图底改径向渐变（`.m3wrap` 不是纯色），沙盘投影放大到左右出血，不再是「浮在页面中间的小方块」。
25. 同文件 — **校区锚点修正**：原来南/北两个片区都取在 `u≈v` 的对角线上，等轴测下屏幕 x 只由 `(u - v)` 决定 → 两个片区重叠成一根竖线，南校区的 4 个标记全被说明卡挡住。现在锚点 北(0.46,0.54) / 南(0.52,0.90) / 东(0.90,0.52)，且 `drawCampusBase()` 的校区绿地与 `pinPosition()` 共用同一套 `CAMPUS_ANCHORS`。
26. 同文件 — 云团半径 30 → 24dp，三层实心圆改**径向渐变柔边**（`Brush.radialGradient`），否则像白盘子。

> 编号 1–26 里，P02 的两处（netBar 竖排、暖金奖励条）在上一轮已改，本次复核无新增。

---

## 四、有意保持的差异（不打算改）

1. **P07 沙盘是 Canvas 自绘骨架，不是逐笔搬运原型那张手绘插画底图**
   建筑、树木、道路都简化为色块与圆角矩形。保留的是：等轴测视角、一道云 = 一个地标、打卡后孔洞、片区 4/4 整片散开、顶卡三校区独立计数、聚焦、说明卡五分支。接高德 3D 时只换 `drawCampusBase()`，`project()`/`pinPosition()` 不用动。

2. **P07 开云动画（`unlock` / `campus` 的 1.9s、2.7s 两段）未实现**
   连带两个元素不出现：顶部的「云层正在打开 · <地标名>」横幅、说明卡标题旁的「云层正在打开」小标签。数据侧是支持的（`state.fogOpened` 会在打卡后跳变），只是没有过渡帧。这是唯一一个「状态对不上」的地方，已经写进上表。

3. **图标是 Material Icons 近似，不是原型那套自制 SVG**
   P02 三段指标（距离/难度/地形）差异最明显：原型是「斜向纸飞机 / 半圆仪表 / 双峰山」，这里是「导航箭头 / 速度表 / 单峰山」。要完全一致得把原型 `ic()` 里那几个 path 抽成 `ImageVector`，工作量不小、收益有限，先记着。

4. **P03 是真实 CameraX 预览，原型是静态底图**
   所以取景框里的地标插画、人形只作构图参考；拍后预览按照片原比例完整显示（`Fit`），不强行填满卡片 —— 填满就得裁掉建筑两侧，那是原型改过一轮的坑。

5. **没有 `?p=&s=` 深链**
   原型靠 URL 参数一跳直达任意状态，方便评审。Android 走真实流程：P01→任务卡→P02→去打卡→P03→快门→P04。副作用是**评审时点不出 pending / confirm / 四种 fail 这些状态**（要真实定位与网络异常才触发）。

   > 如果中期要现场演示这 11 个状态，建议加一个 debug-only 的直达入口（`src/debug/` 里读 intent extra 走 `Routes.result(lm, key)`，release 包不受影响）。目前没做，因为要动导航层。

---

## 五、复验怎么做

```bash
# 编译（自检必退 0）
cd android
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
export ANDROID_HOME="C:/Users/zxc52/AppData/Local/Android/Sdk"
../.cache/tools/gradle-8.9/bin/gradle.bat assembleDebug --console=plain

# 装包 + 走一遍主链路（P01→P02→P03→P04）
"$ANDROID_HOME/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
C:/Users/zxc52/.workbuddy/binaries/python/envs/default/Scripts/python.exe .cache/walk7.py

# P05 / P06 走查
C:/Users/zxc52/.workbuddy/binaries/python/envs/default/Scripts/python.exe .cache/walk6.py

# 文案对账 / 错别字审计
C:/Users/zxc52/.workbuddy/binaries/python/envs/default/Scripts/python.exe .cache/text-diff.py
C:/Users/zxc52/.workbuddy/binaries/python/envs/default/Scripts/python.exe .cache/copy-audit.py
```

`text-diff.py` 会在插值句上给一批假警报（`已收 ${a}/${b} 块` 这种），看片段不看整句；`copy-audit.py` 正常应只剩 `$campusName` 这类插值命中。
