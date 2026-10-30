# Android 客户端

## 本地启动

1. 安装 JDK 17、Android SDK 36 和 Build Tools 35.0.0；当前 AGP 8.13.2 使用后者，版本口径见 [统一工具链](../docs/toolchain.md)。
2. 将 `local.properties.example` 复制为 `local.properties`，填写本机 `sdk.dir`。
3. 需要真实高德地图时，填写 `AMAP_API_KEY`；没有 Key 时应用自动使用可交互的演示地图。
4. 执行 `gradlew.bat assembleDebug`，APK 位于 `app/build/outputs/apk/debug/`。

当前仓库上级目录包含中文字符。AGP 可以通过 `android.overridePathCheck=true` 构建 APK，但 Windows JUnit classpath 仍可能找不到测试类。完整验收请在仓库根目录执行：

```powershell
.\android-app\scripts\verify-windows.ps1
```

脚本只在需要时创建临时英文盘符，依次运行干净构建、单元测试、APK 打包和 Lint，结束后自动解除映射。长期开发仍建议将仓库克隆到 `D:\Projects\Geocaching-Puzzle-Game` 之类的纯英文路径。

高德 Key 必须创建为 Android 平台 Key，并绑定包名 `com.bitse.geotreasure` 与团队 debug/release 证书 SHA-1。Key 只能放在未跟踪的 `local.properties` 或环境变量中。

## 模块边界

- `core/model`：纯 Kotlin 领域模型，不引用 Android 或高德类型。
- `core/map`、`core/location`：平台能力接口及高德实现。
- `data/repository`：数据来源与领域模型之间的边界。
- `feature/*`：页面、状态、用户动作和单次副作用。
- `navigation`：五个页面之间的唯一导航入口。
