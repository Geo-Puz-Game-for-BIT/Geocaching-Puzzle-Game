# ADR 0001：Android 单模块分层基座

- 状态：Accepted
- 日期：2026-10-18

## 背景

第一周需要在五人团队中快速获得可构建、可联调的 Android 基线，同时地图、相机、云端和拼图仍会频繁调整。

## 决定

客户端使用 Kotlin、Jetpack Compose、Material 3、Navigation Compose 和 JDK 17。第一周只保留一个 Gradle `app` 模块，在包内按 `core`、`data`、`feature`、`navigation` 和 `app` 分层。依赖由 `AppContainer` 显式组装，待真实 Repository 数量和测试替换需求明确后再评估 Hilt，避免首周引入注解处理和构建复杂度。

地图和定位通过 `MapAdapter`、`LocationProvider` 隔离。Screen 与 ViewModel 不得引用高德 SDK 类型；没有高德 Key 时使用 Fake 数据和演示地图继续开发。

## 影响

- 所有人只需理解一个 Gradle 模块，构建和调试路径较短。
- 平台 SDK 可替换，UI 与业务状态可独立测试。
- 包之间的边界依靠 Review，而不是 Gradle 编译隔离；第二周根据冲突和构建时间决定是否拆模块。
