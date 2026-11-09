[README.md](https://github.com/user-attachments/files/34558716/README.md)
# 拼迹 · 校园地标寻宝 — Android 客户端

由 `design/prototype/index.html`（**v1.8** 可点击原型，PR#8 整改版）+ `design/tokens/` 转出的原生 Android 工程。
技术栈按既定路线：**Kotlin · Jetpack Compose · Material 3 · MVVM/Repository/UDF · Hilt · Room · CameraX**。

- 包名 / applicationId：`com.bitse.geotreasure`
- `compileSdk 36 · minSdk 26 · targetSdk 35（有意保持）· JVM 17 · Build Tools 35.0.0`
- AGP 8.13.2 · Gradle 8.13（wrapper，腾讯镜像）· Kotlin 2.0.20 · Compose BOM 2024.09.03 · Hilt 2.52 · CameraX 1.3.4 · Room 2.6.1

> **⚠️ 交付边界（PR#8 整改，务必先读）**
> 本工程是**独立 UI 演示工程**：默认演示定位 12 m、初始进度 4/12、提交后本地延时授奖 ——
> 均为**演示配置**，用于走查布局与状态展示，**不是已接入 B/C/D 的正式打卡流程**。
> 正式 UI 实装请按 `UI-REFRESH-PLAN.md`：从最新 `integration/c-camera-cloudbase`（#11）拉
> `feat/android-ui-refresh` 分支迁移主题 / 公共组件 / 普通页面；地图与 B、相机与 C、后端沿用 D，
> 不另建第二套提交链路。

## 〇之零、唯一当前版本与检查入口（PR#8 指定）

| 项 | 唯一当前值 |
|---|---|
| 设计源 | `design/prototype/index.html`（**原型 v1.8**，2026-11-04 PR#8 整改版；SHA 见 `docs/PR8-整改回复-2026-1104.md`） |
| 交付 PDF | `dist/拼迹-第4组-七页高保真原型-v3.1.pdf`（29 页 / 40 屏，与 v1.8 同步重出；v3.0 已归档） |
| Token | `design/tokens/tokens.json`（主色 `#00634D`；`design/tokens/Theme.kt` 已按其校正，旧 `#146C5A` 作废） |
| Token 检查 | `node design/tools/tokens.mjs all` —— 本工作区 **contrast ✅ / audit ✅ / actual ✅**；**仓库内 actual 为 NOT_RUN**（本次交付不含仓库 HTML，见下） |
| 资源清单检查 | `node design/tools/build-manifest.mjs --check` —— 47 资产 + 52 界面状态，✅ 可交付 |
| HTML 说明 | **本次采用 Figma（.fig）+ PDF 交付，仓库内不提供 HTML 原型**（唯一真源 HTML 留存在设计工作区，不入库）；`tokens.mjs` 的 actual 阶段如需在仓库跑，可临时取用工作区 HTML，结果记 RUN；否则如实记 NOT_RUN |
| UI 实装 | `android/UI-REFRESH-PLAN.md`（feat/android-ui-refresh 分支计划） |
| Gradle 冲突 | `docs/PR8-Gradle冲突解决指引.md`（三文件以 #11 为基线） |

---

## 〇、这份交付包里有什么

| 内容 | 说明 |
|---|---|
| `app/src/` | 全部源码：36 个 Kotlin 文件 + `res/`（25 张地标/头图 + 图标 + 主题） |
| `gradlew` `gradlew.bat` `gradle/wrapper/` | Gradle 8.9 wrapper，distributionUrl 已指向腾讯镜像 |
| `gradle/libs.versions.toml` | 版本目录（AGP / Kotlin / Compose BOM 等集中在这） |
| `README.md` | 本文件：编译方法、页面映射、状态口径、接后端改动点、视觉口径 |
| `SELF-CHECK.md` | 与 v1.7 原型的逐状态对齐报告（40 状态对照表 + 26 处修正 + 5 条有意差异） |
| `apk/app-debug.apk` | 已编译的 debug 包，不搭环境也能先装机看效果 |
| `shots/` | API 35 模拟器七页实跑截图（README 第七节的核对依据） |

**不含** `local.properties`（机器相关，见第一节）、`build/` 与 `.gradle/`（构建产物）。

> **本 README 是产品口径的唯一当前入口**（2026-11-04 起）：`docs/scope.md`、`docs/user-flow.md` 已在顶部标注
> 「历史文档，冻结为历史版本」，不再随迭代更新。界面事实以 `design/prototype/index.html`（v1.7，40 状态）与
> `dist/拼迹-第4组-七页高保真原型-v3.0.pdf`（29 页）为准。
> 路径口径：本文截图名指本包 `shots/`（仓库内为 `dist/android/shots/`）；代码路径省略前缀
> `app/src/main/java/com/bitse/geotreasure/`。

## 〇之一、当前产品口径（拼图与打卡）

| 口径 | 内容 |
|---|---|
| 拼图结构 | **三校区（南/北/东）各一个 2×2 模块，共 12 片**；1 片 = 1 个地标的 1 张打卡照 |
| 碎片点亮 | **打卡提交后由服务端判定，成功即自动点亮对应格**——客户端不做半径/精度预判，也没有「手动拖拽拼合」步骤 |
| 手动拼合 | **不作为玩法**，只保留在 P05 作后续回顾（看已收格与缺口），不产生任何奖励 |
| 结果未知 | 提交后服务端未回结果 = 「确认中」，**不发碎片**；恢复时先查原尝试（Room 留痕）再决定补发，不重复提交 |
| 演示账号 | 当前为**固定演示账号（向川）**，P06 账号卡已标明；接账号体系后替换 |
| 3D 页（P07） | **本轮后置**：演示以 P01–P06 六页为准，P07 保留入口但不算必做验收项 |

## 一、编译与运行

工程已带 **Gradle wrapper（8.13）**，`distributionUrl` 指向腾讯镜像（`mirrors.cloud.tencent.com`），国内网络直接同步、不用配代理。版本口径（JDK 17 · SDK 36 · Build Tools 35.0.0 · AGP 8.13.2）见仓库根 `docs/统一工具链.md`。

```bash
# Windows
gradlew.bat assembleDebug
# macOS / Linux
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（约 22 MB，含 25 张地标图）。

**首次配置**：把 `local.properties.example` 复制为 `local.properties`，填写本机 `sdk.dir`。
需要真实高德地图时，在同一文件填 `AMAP_API_KEY`（Android 平台 Key，绑定包名 `com.bitse.geotreasure` 与团队 debug/release 证书 SHA-1）；**没有 Key 时应用自动使用可交互的演示地图**，构建不受影响。Key 只允许放 `local.properties`（不入库）或环境变量，详见 `local.properties.example` 内注释。

**Android Studio（推荐队友用这条）**：File → Open → 选 `android/` 目录，首次 sync 会通过 wrapper 自动下载 Gradle 8.13 与依赖（google() + mavenCentral()）。
`local.properties`（SDK 路径）是机器相关的，交付包里**不含**此文件：AS 打开时会提示 SDK location not found，点提示里的链接或自行创建一个写 `sdk.dir=你的SDK路径` 即可。

**Windows 完整验收（干净构建 + 单元测试 + APK + Lint）**：

```powershell
# 仓库根目录执行
.\android\scripts\verify-windows.ps1
```

脚本只在需要时创建临时英文盘符（`subst`）：仓库路径含中文时，AGP 可以靠 `gradle.properties` 里的
`android.overridePathCheck=true` 构建 APK，但 Windows JUnit classpath 仍可能找不到测试类，
所以统一验收走脚本；结束自动解除映射。长期开发仍建议把仓库克隆到 `D:\Projects\Geocaching-Puzzle-Game` 之类的纯英文路径。

命令行备选（本机无全局 gradle 时）：仓库根目录 `.cache/tools/gradle-8.9/bin/gradle.bat assembleDebug`。

---

## 二、页面 ↔ 代码映射

| 原型 | 路由 | 代码 | 状态 |
|---|---|---|---|
| P01 探索地图 | `explore` | `ui/pages/explore/ExploreScreen.kt` + `ExploreViewModel.kt` | 默认 / 定位中 / 拒绝授权 / 弱信号 / 断网 / 加载 |
| P02 地标详情 | `landmark/{landmarkId}?done={done}` | `ui/pages/landmark/LandmarkDetailScreen.kt` + `LandmarkDetailViewModel.kt` | 默认 / loading 骨架 / 已打卡 / 断网横幅 / 找不到地标 |
| P03 拍照打卡 | `camera/{landmarkId}` | `ui/pages/camera/CameraScreen.kt` + `CameraViewModel.kt` | 取景 / 拍后预览 / 提交中 / 提交失败 / 无相机权限 |
| P04 打卡结果 | `result/{landmarkId}/{outcome}` | `ui/pages/result/ResultScreen.kt` + `ResultViewModel.kt` | success / pending / confirm / fail-radius / fail-accuracy / fail-photo / fail-network |
| P05 拼图收收集 | `puzzle` · `module/{moduleId}` | `ui/pages/puzzle/PuzzleScreen.kt` + `PuzzleViewModel.kt` + `PuzzleParts.kt` | 总览 / 片区 / 全部完成 |
| P06 我的记录 | `records` | `ui/pages/records/RecordsScreen.kt` + `RecordsViewModel.kt` | 默认 / 有待确认 / 空态 / 删除确认框 |
| P07 3D 地图（**后置**） | `map3d` | `ui/pages/map3d/Map3DScreen.kt` + `Map3DViewModel.kt` | 跟随进度 / 全散开 |

公共件：`ui/components/`（Headers / Common / Landmark）、主题 `ui/theme/`（Color / Type / Shape / Theme）、导航 `ui/navigation/`（Routes / GeoTreasureNavHost / OutcomeKeys）。

### 一条硬规则：地标 id 只走路由

`landmark/LM-003` 里的 id 由调用方传入，页面内**没有**「取当前片区下一个待办」这类推断。
原型曾出现「点理教却显示疏桐」，根因就是详情页用 `lmIdx()` 自己猜；这里从结构上把这条路堵死。
唯一取图入口是 `ui/assets/LandmarkImages.kt`，页面里不允许写 `when(id)`。

---

## 三、状态口径：一处定义，四处显示

```
GeoTreasureRepository.progress : StateFlow<UserProgress>
        ├── P01 进度卡 / 水滴明暗 / Tab 角标
        ├── P02 「第 N/4 块」奖励文案
        ├── P04 成功页 2×2 归位（哪一格描金边）
        ├── P05 总览与片区页（ownedSlots）
        └── P06 三个统计 + 片区进度条
```

- 进度存 `ownedSlots: List<Int>`（哪几格），**不是**「前 N 格」。原型踩过这个坑：总览亮疏桐、片区页暗工业生态楼，两边正好相反。
- 底部 Tab 的「x/12」统一取 `UserProgress.totalShards`，三页同一个数。
- P06 的「N 条打卡还在确认」取 `pendingCount`（真正在途的条数），为 0 时整卡不渲染。

---

## 四、接真后端只改这三处

| 要替换的 | 现在 | 换成 | 改动量 |
|---|---|---|---|
| 业务数据 | `data/repository/DemoGeoTreasureRepository.kt`（内存 + 1.5s 模拟判定） | `CloudBaseGeoTreasureRepository` | `di/AppModule.provideRepository()` 一行 |
| 定位 | `data/location/DemoLocationProvider.kt`（固定 12m） | 高德 `AMapLocationProvider` | `di/AppModule.provideLocationProvider()` 一行 |
| 拍照 | CameraX `Preview` + `ImageCapture`，落在 `cache/checkin/` | 不变，照片路径交给 `submitCheckIn` 上传 | 无 |

`GeoTreasureRepository` 的接口已经按云函数形状设计：

```kotlin
val progress: StateFlow<UserProgress>          // ← watchProgress(userId)
fun landmark(id: String): Landmark?            // ← getCampusMap() 下发后本地索引
suspend fun submitCheckIn(landmarkId, photoPath): CheckInOutcome  // ← uploadFile + checkIn 云函数
fun deletePhoto(photoId)                       // ← 只删照片，不撤销碎片
```

`submitCheckIn` 的返回直接映射到 P04 的七个结局（`CheckInOutcome` → `OutcomeKeys`），
所以「距离不够 / 精度不够 / 照片不合格 / 网络断了」这四套文案不用改一行。

Room 只做一件事：`check_in_records` 留痕（landmarkId / photoPath / 时间 / 状态），
**不做进度主存** —— 进度是服务端的账，本地不再记第二本。

---
## 四之一、能力实现状态表（不冒充已完成）

| 能力 | 状态 | 说明 |
|---|---|---|
| 模块 / 地标数据 | ✅ 本地实现 | `DemoContent` 演示种子；接 `getCampusMap` 后由云端下发 |
| 打卡提交流程 | ✅ 已接 | 提交 → 处理中 1.5s → 结果，全链路可跑；**判定本身**由服务端承担，demo 用 1.5s 模拟 |
| 碎片发放 | ✅ 成功才发 | 提交结果未知（pending/confirm）**不发碎片**；提交前先查原尝试（Room PENDING 留痕），不重复提交 |
| 照片本地留存 | ✅ 已实现 | CameraX 落盘 `cache/checkin/` + Room 留痕 |
| 删除照片 | ✅ 本地 / ⏳ 云端 | 内存 + Room 同步删；**云端副本删除等 D 的接口，不做假成功** |
| 打卡记录查询 | ✅ 本地 / ⏳ 云端 | P06 由 `photos` Flow 派生；云端历史查询接口未定 |
| 定位 | ✅ 演示 / ⏳ 高德 | `DemoLocationProvider` 固定 12m；换 `AMapLocationProvider` 只动 `di/AppModule` 一行 |
| 到点才开拍门槛 | ✅ 演示值 | `CameraViewModel.ARRIVAL_METERS = 60f` 是演示配置，接后端换服务端下发；是否通过仍服务端判 |
| P04 六个不可直达状态 | ✅ 渲染 / ⏳ 入口 | `previewOutcome()` 已按态映射，正常操作点不到，debug 入口见第八节 #5 |

## 四之二、跨成员依赖（缺入口 / 缺接口的都在这，不闭门造车）

| 依赖 | 对象 | 内容 |
|---|---|---|
| `checkIn` 云函数返回结构 | D（后端） | 四种失败原因（距离/精度/照片/网络）+ pending/confirm 两态，形状按 `CheckInOutcome` |
| `getCampusMap` 云函数 | D | 模块与地标下发，结构按 `CampusModule` |
| 照片删除 / 历史查询接口 | D | P06 的云端删除与历史查询；没给之前界面上只做本地能力 |
| P04 debug 直达入口 | B（框架） | `src/debug/` 读 intent extra 直跳 `Routes.result(lm, key)`，中期演示 7 个不可达状态用 |
| 设置页入口 | B | P01「去设置」目前跳系统设置；应用内设置页待导航方案 |

---

## 五、与原型的有意差异（都写在代码注释里）

1. **P03 不再由前端算「是否偏远」**
   原型里 `far` 态靠距离判断。设计稿明确「界面不预设打卡半径、也不写死精度门槛，是否通过由服务端在提交后判定」，
   所以这里只显示「距目标约 X m」，不出现基于本地阈值的红/绿提示。要保留那个提示，应由服务端在 `checkIn` 返回里带一个 hint。

2. **P07 的 3D 沙盘用 Canvas 重画了骨架，不是逐笔搬运原型的 1000 行 SVG**
   保留：等轴测视角、一片云 = 一个地标、打卡成功后该地标头顶的云散开、片区 4/4 整片散开、
   顶卡计数（X/12 片 · Y/3 校区）、校区聚焦、底部 sheet 的文案与入口。
   接高德 3D 后把 `drawCampusBase()` 换成 MapView，`project()` 的云与 pin 定位逻辑不用改。

3. **不做「假装已实现」的入口**
   原型里的「收藏」「账号设置」是 toast 占位。这里保留按钮位置，但不伪造成功反馈。
   真正能点的（已在模拟器上逐个点过）：地标水滴、查看任务、去打卡、快门、重拍、确认提交、
   查看拼图、去 3D、片区卡、地标清单项、我的记录、删除照片（带确认框）、P01/P07 的 2D↔3D 与全览/南/北/东。

4. **禁用 dynamicColor**
   跟随系统取色会把品牌绿 `#00634D` 冲掉，直接影响视觉分，`ui/theme/Theme.kt` 里已注明。

5. **人脸文案与演示账号标签（采纳 UI 评审后更新）**
   人脸口径统一为「**检测清晰人脸，不比对身份**」（P02 规则、P03 说明、P04 fail-photo 三处）；
   P06 账号卡加「演示账号」标签。v1.7 原型还是旧话术（「不做人脸识别」），下一版原型同步回改。

6. **P03 快门加了「到点才开拍」门槛（UI-03）**
   原型 p03:far 是「只提醒、不禁用快门」；采纳评审后改为：联网 + 定位就绪 + 距目标 < `ARRIVAL_METERS`
   三个条件齐备才能按快门（演示 60m，接后端换服务端下发）。**是否打卡通过仍由服务端判定**，口径不变。

---

## 六、视觉口径（验收自查用）

| 项 | 值 | 落在哪 |
|---|---|---|
| 主色 | `#00634D` | `theme/Color.kt` `Primary` |
| 背景 / 卡片 | `#F8F7F2` / `#FFFEFA` | `Background` / `Surface` |
| 次表面 / 其上文字 | `#E6EDE5` / `#5D6962` | `SurfaceVariant` / `OnSurfaceVariant` |
| 描边 | `#8A938D` / `#CFD8CF` / `#E7E9DE` | `Outline` / `OutlineVariant` / `HairLine` |
| 奖励金 | `#B58B45` | `RewardGold` |
| 控件圆角 14 · 卡片 18 · 格子 8 | `Radius.*` | `theme/Shape.kt` |
| 1 CSS px = 1 dp（412 宽屏） | 尺寸直接沿用原型数值 | 全工程 |

**图片缩放规则（最容易错的一条）**

| 位置 | 容器 | ContentScale |
|---|---|---|
| P02 Hero | 412×252 宽幅 | `Crop` |
| P03 拍后预览 | 近全屏 | `Fit`（contain） |
| P01 任务卡缩略图 / P05 格子 / P06 照片 | 方形 | `Fit` |

3:2 的抠图放进方形容器时用 `Crop`，会把建筑两侧切掉 —— 这是原型改过一轮的问题，代码里 `LandmarkImage(crop=)` 默认就是 `Fit`。

**改样式的规则（A-N03）**：颜色 / 字号 / 间距 / 圆角 / 按钮 / 卡片 / 空态 / 错误态全部集中在
`ui/theme/`（Color · Type · Shape）与 `ui/components/`（Common · States）。改样式**改公共定义**，
不要在页面里写 `Color(0xFF…)` 硬编码——页面里出现的复用色已收敛为 `GoldText` / `OnPrimaryContainer` / `HairLine` 等 token。

---

## 七、已验证（API 35 模拟器实跑，截图在 `dist/android/shots/`）

✅ `gradle assembleDebug` → `BUILD SUCCESSFUL`，零 error、零 warning，APK 21.6 MB
✅ 25 张地标图与头图打包进 `res/drawable-nodpi`，`LandmarkImages` 12 → 12 一一对应
✅ **七个页面全部在 AVD `gt35`（Pixel 6 / Android 15 / 1080×2400 / 420dpi）上跑通并截图**
（`emulator -avd gt35 -no-window -gpu swiftshader_indirect`，WHPX 加速）

| 页 | 截图 | 核对到的点 |
|---|---|---|
| P01 | `01-explore.png` | 头图不被状态栏压、三校区切换、3 个已打卡水滴 + 1 个「？」目标、任务卡、Tab 角标 4/12 |
| P02 未解锁 | `02-detail-todo.png` | Hero `Crop` 铺满、三格信息、任务卡、底部「去打卡」 |
| P02 已打卡 | `02-detail-done.png` | 底部换成「该地标已完成…」+「查看拼图」tonal |
| P03 取景 | `03-camera.png` | CameraX 预览 + 四角取景框 + 三分网格 + 虚线人形 + 快门 |
| P03 拍后 | `03-camera-review.png` | `Fit` 大图、重拍 / 确认提交 |
| P04 | `04-result-success.png` | 2×2 归位、新碎片描金边、两个次级入口一屏放下 |
| P05 总览 | `05-puzzle-overview.png` | 三张片区卡、锁定格、南校区金边「已合成」 |
| P05 片区 | `05-puzzle-module.png` | 3/4 明暗、地标清单四项、待打卡/已打卡 |
| P06 | `06-records.png` `06-records-bottom.png` | 账号卡、三格统计、片区进度、待确认条、照片两列等宽 |
| P06 删除 | `06-delete-dialog.png` | 确认框文案 + 红底删除 |
| P07 | `07-map3d.png` `07-map3d-east.png` | 斜轴测台面、12 个水滴、未解锁头顶云团、聚焦切换 |

## 七之二、实机暴露并修掉的问题（都是「编译通过但跑起来不对」）

| 现象 | 根因 | 修法 |
|---|---|---|
| P01 一进去就崩 | `CampusSwitch` 里 `Modifier.weight(0f)` 非法 | 删占位块，整颗胶囊 `.clickable` |
| 状态栏压住品牌头图 | 没处理 edge-to-edge | 七个页面根容器统一 `statusBarsPadding()` |
| P01 底图几乎空白 | `MapBackground` 只画了 4 条淡路 | 重写：建筑块 + 树点 + 双线路网 + 绿地 |
| 水滴只显示「？」，图形没出来 | `addPathNodes` 解析 SVG 字符串静默失败 | 改几何构造（圆 + 三角，按 `size.width/34f` 预缩放） |
| P01 的「3D」点不动 | `Text` 只做了选中态样式、没绑点击 | 加 `.clickable { onOpen3D() }` |
| P04「查看拼图」落到片区页 | 回调直接 `navigate(MODULE)` | 改 `PUZZLE`（原型 `go:p05` 是总览） |
| P07「去这个地标打卡」绕到详情页 | 回调走了 `landmark` 路由 | 直达 `camera`，与按钮文案一致 |
| P03 快门被手势条压住 | `ShutterBar` 无导航条内边距 | 加 `navigationBarsPadding()` |
| P03 拍后按钮贴底 | 同上 | 底部按钮行加 `navigationBarsPadding()` |
| P04「继续探索」在屏幕外 | 方格子铺满 + 三个按钮竖排 | 格子 `aspectRatio` 1.32 + 两个次级入口并排 |

## 七之三、模拟器自身的限制（不是代码问题）

- **虚拟相机的拍照结果是彩块**（swiftshader 渲染的测试场景，不是实景），所以 P03 拍后预览与 P06 照片里是色块。
  真机上换 `CameraX ImageCapture` 的真实输出即可，代码路径不变。
- P06 的照片网格在 demo 里用 `LandmarkImages` 兜底显示地标图；接云存储后改成按 `fileID` 加载。
- 无头模拟器上相机首帧有 ~1 s 延迟，截图时容易拍到黑底。

---

## 八、下一步建议（按性价比排序）

1. **接 CloudBase**：先写 `getCampusMap` + `checkIn` 两个云函数，替换 `provideRepository`；页面零改动。
2. **接高德**：`AMapLocationProvider`（定位）+ P01 底图换成 MapView（现在是 Canvas 自绘的路网，只为对齐设计稿）。
3. ~~补 wrapper~~ **已完成（11-03）**：wrapper 8.9 + 腾讯镜像，Android Studio 打开即同步。
4. **加一组 Compose Preview**：每个页面加 `@Preview`，改 UI 时不用起模拟器也能看。
5. **debug-only 直达入口**（UI 走查用）：P04 的 pending / confirm / 四种 fail 共 7 个状态正常点不出来，
   可在 `src/debug/` 里读 intent extra 直跳 `Routes.result(lm, key)`，中期演示时一秒切换。
